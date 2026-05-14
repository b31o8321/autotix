package dev.autotix.infrastructure.infra.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageProviderTest {

    @TempDir
    Path tempDir;

    private LocalStorageProvider provider;

    @BeforeEach
    void setUp() {
        StorageConfig config = new StorageConfig();
        config.getLocal().setRoot(tempDir.toString());
        provider = new LocalStorageProvider(config);
    }

    @Test
    void upload_download_roundTrip_preservesBytes() throws Exception {
        byte[] content = "Hello, attachment!".getBytes(StandardCharsets.UTF_8);
        String key = "attachments/2025/05/42/test-file.txt";

        provider.upload(key, new ByteArrayInputStream(content), content.length, "text/plain");

        InputStream downloaded = provider.download(key);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = downloaded.read(buf)) != -1) baos.write(buf, 0, n);
        downloaded.close();
        byte[] result = baos.toByteArray();

        assertArrayEquals(content, result);
    }

    @Test
    void exists_returnsFalse_forUnknownKey() {
        assertFalse(provider.exists("unknown/key/file.txt"));
    }

    @Test
    void exists_returnsTrue_afterUpload() {
        byte[] content = "data".getBytes();
        String key = "attachments/2025/05/1/exists-test.bin";
        provider.upload(key, new ByteArrayInputStream(content), content.length, "application/octet-stream");
        assertTrue(provider.exists(key));
    }

    @Test
    void delete_removesFile() {
        byte[] content = "to delete".getBytes();
        String key = "attachments/2025/05/1/delete-me.txt";
        provider.upload(key, new ByteArrayInputStream(content), content.length, "text/plain");

        assertTrue(provider.exists(key));
        boolean deleted = provider.delete(key);
        assertTrue(deleted);
        assertFalse(provider.exists(key));
    }

    @Test
    void delete_returnsFalse_forNonExistentKey() {
        assertFalse(provider.delete("no/such/file.txt"));
    }

    @Test
    void presignedGet_returnsLocalApiUrl() throws Exception {
        String key = "attachments/2025/05/1/my file.txt";
        URL url = provider.presignedGet(key, java.time.Duration.ofMinutes(60));
        assertNotNull(url);
        String urlStr = url.toString();
        assertTrue(urlStr.contains("/api/files"), "Expected /api/files in URL: " + urlStr);
        assertTrue(urlStr.contains("key="), "Expected key= parameter in URL: " + urlStr);
    }
}
