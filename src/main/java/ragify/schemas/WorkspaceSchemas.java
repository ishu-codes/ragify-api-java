package ragify.schemas;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class WorkspaceSchemas {

    private WorkspaceSchemas() {}

    public record WorkspaceInfo(
        Long id,
        Long userId,
        String name,
        String description,
        JsonNode tags,
        JsonNode materials,
        OffsetDateTime createdAt
    ) {}

    public record QueryBody(Long sessionId, @NotBlank String query) {}

    public record QueryResponse(Long sessionId, String sessionName, String createdAt, String answer) {}

    public record IdResponse(Long id) {}

    public record WorkspaceIdResponse(Long workspaceId) {}

    public record UploadResponse(Long statusId, String message) {}

    public record UploadStatusResponse(
        Long id,
        Long workspaceId,
        Long userId,
        String status,
        JsonNode files,
        JsonNode logs,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt,
        String error
    ) {}

    public record UpdateWorkspaceBody(String name, String description, List<String> tags) {}

    public record UpdateSessionBody(@NotBlank String name) {}

    public record SessionSummary(
        Long id,
        Long workspaceId,
        String name,
        Integer messageCount,
        OffsetDateTime createdAt
    ) {}

    public record SessionMessagesResponse(
        Long id,
        Long workspaceId,
        String name,
        OffsetDateTime createdAt,
        List<Map<String, String>> messages
    ) {}
}
