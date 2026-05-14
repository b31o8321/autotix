package dev.autotix.infrastructure.infra.storage;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Date;

/**
 * AWS S3 (and MinIO / any S3-compatible) storage provider.
 * Active when autotix.storage.driver=s3.
 * MinIO: set endpoint and path-style=true.
 */
@Component
@ConditionalOnProperty(name = "autotix.storage.driver", havingValue = "s3")
public class S3StorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(S3StorageProvider.class);

    private final AmazonS3 s3;
    private final String bucket;

    public S3StorageProvider(StorageConfig config) {
        StorageConfig.S3 cfg = config.getS3();
        this.bucket = cfg.getBucket();

        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(cfg.getAccessKey(), cfg.getSecretKey())));

        if (cfg.getEndpoint() != null && !cfg.getEndpoint().isEmpty()) {
            builder.withEndpointConfiguration(
                    new EndpointConfiguration(cfg.getEndpoint(), cfg.getRegion()));
        } else {
            builder.withRegion(cfg.getRegion());
        }

        if (cfg.isPathStyle()) {
            builder.withPathStyleAccessEnabled(true);
        }

        this.s3 = builder.build();
        log.info("S3StorageProvider initialized with bucket={}", bucket);
    }

    @Override
    public String upload(String key, InputStream content, long contentLength, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        if (contentType != null && !contentType.isEmpty()) {
            metadata.setContentType(contentType);
        }
        s3.putObject(new PutObjectRequest(bucket, key, content, metadata));
        return s3.getUrl(bucket, key).toString();
    }

    @Override
    public InputStream download(String key) {
        return s3.getObject(bucket, key).getObjectContent();
    }

    @Override
    public boolean delete(String key) {
        s3.deleteObject(bucket, key);
        return true;
    }

    @Override
    public URL presignedGet(String key, Duration ttl) {
        Date expiry = Date.from(java.time.Instant.now().plus(ttl));
        return s3.generatePresignedUrl(bucket, key, expiry);
    }

    @Override
    public boolean exists(String key) {
        return s3.doesObjectExist(bucket, key);
    }
}
