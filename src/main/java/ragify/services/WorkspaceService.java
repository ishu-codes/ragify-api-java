package ragify.services;

import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ragify.common.ApiException;
import ragify.config.AppProperties;
import ragify.ragify_client.VectorStoreClient;
import ragify.models.Workspace;
import ragify.repository.WorkspaceRepository;
import ragify.schemas.WorkspaceSchemas.IdResponse;
import ragify.schemas.WorkspaceSchemas.WorkspaceInfo;
import ragify.utils.JsonNodes;
import ragify.utils.WorkspaceUtils;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final VectorStoreClient vectorStoreClient;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<WorkspaceInfo> getWorkspaces(Long userId) {
        return workspaceRepository
            .findAllByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(WorkspaceService::toInfo)
            .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceInfo getWorkspace(Long workspaceId, Long userId) {
        return toInfo(ensureWorkspaceAccess(workspaceId, userId));
    }

    @Transactional
    public WorkspaceInfo createWorkspace(Long userId) {
        Workspace workspace = new Workspace();
        workspace.setUserId(userId);
        workspace.setName("Untitled Workspace");
        workspace.setDescription("");
        workspace.setTags(JsonNodes.array());
        workspace.setMaterials(JsonNodes.array());
        workspaceRepository.save(workspace);

        vectorStoreClient.createCollection(workspace.getId().intValue());
        return toInfo(workspace);
    }

    @Transactional
    public WorkspaceInfo updateWorkspaceDetails(
        Long workspaceId,
        Long userId,
        String name,
        String description,
        List<String> tags
    ) {
        Workspace workspace = ensureWorkspaceAccess(workspaceId, userId);
        applyUpdates(workspace, normalizeUpdates(name, description, tags));
        return toInfo(workspace);
    }

    @Transactional
    public IdResponse deleteWorkspace(Long workspaceId, Long userId) {
        ensureWorkspaceAccess(workspaceId, userId);
        workspaceRepository.deleteById(workspaceId);

        WorkspaceUtils.removeDir(uploadDir(workspaceId));
        return new IdResponse(workspaceId);
    }

    public Workspace ensureWorkspaceAccess(Long workspaceId, Long userId) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Workspace could not be found"));

        if (!workspace.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Workspace access denied");
        }
        return workspace;
    }

    public Path uploadDir(Long workspaceId) {
        return Path.of(appProperties.storagePath()).toAbsolutePath().resolve(String.valueOf(workspaceId));
    }

    private static void applyUpdates(Workspace workspace, java.util.Map<String, Object> updates) {
        updates.forEach((key, value) -> {
            switch (key) {
                case "name" -> workspace.setName((String) value);
                case "description" -> workspace.setDescription((String) value);
                case "tags" -> workspace.setTags(JsonNodes.MAPPER.valueToTree(value));
                default -> {
                }
            }
        });
    }

    private static java.util.Map<String, Object> normalizeUpdates(String name, String description, List<String> tags) {
        String normalizedName = name == null ? "" : name.strip();
        if (normalizedName.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Workspace name is required");
        }

        java.util.Map<String, Object> updates = new java.util.LinkedHashMap<>();
        updates.put("name", normalizedName);
        updates.put("description", description == null ? "" : description.strip());
        updates.put(
            "tags",
            tags == null
                ? List.of()
                : tags
                      .stream()
                      .filter(tag -> tag != null && !tag.strip().isEmpty())
                      .map(String::strip)
                      .toList()
        );
        return updates;
    }

    private static WorkspaceInfo toInfo(Workspace workspace) {
        return new WorkspaceInfo(
            workspace.getId(),
            workspace.getUserId(),
            workspace.getName(),
            workspace.getDescription(),
            workspace.getTags(),
            workspace.getMaterials(),
            workspace.getCreatedAt()
        );
    }
}
