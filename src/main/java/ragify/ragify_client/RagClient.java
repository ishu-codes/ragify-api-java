package ragify.ragify_client;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ragify.v1.QueryRequest;
import ragify.v1.QueryResponse;

/**
 * RAG query facade mirroring api-python's ragify_client.query.builder.
 */
@Component
@RequiredArgsConstructor
public class RagClient {

    private static final double QUERY_TIMEOUT = 300;
    private final GrpcClient grpc;

    public String query(int workspaceId, List<ChatMessage> messages) {
        QueryRequest.Builder request = QueryRequest.newBuilder().setWorkspaceId(workspaceId);
        for (ChatMessage message : messages) {
            request.addMessages(
                ragify.v1.ChatMessage.newBuilder().setRole(message.role()).setContent(message.content())
            );
        }
        QueryResponse response = grpc.rag(stub -> stub.query(request.build()), QUERY_TIMEOUT);
        return response.getAnswer();
    }
}
