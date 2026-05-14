package dev.autotix.interfaces.files;

import dev.autotix.infrastructure.infra.storage.StorageProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

/**
 * Serve locally stored attachment files.
 * Only used when autotix.storage.driver=local (presignedGet points here).
 * For S3/OSS the presigned URL bypasses this endpoint.
 *
 * Auth: JWT (anyRequest().authenticated() — token param supported by JwtAuthenticationFilter).
 * GET /api/files?key=<storage_key>
 */
@RestController
public class FileServeController {

    private final StorageProvider storageProvider;

    public FileServeController(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    @GetMapping("/api/files")
    public ResponseEntity<StreamingResponseBody> download(@RequestParam String key) {
        try {
            InputStream stream = storageProvider.download(key);
            String contentType = guessContentType(key);
            String fileName = extractFileName(key);

            StreamingResponseBody body = out -> {
                try (InputStream in = stream) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                }
            };

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(body);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String guessContentType(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private String extractFileName(String key) {
        int slash = key.lastIndexOf('/');
        return slash >= 0 ? key.substring(slash + 1) : key;
    }
}
