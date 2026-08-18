package ragify.ragify_client;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import ragify.v1.ConvertToMarkdownRequest;
import ragify.v1.ConvertToMarkdownResponse;
import ragify.v1.IngestPdfRequest;
import ragify.v1.IngestPdfResponse;
import ragify.v1.PdfFile;
import ragify.v1.ProcessDocumentRequest;
import ragify.v1.ProcessDocumentResponse;
import ragify.v1.ProcessSectionRequest;
import ragify.v1.ProcessSectionResponse;

/**
 * Ingestion facade mirroring api-python's ragify_client.ingestion.
 */
@Component
public class IngestionClient {

    private static final double PROCESS_DOCUMENT_TIMEOUT = 300;
    private static final double PROCESS_SECTION_TIMEOUT = 60;
    private static final double CONVERT_TIMEOUT = 120;
    private static final double INGEST_PDF_TIMEOUT = 1800;

    private final GrpcClient grpc;

    public IngestionClient(GrpcClient grpc) {
        this.grpc = grpc;
    }

    public int processDocument(int workspaceId, String filename, String kind, byte[] content, String chunkType) {
        ProcessDocumentRequest request = ProcessDocumentRequest.newBuilder()
            .setWorkspaceId(workspaceId)
            .setFilename(filename)
            .setKind(kind == null ? "" : kind)
            .setContent(com.google.protobuf.ByteString.copyFrom(content))
            .setChunkType(chunkType == null ? "" : chunkType)
            .build();
        ProcessDocumentResponse response = grpc.ingestion(
            stub -> stub.processDocument(request),
            PROCESS_DOCUMENT_TIMEOUT
        );
        return response.getChunks();
    }

    public List<String> processSection(List<String> paragraphs) {
        ProcessSectionRequest request = ProcessSectionRequest.newBuilder().addAllParagraphs(paragraphs).build();
        ProcessSectionResponse response = grpc.ingestion(stub -> stub.processSection(request), PROCESS_SECTION_TIMEOUT);
        return new ArrayList<>(response.getChunksList());
    }

    public String convertToMarkdown(String filename, byte[] content) {
        ConvertToMarkdownRequest request = ConvertToMarkdownRequest.newBuilder()
            .setFilename(filename)
            .setContent(com.google.protobuf.ByteString.copyFrom(content))
            .build();
        ConvertToMarkdownResponse response = grpc.ingestion(stub -> stub.convertToMarkdown(request), CONVERT_TIMEOUT);
        return response.getMarkdown();
    }

    public IngestPdfResult ingestPdf(int workspaceId, List<PdfFile> files) {
        IngestPdfRequest.Builder request = IngestPdfRequest.newBuilder().setWorkspaceId(workspaceId);
        for (PdfFile file : files) {
            request.addFiles(ragify.v1.PdfFile.newBuilder()
                .setName(file.name())
                .setContent(com.google.protobuf.ByteString.copyFrom(file.content())));
        }
        IngestPdfResponse response = grpc.ingestion(stub -> stub.ingestPdf(request.build()), INGEST_PDF_TIMEOUT);
        List<PdfResult> results = new ArrayList<>();
        for (ragify.v1.PdfResult result : response.getResultsList()) {
            results.add(new PdfResult(result.getName(), result.getOk(), result.getError()));
        }
        return new IngestPdfResult(response.getProcessed(), response.getFailed(), results);
    }

    public record PdfFile(String name, byte[] content) {}

    public record PdfResult(String name, boolean ok, String error) {}

    public record IngestPdfResult(int processed, int failed, List<PdfResult> results) {}
}
