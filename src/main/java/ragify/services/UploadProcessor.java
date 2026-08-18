package ragify.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ragify.ragify_client.IngestionClient;
import ragify.models.UploadStatus;
import ragify.repository.UploadStatusRepository;
import ragify.utils.JsonNodes;
import tools.jackson.databind.JsonNode;

/**
 * Background processor for uploaded documents (equivalent of
 * _process_uploaded_files in api-python's workspace/service.py), backed by
 * the rag gRPC IngestionService instead of embedded RAG code.
 */
@Component
@RequiredArgsConstructor
public class UploadProcessor {

    private static final Logger log = LoggerFactory.getLogger(UploadProcessor.class);

    private final UploadStatusRepository uploadStatusRepository;
    private final IngestionClient ingestionClient;
    private final TransactionTemplate transactionTemplate;

    @Async("uploadExecutor")
    public void processUpload(Long statusId, Long workspaceId, String uploadDir) {
        try {
            transactionTemplate.executeWithoutResult(status -> process(statusId, workspaceId, uploadDir));
        } catch (Exception ex) {
            log.error("[upload:{}] background processing failed", statusId, ex);
        }
    }

    private void process(Long statusId, Long workspaceId, String uploadDir) {
        UploadStatus status = uploadStatusRepository.findById(statusId).orElse(null);
        if (status == null) {
            return;
        }

        status.setStatus("processing");
        status.setError(null);
        appendLog(status, "Starting background file processing");

        Map<String, List<JsonNode>> filesByType = new LinkedHashMap<>();
        for (JsonNode file : status.getFiles()) {
            filesByType.computeIfAbsent(file.path("kind").asText("file"), key -> new ArrayList<>()).add(file);
        }

        int successfulCount = 0;
        int failedCount = 0;

        for (Map.Entry<String, List<JsonNode>> entry : filesByType.entrySet()) {
            String fileType = entry.getKey();
            List<JsonNode> files = entry.getValue();
            appendLog(status, "Processing " + files.size() + " " + fileType + " file(s)");

            ProcessingResult result = switch (fileType) {
                case "pdf" -> processPdfFiles(status, workspaceId, files);
                case "md" -> processTextFiles(status, workspaceId, files, "markdown", "markdown");
                default -> processTextFiles(status, workspaceId, files, fileType, fileType);
            };

            successfulCount += result.successful();
            failedCount += result.failed();
        }

        finalizeStatus(status, successfulCount, failedCount);
    }

    private ProcessingResult processPdfFiles(UploadStatus status, Long workspaceId, List<JsonNode> files) {
        List<IngestionClient.PdfFile> pdfs = new ArrayList<>();
        for (JsonNode file : files) {
            try {
                String storagePath = file.path("storage_path").asText("");
                pdfs.add(
                    new IngestionClient.PdfFile(file.path("name").asText(), Files.readAllBytes(Path.of(storagePath)))
                );
            } catch (Exception ex) {
                String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                for (JsonNode remaining : files) {
                    setFileStatus(status, remaining.path("id").asText(), "failed", error);
                    appendLog(status, "Failed to process PDF " + remaining.path("name").asText() + ": " + error);
                }
                return new ProcessingResult(0, files.size());
            }
        }

        try {
            IngestionClient.IngestPdfResult result = ingestionClient.ingestPdf(workspaceId.intValue(), pdfs);
            List<String> errors = new ArrayList<>();
            for (IngestionClient.PdfResult pdfResult : result.results()) {
                if (!pdfResult.ok()) {
                    errors.add(pdfResult.name() + ": " + pdfResult.error());
                }
            }
            if (!errors.isEmpty()) {
                throw new IllegalStateException("PDF ingestion failed: " + String.join("; ", errors));
            }

            for (JsonNode file : files) {
                setFileStatus(status, file.path("id").asText(), "completed", null);
                appendLog(status, "Successfully processed PDF: " + file.path("name").asText());
            }
            return new ProcessingResult(files.size(), 0);
        } catch (Exception ex) {
            String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            for (JsonNode file : files) {
                setFileStatus(status, file.path("id").asText(), "failed", error);
                appendLog(status, "Failed to process PDF " + file.path("name").asText() + ": " + error);
            }
            return new ProcessingResult(0, files.size());
        }
    }

    private ProcessingResult processTextFiles(
        UploadStatus status,
        Long workspaceId,
        List<JsonNode> files,
        String chunkType,
        String fileLabel
    ) {
        int successful = 0;
        int failed = 0;

        for (JsonNode file : files) {
            String fileId = file.path("id").asText();
            String fileName = file.path("name").asText();
            String kind = file.path("kind").asText("file");
            String storagePath = file.path("storage_path").asText("");
            try {
                byte[] content = Files.readAllBytes(Path.of(storagePath));
                if (content.length == 0) {
                    throw new IllegalArgumentException("File is empty");
                }

                int chunks = ingestionClient.processDocument(
                    workspaceId.intValue(),
                    fileName,
                    kind,
                    content,
                    chunkType
                );

                setFileStatus(status, fileId, "completed", null);
                appendLog(
                    status,
                    "Successfully processed " + fileLabel + " file: " + fileName + " (" + chunks + " chunks)"
                );
                successful++;
            } catch (Exception ex) {
                String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                setFileStatus(status, fileId, "failed", error);
                appendLog(status, "Failed to process " + fileLabel + " file " + fileName + ": " + error);
                failed++;
            }
        }
        return new ProcessingResult(successful, failed);
    }

    private void finalizeStatus(UploadStatus status, int successfulCount, int failedCount) {
        if (successfulCount > 0) {
            status.setStatus("completed");
            status.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            status.setError(null);
            appendLog(
                status,
                "Upload completed: " + successfulCount + " file(s) processed successfully, " + failedCount + " failed"
            );
        } else if (failedCount > 0) {
            status.setStatus("failed");
            status.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            status.setError("All files failed to process. " + failedCount + " file(s) failed.");
            appendLog(status, "Upload failed: all " + failedCount + " file(s) failed to process");
        } else {
            status.setStatus("failed");
            status.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            status.setError("No files were processed");
            appendLog(status, "Upload failed: no files were processed");
        }
    }

    private void setFileStatus(UploadStatus status, String fileId, String nextStatus, String error) {
        var files = (tools.jackson.databind.node.ArrayNode) status.getFiles().deepCopy();
        for (int i = 0; i < files.size(); i++) {
            JsonNode file = files.get(i);
            if (fileId.equals(file.path("id").asText())) {
                var updated = ((tools.jackson.databind.node.ObjectNode) file).deepCopy();
                updated.put("status", nextStatus);
                if (error == null) {
                    updated.putNull("error");
                } else {
                    updated.put("error", error);
                }
                files.set(i, updated);
            }
        }
        status.setFiles(files);
    }

    private void appendLog(UploadStatus status, String message) {
        var logs = (tools.jackson.databind.node.ArrayNode) status.getLogs().deepCopy();
        var entry = JsonNodes.object();
        entry.put("message", message);
        entry.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        logs.add(entry);
        status.setLogs(logs);
        log.info("[upload:{}] {}", status.getId(), message);
    }

    private record ProcessingResult(int successful, int failed) {}
}
