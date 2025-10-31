package org.coal;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@Builder
@Getter
@Setter
public class Configuration {
    private final String ftpHost;
    private final int ftpPort;
    private final String ftpUsername;
    private final String ftpPassword;
    private final String ftpRemoteDir;

    private final String s3Endpoint;
    private final String s3KeyId;
    private final String s3SecretKey;
    private final String s3Bucket;
    private final String s3Region;

    private final String elasticConnectionString;

    private final String localLogDir;

    private static Configuration instance;

    public static Configuration fromEnvironment() {
        return Configuration.builder()
                .ftpHost(getEnv("FTP_HOST", "localhost"))
                .ftpPort(Integer.parseInt(getEnv("FTP_PORT", "21")))
                .ftpUsername(getEnv("FTP_USERNAME", "anonymous"))
                .ftpPassword(getEnv("FTP_PASSWORD", ""))
                .ftpRemoteDir(getEnv("FTP_REMOTE_DIR", "/logs"))
                .s3KeyId(getEnv("S3_ACCESS_KEY_ID", ""))
                .s3SecretKey(getEnv("S3_SECRET_ACCESS_KEY", ""))
                .s3Bucket(getEnv("S3_BUCKET", "logs-bucket"))
                .localLogDir(getEnv("LOCAL_LOG_DIR", "./logs"))
                .elasticConnectionString(getEnv("ELASTIC_CONNECTION_STRING", "http://elasticsearch:9200"))
                .s3Region(getEnv("S3_REGION", "auto"))
                .build();
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    public void validate() {
        if (s3KeyId.isEmpty() || s3SecretKey.isEmpty()) {
            throw new IllegalStateException("S3 credentials are required: S3_ACCOUNT_ID, S3_ACCESS_KEY_ID, S3_SECRET_ACCESS_KEY");
        }
        if (ftpHost.isEmpty()) {
            throw new IllegalStateException("FTP_HOST is required");
        }
    }

    public static Configuration getInstance() {
        if (instance == null) {
            instance = Configuration.fromEnvironment();
        }
        return instance;
    }


}
