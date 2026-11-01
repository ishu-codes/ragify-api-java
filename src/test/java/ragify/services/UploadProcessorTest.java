package ragify.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import ragify.models.UploadStatus;
import ragify.ragify_client.IngestionClient;
import ragify.repository.UploadStatusRepository;
import ragify.utils.JsonNodes;
import ragify.utils.WorkspaceUtils;

@ExtendWith(MockitoExtension.class)
class UploadProcessorTest {

    @Mock
    private UploadStatusRepository uploadStatusRepository;

    @Mock
    private IngestionClient ingestionClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    @TempDir
    Path uploadDirectory;

    private UploadProcessor uploadProcessor;

    @BeforeEach
    void setUp() {
        uploadProcessor = new UploadProcessor(uploadStatusRepository, ingestionClient, transactionTemplate);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void ingestsMarkdownAndCompletesTheUpload() throws Exception {
        Path document = uploadDirectory.resolve("notes.md");
        Files.writeString(document, "# Notes\n\nSome facts");
        UploadStatus status = status("notes", "notes.md", "md", document);
        when(uploadStatusRepository.findById(51L)).thenReturn(Optional.of(status));
        when(ingestionClient.processDocument(
            5,
            "notes.md",
            "md",
            Files.readAllBytes(document),
            "markdown"
        )).thenReturn(3);

        uploadProcessor.processUpload(51L, 5L, uploadDirectory.toString());

        assertThat(status.getStatus()).isEqualTo("completed");
        assertThat(status.getCompletedAt()).isNotNull();
        assertThat(status.getFiles().get(0).path("status").asString()).isEqualTo("completed");
        assertThat(status.getLogs().get(status.getLogs().size() - 1).path("message").asString())
            .isEqualTo("Upload completed: 1 file(s) processed successfully, 0 failed");
        verify(uploadStatusRepository).findById(51L);
    }

    @Test
    void marksEmptyFilesAsFailedWithoutCallingIngestion() throws Exception {
        Path document = uploadDirectory.resolve("empty.txt");
        Files.write(document, new byte[0]);
        UploadStatus status = status("empty", "empty.txt", "txt", document);
        when(uploadStatusRepository.findById(52L)).thenReturn(Optional.of(status));

        uploadProcessor.processUpload(52L, 5L, uploadDirectory.toString());

        assertThat(status.getStatus()).isEqualTo("failed");
        assertThat(status.getError()).isEqualTo("All files failed to process. 1 file(s) failed.");
        assertThat(status.getFiles().get(0).path("status").asString()).isEqualTo("failed");
        assertThat(status.getFiles().get(0).path("error").asString()).isEqualTo("File is empty");
        verify(ingestionClient, never()).processDocument(any(Integer.class), any(), any(), any(), any());
    }

    @Test
    void ignoresStatusesThatNoLongerExist() {
        when(uploadStatusRepository.findById(404L)).thenReturn(Optional.empty());

        uploadProcessor.processUpload(404L, 5L, uploadDirectory.toString());

        verify(uploadStatusRepository).findById(404L);
    }

    private static UploadStatus status(String id, String name, String kind, Path document) {
        UploadStatus status = new UploadStatus();
        status.setId(1L);
        status.setWorkspaceId(5L);
        status.setUserId(2L);
        status.setFiles(JsonNodes.MAPPER.valueToTree(java.util.List.of(
            WorkspaceUtils.fileRecord(
                id,
                name,
                kind,
                document.toFile().length(),
                "text/plain",
                document.toString(),
                "2026-01-01T00:00:00Z"
            )
        )));
        status.setLogs(JsonNodes.array());
        return status;
    }
}
