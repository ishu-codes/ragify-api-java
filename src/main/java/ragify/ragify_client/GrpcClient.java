package ragify.ragify_client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import ragify.config.AppProperties;
import ragify.v1.IngestionServiceGrpc;
import ragify.v1.RagServiceGrpc;
import ragify.v1.VectorStoreServiceGrpc;

/**
 * Lazy, reconnecting gRPC transport for the ragify-rag server, mirroring
 * api-python's ragify_client.client.
 */
@Component
public class GrpcClient {

    private static final int MAX_MESSAGE_SIZE = 500 * 1024 * 1024;

    private final String endpoint;
    private ManagedChannel channel;
    private VectorStoreServiceGrpc.VectorStoreServiceBlockingStub vectorStore;
    private IngestionServiceGrpc.IngestionServiceBlockingStub ingestion;
    private RagServiceGrpc.RagServiceBlockingStub rag;

    public GrpcClient(AppProperties properties) {
        String configured = properties.grpcEndpoint();
        this.endpoint = configured == null || configured.isBlank() ? "localhost:50051" : configured;
    }

    public <T> T vectorStore(
        Function<VectorStoreServiceGrpc.VectorStoreServiceBlockingStub, T> operation,
        double timeoutSeconds
    ) {
        return withRetry(() ->
            operation.apply(vectorStoreStub().withDeadlineAfter(seconds(timeoutSeconds), TimeUnit.SECONDS))
        );
    }

    public <T> T ingestion(
        Function<IngestionServiceGrpc.IngestionServiceBlockingStub, T> operation,
        double timeoutSeconds
    ) {
        return withRetry(() ->
            operation.apply(ingestionStub().withDeadlineAfter(seconds(timeoutSeconds), TimeUnit.SECONDS))
        );
    }

    public <T> T rag(Function<RagServiceGrpc.RagServiceBlockingStub, T> operation, double timeoutSeconds) {
        return withRetry(() -> operation.apply(ragStub().withDeadlineAfter(seconds(timeoutSeconds), TimeUnit.SECONDS)));
    }

    private VectorStoreServiceGrpc.VectorStoreServiceBlockingStub vectorStoreStub() {
        ensureConnected();
        return vectorStore;
    }

    private IngestionServiceGrpc.IngestionServiceBlockingStub ingestionStub() {
        ensureConnected();
        return ingestion;
    }

    private RagServiceGrpc.RagServiceBlockingStub ragStub() {
        ensureConnected();
        return rag;
    }

    private synchronized void ensureConnected() {
        if (channel == null) {
            ManagedChannel newChannel = ManagedChannelBuilder.forTarget(endpoint)
                .usePlaintext()
                .maxInboundMessageSize(MAX_MESSAGE_SIZE)
                .build();
            channel = newChannel;
            vectorStore = VectorStoreServiceGrpc.newBlockingStub(newChannel);
            ingestion = IngestionServiceGrpc.newBlockingStub(newChannel);
            rag = RagServiceGrpc.newBlockingStub(newChannel);
        }
    }

    private <T> T withRetry(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (StatusRuntimeException exc) {
            if (exc.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                reset();
                return operation.get();
            }
            throw exc;
        }
    }

    private static long seconds(double timeoutSeconds) {
        return (long) Math.ceil(timeoutSeconds);
    }

    public synchronized void reset() {
        if (channel != null) {
            channel.shutdownNow();
            channel = null;
            vectorStore = null;
            ingestion = null;
            rag = null;
        }
    }

    @PreDestroy
    public void close() {
        reset();
    }
}
