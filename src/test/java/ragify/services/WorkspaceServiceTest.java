package ragify.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ragify.common.ApiException;
import ragify.config.AppProperties;
import ragify.models.Workspace;
import ragify.ragify_client.VectorStoreClient;
import ragify.repository.WorkspaceRepository;
import ragify.schemas.WorkspaceSchemas.WorkspaceInfo;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private VectorStoreClient vectorStoreClient;

    @TempDir
    Path storagePath;

    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
            "super-secret-code",
            60,
            "http://localhost:3000",
            storagePath.toString(),
            "localhost:50051"
        );
        workspaceService = new WorkspaceService(workspaceRepository, vectorStoreClient, properties);
    }

    @Test
    void createsAnEmptyWorkspaceAndItsVectorCollection() {
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace workspace = invocation.getArgument(0);
            workspace.setId(17L);
            workspace.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return workspace;
        });

        WorkspaceInfo result = workspaceService.createWorkspace(9L);

        assertThat(result.id()).isEqualTo(17L);
        assertThat(result.userId()).isEqualTo(9L);
        assertThat(result.name()).isEqualTo("Untitled Workspace");
        assertThat(result.description()).isEmpty();
        assertThat(result.tags().isArray()).isTrue();
        assertThat(result.tags()).isEmpty();
        assertThat(result.materials()).isEmpty();
        verify(vectorStoreClient).createCollection(17);
    }

    @Test
    void listsWorkspacesInRepositoryOrder() {
        Workspace newer = workspace(2L, 4L, "Newer");
        Workspace older = workspace(1L, 4L, "Older");
        when(workspaceRepository.findAllByUserIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(newer, older));

        List<WorkspaceInfo> result = workspaceService.getWorkspaces(4L);

        assertThat(result).extracting(WorkspaceInfo::name).containsExactly("Newer", "Older");
    }

    @Test
    void rejectsAWorkspaceOwnedByAnotherUser() {
        when(workspaceRepository.findById(3L)).thenReturn(Optional.of(workspace(3L, 8L, "Private")));

        assertThatThrownBy(() -> workspaceService.getWorkspace(3L, 9L))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(exception).hasMessage("Workspace access denied");
            });
    }

    @Test
    void reportsMissingWorkspacesAsNotFound() {
        when(workspaceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.ensureWorkspaceAccess(404L, 1L))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception).hasMessage("Workspace could not be found");
            });
    }

    @Test
    void normalizesWorkspaceUpdates() {
        Workspace workspace = workspace(5L, 1L, "Old");
        when(workspaceRepository.findById(5L)).thenReturn(Optional.of(workspace));

        WorkspaceInfo result = workspaceService.updateWorkspaceDetails(
            5L,
            1L,
            "  Research notes  ",
            "  Useful sources  ",
            List.of(" java ", "", "testing")
        );

        assertThat(result.name()).isEqualTo("Research notes");
        assertThat(result.description()).isEqualTo("Useful sources");
        assertThat(result.tags()).extracting(node -> node.asString()).containsExactly("java", "testing");
        assertThat(workspace.getTags()).hasSize(2);
    }

    @Test
    void rejectsAnEmptyWorkspaceName() {
        when(workspaceRepository.findById(5L)).thenReturn(Optional.of(workspace(5L, 1L, "Old")));

        assertThatThrownBy(() -> workspaceService.updateWorkspaceDetails(5L, 1L, "   ", null, null))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception).hasMessage("Workspace name is required");
            });
    }

    @Test
    void deletesTheDatabaseRowAndWorkspaceFiles() throws Exception {
        Workspace workspace = workspace(12L, 1L, "Temporary");
        when(workspaceRepository.findById(12L)).thenReturn(Optional.of(workspace));
        Path workspaceDirectory = storagePath.resolve("12");
        Files.createDirectories(workspaceDirectory);
        Files.writeString(workspaceDirectory.resolve("notes.txt"), "temporary");

        assertThat(workspaceService.deleteWorkspace(12L, 1L).id()).isEqualTo(12L);

        verify(workspaceRepository).deleteById(12L);
        verify(vectorStoreClient, never()).deleteCollection(12);
        assertThat(workspaceDirectory).doesNotExist();
    }

    private static Workspace workspace(Long id, Long userId, String name) {
        Workspace workspace = new Workspace();
        workspace.setId(id);
        workspace.setUserId(userId);
        workspace.setName(name);
        workspace.setDescription("");
        workspace.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return workspace;
    }
}
