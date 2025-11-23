package org.coal;

import io.github.cdimascio.dotenv.Dotenv;
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
    private static Dotenv dotenv;

    public static Configuration fromEnvironment() {
        return Configuration.builder()
                .ftpHost(getEnv("FTP_HOST", "localhost"))
                .ftpPort(Integer.parseInt(getEnv("FTP_PORT", "21")))
                .ftpUsername(getEnv("FTP_USERNAME", "anonymous"))
                .ftpPassword(getEnv("FTP_PASSWORD", ""))
                .ftpRemoteDir(getEnv("FTP_REMOTE_DIR", "/logs"))
                .s3KeyId(getEnv("S3_ACCESS_KEY", ""))
                .s3SecretKey(getEnv("S3_SECRET_KEY", ""))
                .s3Bucket(getEnv("S3_BUCKET", "logs-bucket"))
                .localLogDir(getEnv("LOCAL_LOG_DIR", "./logs"))
                .elasticConnectionString(getEnv("ELASTICSEARCH_HOST", "http://elasticsearch:9200"))
                .s3Region(getEnv("S3_REGION", "auto"))
                .s3Endpoint(getEnv("S3_ENDPOINT", ""))
                .build();
    }

    private static String getEnv(String key, String defaultValue) {
        // Ưu tiên: System env > .env file > default value

        // 1. Kiểm tra System environment variables trước (Docker -e hoặc system env)
        String systemEnv = System.getenv(key);
        if (systemEnv != null && !systemEnv.isEmpty()) {
            return systemEnv;
        }

        // 2. Thử load từ .env file (nếu tồn tại)
        try {
            if (dotenv == null) {
                dotenv = Dotenv.configure()
                        .ignoreIfMissing() // Không throw exception nếu file không tồn tại
                        .load();
            }
            String dotenvValue = dotenv.get(key);
            if (dotenvValue != null && !dotenvValue.isEmpty()) {
                return dotenvValue;
            }
        } catch (Exception e) {
            // Bỏ qua lỗi, sẽ dùng default value
            System.out.println("Warning: Could not load .env file, using system env or defaults");
        }

        // 3. Trả về default value
        return defaultValue;
    }

    public void validate() {
        if (s3KeyId.isEmpty() || s3SecretKey.isEmpty() || s3Endpoint.isEmpty()) {
            throw new IllegalStateException("S3 credentials are required: S3_ACCESS_KEY, S3_SECRET_KEY, S3_ENDPOINT");
        }
        if (ftpHost.isEmpty()) {
            throw new IllegalStateException("FTP_HOST is required");
        }
    }

    public static Configuration getInstance() {
        if (instance == null) {
            instance = Configuration.fromEnvironment();
            instance.validate();
        }
        return instance;
    }
}