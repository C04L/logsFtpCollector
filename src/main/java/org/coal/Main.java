package org.coal;

import java.sql.Connection;
import java.time.Duration;

public class Main {
    public static void main(String[] args) throws Exception {
        Configuration.getInstance();
        DatabaseConnection.initDatabase();
        S3Connection.initS3Client();
        ElasticSearch.initElasticConfiguration();

        FTPConnection ftpConnection = new FTPConnection();

        while (true) {
            try {
                ftpConnection.collectLogs();
                Thread.sleep(Duration.ofMinutes(5).toMillis());
            } catch (Exception e) {
                System.err.println("Error during log collection: " + e.getMessage());
                e.printStackTrace();
                Thread.sleep(Duration.ofMinutes(1).toMillis());
            }
        }
    }
}