package dev.autotix.infrastructure.infra.storage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies OssStorageProvider can be constructed without throwing.
 * No live OSS calls.
 */
class OssStorageProviderConstructorTest {

    @Test
    void constructor_withValidConfig_doesNotThrow() throws Exception {
        StorageConfig config = new StorageConfig();
        StorageConfig.Oss oss = new StorageConfig.Oss();
        oss.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        oss.setBucket("test-bucket");
        oss.setAccessKeyId("test-id");
        oss.setAccessKeySecret("test-secret");
        config.setOss(oss);

        OssStorageProvider provider = new OssStorageProvider(config);
        assertNotNull(provider);

        // Verify ossClient field is non-null
        Field clientField = OssStorageProvider.class.getDeclaredField("ossClient");
        clientField.setAccessible(true);
        assertNotNull(clientField.get(provider));
    }
}
