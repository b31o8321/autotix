package dev.autotix.infrastructure.infra.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

/**
 * Pluggable file storage abstraction.
 * Implementations: LocalStorageProvider (default), S3StorageProvider, OssStorageProvider.
 * Activated via autotix.storage.driver = local | s3 | oss.
 *
 * Key convention (caller's responsibility):
 *   attachments/{yyyy}/{MM}/{ticketId}/{uuid}-{originalFilename}
 */
public interface StorageProvider {

    /**
     * Upload content and return the canonical URL (public, presigned, or internal path).
     */
    String upload(String key, InputStream content, long contentLength, String contentType);

    /** Open an InputStream to download the object. Caller must close. */
    InputStream download(String key);

    /** Delete the object. Returns true if it was present and deleted. */
    boolean delete(String key);

    /**
     * Generate a temporary access URL valid for ttl.
     * For local storage, returns /api/files?key=<key>.
     */
    URL presignedGet(String key, Duration ttl);

    boolean exists(String key);
}
