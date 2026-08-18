package ragify.utils;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import ragify.common.ApiException;

public final class WorkspaceUtils {

    private static final Map<String, String> EXTENSION_BY_MIME = Map.ofEntries(
        Map.entry("image/jpeg", ".jpg"),
        Map.entry("image/png", ".png"),
        Map.entry("image/webp", ".webp"),
        Map.entry("application/pdf", ".pdf"),
        Map.entry("application/json", ".json"),
        Map.entry("text/plain", ".txt"),
        Map.entry("application/zip", ".zip"),
        Map.entry("application/x-tar", ".tar"),
        Map.entry("application/gzip", ".gz"),
        Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
        Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
        Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx")
    );

    private WorkspaceUtils() {}

    public static String detectMimeType(String fileName) {
        String mimeType = URLConnection.guessContentTypeFromName(fileName == null ? "" : fileName);
        return mimeType == null ? "application/octet-stream" : mimeType;
    }

    public static String replaceExtension(String filename, String mimeType) {
        String extension = EXTENSION_BY_MIME.get(mimeType);
        if (extension == null) {
            return filename;
        }
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            return filename.substring(0, dot) + extension;
        }
        return filename + extension;
    }

    public static String kindOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            return filename.substring(dot + 1).toLowerCase();
        }
        return "file";
    }

    public static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not create storage directory: " + ex.getMessage()
            );
        }
    }

    public static void removeDir(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort, matches Python remove_dir
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    public static Map<String, Object> fileRecord(
        String id,
        String name,
        String kind,
        long size,
        String mimeType,
        String storagePath,
        String createdAt
    ) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("name", name);
        record.put("kind", kind);
        record.put("size", size);
        record.put("mime_type", mimeType);
        record.put("storage_path", storagePath);
        record.put("status", "uploaded");
        record.put("error", null);
        record.put("created_at", createdAt);
        return record;
    }
}
