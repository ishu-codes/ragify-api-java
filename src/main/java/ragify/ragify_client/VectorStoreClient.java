package ragify.ragify_client;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import ragify.v1.Document;
import ragify.v1.Empty;
import ragify.v1.InsertDocumentsRequest;
import ragify.v1.WorkspaceRequest;

/**
 * Vector store facade mirroring api-python's ragify_client.vector_store.
 */
@Component
public class VectorStoreClient {

    private static final double DEFAULT_TIMEOUT = 60;
    private static final double INSERT_TIMEOUT = 120;

    private final GrpcClient grpc;

    public VectorStoreClient(GrpcClient grpc) {
        this.grpc = grpc;
    }

    public void createCollection(int workspaceId) {
        grpc.vectorStore(
            stub -> stub.createCollection(WorkspaceRequest.newBuilder().setWorkspaceId(workspaceId).build()),
            DEFAULT_TIMEOUT
        );
    }

    public void deleteCollection(int workspaceId) {
        grpc.vectorStore(
            stub -> stub.deleteCollection(WorkspaceRequest.newBuilder().setWorkspaceId(workspaceId).build()),
            DEFAULT_TIMEOUT
        );
    }

    public int insertDocuments(int workspaceId, List<DocumentRecord> documents) {
        InsertDocumentsRequest.Builder request = InsertDocumentsRequest.newBuilder().setWorkspaceId(workspaceId);
        for (DocumentRecord document : documents) {
            Document.Builder wire = Document.newBuilder().setText(document.text());
            document.metadata().forEach(wire::putMetadata);
            request.addDocuments(wire);
        }
        return grpc.vectorStore(stub -> stub.insertDocuments(request.build()).getInserted(), INSERT_TIMEOUT);
    }

    public record DocumentRecord(String text, Map<String, String> metadata) {}
}
