package dev.autotix.infrastructure.infra.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Default local-filesystem storage.
 * Active when autotix.storage.driver=local (or missing).
 * Stores files under autotix.storage.local.root.
 *
 * presignedGet returns /api/files?key=... — the backend serves this via FileServeController.
 */
@Component
@ConditionalOnProperty(name = "autotix.storage.driver", havingValue = "local", matchIfMissing = true)
public class LocalStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageProvider.class);

    private final String root;

    public LocalStorageProvider(StorageConfig config) {
        this.root = config.getLocal().getRoot();
        log.info("LocalStorageProvider initialized with root={}", root);
    }

    @Override
    public String upload(String key, InputStream content, long contentLength, String contentType) {
        File target = resolve(key);
        File parentDir = target.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new RuntimeException("Failed to create directory: " + parentDir.getAbsolutePath());
        }
        try (FileOutputStream fos = new FileOutputStream(target)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = content.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
        } catch (IOException e) {
            throw new RuntimeException("Upload failed for key=" + key, e);
        }
        return "/api/files?key=" + encodeKey(key);
    }

    @Override
    public InputStream download(String key) {
        File file = resolve(key);
        if (!file.exists()) {
            throw new RuntimeException("File not found for key=" + key);
        }
        try {
            return new FileInputStream(file);
        } catch (IOException e) {
            throw new RuntimeException("Download failed for key=" + key, e);
        }
    }

    @Override
    public boolean delete(String key) {
        Path path = Paths.get(root, key.replace("/", File.separator));
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Delete failed for key={}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public URL presignedGet(String key, Duration ttl) {
        // TTL is ignored for local storage — frontend uses the same auth-protected endpoint.
        // We use a synthetic http: URL so URL object is valid; the real serving path is /api/files?key=...
        // The frontend receives the string form which is what matters.
        try {
            return new URL("http://localhost/api/files?key=" + encodeKey(key));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to build presigned URL for key=" + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return resolve(key).exists();
    }

    // -----------------------------------------------------------------------

    private File resolve(String key) {
        // Normalise: key may use forward slashes regardless of OS
        return new File(root + File.separator + key.replace("/", File.separator));
    }

    private String encodeKey(String key) {
        try {
            return URLEncoder.encode(key, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return key;
        }
    }
}
