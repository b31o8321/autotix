package dev.autotix.infrastructure.infra.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Date;

/**
 * Aliyun OSS storage provider.
 * Active when autotix.storage.driver=oss.
 */
@Component
@ConditionalOnProperty(name = "autotix.storage.driver", havingValue = "oss")
public class OssStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(OssStorageProvider.class);

    private final OSS ossClient;
    private final String bucket;
    private final String endpoint;

    public OssStorageProvider(StorageConfig config) {
        StorageConfig.Oss cfg = config.getOss();
        this.bucket = cfg.getBucket();
        this.endpoint = cfg.getEndpoint();
        this.ossClient = new OSSClientBuilder()
                .build(endpoint, cfg.getAccessKeyId(), cfg.getAccessKeySecret());
        log.info("OssStorageProvider initialized with bucket={}", bucket);
    }

    @Override
    public String upload(String key, InputStream content, long contentLength, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        if (contentType != null && !contentType.isEmpty()) {
            metadata.setContentType(contentType);
        }
        ossClient.putObject(new PutObjectRequest(bucket, key, content, metadata));
        // Build URL: https://{bucket}.{host}/{key}
        String host = endpoint.replaceFirst("https?://", "");
        return "https://" + bucket + "." + host + "/" + key;
    }

    @Override
    public InputStream download(String key) {
        return ossClient.getObject(bucket, key).getObjectContent();
    }

    @Override
    public boolean delete(String key) {
        ossClient.deleteObject(bucket, key);
        return true;
    }

    @Override
    public URL presignedGet(String key, Duration ttl) {
        Date expiry = Date.from(java.time.Instant.now().plus(ttl));
        return ossClient.generatePresignedUrl(bucket, key, expiry);
    }

    @Override
    public boolean exists(String key) {
        return ossClient.doesObjectExist(bucket, key);
    }
}
