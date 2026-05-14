package dev.autotix.infrastructure.infra.storage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies S3StorageProvider can be constructed without throwing.
 * No live S3 calls.
 */
class S3StorageProviderConstructorTest {

    @Test
    void constructor_withValidConfig_doesNotThrow() throws Exception {
        StorageConfig config = new StorageConfig();
        StorageConfig.S3 s3 = new StorageConfig.S3();
        s3.setRegion("us-east-1");
        s3.setBucket("test-bucket");
        s3.setAccessKey("test-access");
        s3.setSecretKey("test-secret");
        s3.setPathStyle(false);
        config.setS3(s3);

        S3StorageProvider provider = new S3StorageProvider(config);
        assertNotNull(provider);
    }

    @Test
    void constructor_withMinioConfig_doesNotThrow() throws Exception {
        StorageConfig config = new StorageConfig();
        StorageConfig.S3 s3 = new StorageConfig.S3();
        s3.setEndpoint("http://localhost:9000");
        s3.setRegion("us-east-1");
        s3.setBucket("test-bucket");
        s3.setAccessKey("minio");
        s3.setSecretKey("minio123");
        s3.setPathStyle(true);
        config.setS3(s3);

        S3StorageProvider provider = new S3StorageProvider(config);

        // Verify the s3 client field is non-null via reflection
        Field s3Field = S3StorageProvider.class.getDeclaredField("s3");
        s3Field.setAccessible(true);
        assertNotNull(s3Field.get(provider));
    }
}
