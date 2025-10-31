package org.coal;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.FileOutputStream;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

public class FTPConnection {
    private final FTPClient client = new FTPClient();
    private static final Configuration configuration = Configuration.getInstance();
    private final Connection dbConnection = DatabaseConnection.dbConnection;
    private final S3Client s3Client = S3Connection.getS3Client();

    public void collectLogs() throws Exception {
        try {
            client.connect(configuration.getFtpHost(), configuration.getFtpPort());
            client.login(configuration.getFtpUsername(), configuration.getFtpPassword());
            client.enterLocalPassiveMode();
            client.setFileType(FTPClient.BINARY_FILE_TYPE);
            String remoteDir = configuration.getFtpRemoteDir();
            client.changeWorkingDirectory(remoteDir);

            FTPFile[] files = client.listFiles();
            int newFiles = 0;
            int skippedFiles = 0;

            for (FTPFile file : files) {
                if (file.isFile() && file.getName().endsWith(".log")) {
                    if (isFileAlreadyDownloaded(file.getName())) {
                        skippedFiles++;
                        continue;
                    }

                    downloadAndProcess(client, file);
                    newFiles++;
                }
            }

             System.out.println(String.format("[%s] Scan complete - New: %d, Skipped: %d",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), newFiles, skippedFiles));

        } finally {
            if (client.isConnected()) {
                client.logout();
                client.disconnect();
            }
        }
    }

    private boolean isFileAlreadyDownloaded(String fileName) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM downloaded_files WHERE filename = ?)";

        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
            pstmt.setString(1, fileName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private void downloadAndProcess(FTPClient ftpClient, FTPFile ftpFile) throws Exception {
        String fileName = ftpFile.getName();
        Path localDir = Paths.get(configuration.getLocalLogDir());
        Files.createDirectories(localDir);

        Path localFile = localDir.resolve(fileName);

        try (OutputStream outputStream = new FileOutputStream(localFile.toFile())) {
            boolean success = ftpClient.retrieveFile(fileName, outputStream);
            if (!success) {
                System.err.println("Failed to download: " + fileName);
                return;
            }
        }

        System.out.println("Downloaded: " + fileName + " (" + ftpFile.getSize() + " bytes)");

        boolean s3Success = uploadToS3(localFile.toFile(), fileName, s3Client);

        recordDownloadedFile(fileName, ftpFile.getSize(), ftpFile.getTimestamp(), s3Success);
    }

    private static boolean uploadToS3(File file, String fileName, S3Client s3Client) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(configuration.getS3Bucket())
                    .key(fileName)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromFile(file));
            System.out.println("Uploaded to S3: " + fileName);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to upload to S3: " + fileName);
            e.printStackTrace();
            return false;
        }
    }

    private void recordDownloadedFile(String fileName, long fileSize,
                                             Calendar ftpModifiedTime, boolean s3Uploaded) throws SQLException {
        String sql = """
            INSERT INTO downloaded_files (filename, file_size, ftp_modified_time, s3_uploaded) 
            VALUES (?, ?, ?, ?)
            """;

        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
            pstmt.setString(1, fileName);
            pstmt.setLong(2, fileSize);
            pstmt.setTimestamp(3, ftpModifiedTime != null ?
                new Timestamp(ftpModifiedTime.getTimeInMillis()) : null);
            pstmt.setBoolean(4, s3Uploaded);
            pstmt.executeUpdate();
        }
    }


}
