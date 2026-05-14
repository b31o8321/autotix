package dev.autotix.infrastructure.infra.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binding for autotix.storage.* properties.
 */
@Configuration
@ConfigurationProperties(prefix = "autotix.storage")
public class StorageConfig {

    private String driver = "local";
    private Local local = new Local();
    private S3 s3 = new S3();
    private Oss oss = new Oss();

    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }

    public Local getLocal() { return local; }
    public void setLocal(Local local) { this.local = local; }

    public S3 getS3() { return s3; }
    public void setS3(S3 s3) { this.s3 = s3; }

    public Oss getOss() { return oss; }
    public void setOss(Oss oss) { this.oss = oss; }

    // -----------------------------------------------------------------------

    public static class Local {
        private String root = "/data/attachments";

        public String getRoot() { return root; }
        public void setRoot(String root) { this.root = root; }
    }

    public static class S3 {
        private String endpoint = "";
        private String region = "us-east-1";
        private String bucket = "autotix";
        private String accessKey = "";
        private String secretKey = "";
        private boolean pathStyle = false;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }

        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

        public boolean isPathStyle() { return pathStyle; }
        public void setPathStyle(boolean pathStyle) { this.pathStyle = pathStyle; }
    }

    public static class Oss {
        private String endpoint = "";
        private String bucket = "autotix";
        private String accessKeyId = "";
        private String accessKeySecret = "";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }

        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

        public String getAccessKeySecret() { return accessKeySecret; }
        public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    }
}
