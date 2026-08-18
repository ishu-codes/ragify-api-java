package ragify.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import ragify.common.ApiException;
import ragify.models.UploadStatus;
import ragify.repository.UploadStatusRepository;
import ragify.schemas.WorkspaceSchemas.UploadResponse;
import ragify.schemas.WorkspaceSchemas.UploadStatusResponse;
import ragify.utils.JsonNodes;
import ragify.utils.WorkspaceUtils;

@Service
@RequiredArgsConstructor
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final WorkspaceService workspaceService;
    private final UploadStatusRepository uploadStatusRepository;
    private final UploadProcessor uploadProcessor;

    @Transactional
    public UploadResponse uploadDocs(Long workspaceId, Long userId, MultipartFile[] files) {
        workspaceService.ensureWorkspaceAccess(workspaceId, userId);

        if (files == null || files.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No documents provided");
        }

        Path uploadDir = workspaceService.uploadDir(workspaceId);
        WorkspaceUtils.ensureDir(uploadDir);

        List<Map<String, Object>> materialRecords = new ArrayList<>();
        for (MultipartFile file : files) {
            Map<String, Object> record = determineTypeAndSaveDoc(file, uploadDir);
            if (record != null) {
                materialRecords.add(record);
            }
        }

        if (materialRecords.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No files were uploaded successfully");
        }

        UploadStatus status = new UploadStatus();
        status.setWorkspaceId(workspaceId);
        status.setUserId(userId);
        status.setStatus("uploaded");
        status.setFiles(JsonNodes.MAPPER.valueToTree(materialRecords));
        status.setLogs(JsonNodes.array());
        uploadStatusRepository.save(status);
        appendLog(status, "Received " + materialRecords.size() + " files from client");

        Long statusId = status.getId();
        String dir = uploadDir.toString();
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    uploadProcessor.processUpload(statusId, workspaceId, dir);
                }
            }
        );

        return new UploadResponse(statusId, "Uploaded " + materialRecords.size() + " files. Processing started.");
    }

    @Transactional(readOnly = true)
    public UploadStatusResponse getUploadStatus(Long workspaceId, Long statusId, Long userId) {
        workspaceService.ensureWorkspaceAccess(workspaceId, userId);
        UploadStatus status = uploadStatusRepository
            .findById(statusId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Upload status could not be found"));

        if (!status.getWorkspaceId().equals(workspaceId) || !status.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Upload status could not be found");
        }

        return new UploadStatusResponse(
            status.getId(),
            status.getWorkspaceId(),
            status.getUserId(),
            status.getStatus(),
            status.getFiles(),
            status.getLogs(),
            status.getCreatedAt(),
            status.getUpdatedAt(),
            status.getCompletedAt(),
            status.getError()
        );
    }

    private Map<String, Object> determineTypeAndSaveDoc(MultipartFile file, Path uploadDir) {
        try {
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) {
                return null;
            }

            String fileId = UUID.randomUUID().toString();
            byte[] bytes = file.getBytes();
            String mimeType = WorkspaceUtils.detectMimeType(originalName);
            String filename = WorkspaceUtils.replaceExtension(originalName, mimeType);
            String kind = WorkspaceUtils.kindOf(filename);

            Path target = uploadDir.resolve(fileId + "-" + filename);
            Files.write(target, bytes);

            return WorkspaceUtils.fileRecord(
                fileId,
                filename,
                kind,
                bytes.length,
                mimeType,
                target.toAbsolutePath().toString(),
                OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            );
        } catch (Exception ex) {
            log.warn("[upload] failed receiving {}: {}", file.getOriginalFilename(), ex.getMessage());
            return null;
        }
    }

    private void appendLog(UploadStatus status, String message) {
        var logs = (tools.jackson.databind.node.ArrayNode) status.getLogs().deepCopy();
        var entry = JsonNodes.object();
        entry.put("message", message);
        entry.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        logs.add(entry);
        status.setLogs(logs);
    }
}
