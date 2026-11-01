package ragify.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ragify.common.ApiException;
import ragify.models.UploadStatus;
import ragify.repository.UploadStatusRepository;
import ragify.schemas.WorkspaceSchemas.UploadResponse;
import ragify.schemas.WorkspaceSchemas.UploadStatusResponse;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private UploadStatusRepository uploadStatusRepository;

    @Mock
    private UploadProcessor uploadProcessor;

    @TempDir
    Path uploadDirectory;

    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new UploadService(workspaceService, uploadStatusRepository, uploadProcessor);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void storesFilesAndStartsProcessingAfterTheTransactionCommits() throws Exception {
        when(workspaceService.uploadDir(5L)).thenReturn(uploadDirectory);
        when(uploadStatusRepository.save(any(UploadStatus.class))).thenAnswer(invocation -> {
            UploadStatus status = invocation.getArgument(0);
            status.setId(88L);
            return status;
        });
        MockMultipartFile document = new MockMultipartFile(
            "files",
            "Research Notes.txt",
            "text/plain",
            "useful facts".getBytes()
        );

        UploadResponse response = uploadService.uploadDocs(5L, 2L, new MockMultipartFile[] { document });

        assertThat(response.statusId()).isEqualTo(88L);
        assertThat(response.message()).isEqualTo("Uploaded 1 files. Processing started.");

        Path storedFile;
        try (var files = Files.list(uploadDirectory)) {
            storedFile = files.findFirst().orElseThrow();
        }
        assertThat(storedFile.getFileName().toString()).endsWith("-Research Notes.txt");
        assertThat(Files.readString(storedFile)).isEqualTo("useful facts");
        verify(uploadProcessor, never()).processUpload(any(), any(), any());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(uploadProcessor).processUpload(88L, 5L, uploadDirectory.toString());
    }

    @Test
    void rejectsAnEmptyUploadRequest() {
        assertThatThrownBy(() -> uploadService.uploadDocs(5L, 2L, new MockMultipartFile[0]))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception).hasMessage("No documents provided");
            });
    }

    @Test
    void ignoresFilesWithoutNames() {
        when(workspaceService.uploadDir(5L)).thenReturn(uploadDirectory);
        MockMultipartFile unnamedFile = new MockMultipartFile("files", "", "text/plain", "content".getBytes());

        assertThatThrownBy(() -> uploadService.uploadDocs(5L, 2L, new MockMultipartFile[] { unnamedFile }))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception).hasMessage("No files were uploaded successfully");
            });
    }

    @Test
    void returnsOnlyTheOwnersUploadStatus() {
        UploadStatus status = new UploadStatus();
        status.setId(91L);
        status.setWorkspaceId(5L);
        status.setUserId(2L);
        when(uploadStatusRepository.findById(91L)).thenReturn(Optional.of(status));

        UploadStatusResponse response = uploadService.getUploadStatus(5L, 91L, 2L);

        assertThat(response.id()).isEqualTo(91L);
        assertThat(response.workspaceId()).isEqualTo(5L);
        assertThat(response.userId()).isEqualTo(2L);
    }

    @Test
    void hidesUploadStatusesOwnedByAnotherUser() {
        UploadStatus status = new UploadStatus();
        status.setId(92L);
        status.setWorkspaceId(5L);
        status.setUserId(99L);
        when(uploadStatusRepository.findById(92L)).thenReturn(Optional.of(status));

        assertThatThrownBy(() -> uploadService.getUploadStatus(5L, 92L, 2L))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception).hasMessage("Upload status could not be found");
            });
    }
}
