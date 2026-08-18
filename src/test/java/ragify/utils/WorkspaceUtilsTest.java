package ragify.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkspaceUtilsTest {

    @Test
    void replaceExtensionMapsMimeToExtension() {
        assertThat(WorkspaceUtils.replaceExtension("report", "application/pdf")).isEqualTo("report.pdf");
        assertThat(WorkspaceUtils.replaceExtension("notes.txt", "text/plain")).isEqualTo("notes.txt");
        assertThat(
            WorkspaceUtils.replaceExtension(
                "paper",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        ).isEqualTo("paper.docx");
    }

    @Test
    void unknownMimeKeepsFilename() {
        assertThat(WorkspaceUtils.replaceExtension("weird.bin", "application/x-weird")).isEqualTo("weird.bin");
        assertThat(WorkspaceUtils.replaceExtension("notes.md", "text/markdown")).isEqualTo("notes.md");
    }

    @Test
    void kindComesFromReplacedExtension() {
        assertThat(WorkspaceUtils.kindOf("upload.pdf")).isEqualTo("pdf");
        assertThat(WorkspaceUtils.kindOf("notes.md")).isEqualTo("md");
        assertThat(WorkspaceUtils.kindOf("README")).isEqualTo("file");
    }

    @Test
    void detectsMimeFromFileName() {
        assertThat(WorkspaceUtils.detectMimeType("doc.pdf")).isEqualTo("application/pdf");
        assertThat(WorkspaceUtils.detectMimeType("notes.md")).isEqualTo("text/markdown");
        assertThat(WorkspaceUtils.detectMimeType("unknown.xyz")).isEqualTo("application/octet-stream");
    }
}
