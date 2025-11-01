package org.coal;

import com.jcraft.jsch.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Vector;

public class FTPConnection {
    private final Configuration configuration = Configuration.getInstance();
    private final Connection dbConnection = DatabaseConnection.dbConnection;
    private final S3Client s3Client = S3Connection.getS3Client();

    public void collectLogs() throws Exception {
        Session session = null;
        ChannelSftp channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(
                configuration.getFtpUsername(),
                configuration.getFtpHost(),
                configuration.getFtpPort()
            );
            session.setPassword(configuration.getFtpPassword());

            // Avoid host key checking in production (use proper key mgmt)
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();

            String remoteDir = configuration.getFtpRemoteDir();
            channel.cd(remoteDir);

            Vector<ChannelSftp.LsEntry> files = channel.ls("*.log");
            int newFiles = 0;
            int skippedFiles = 0;

            for (ChannelSftp.LsEntry entry : files) {
                if (entry.getAttrs().isDir()) continue;

                String fileName = entry.getFilename();
                if (isFileAlreadyDownloaded(fileName)) {
                    skippedFiles++;
                    continue;
                }

                downloadAndProcess(channel, entry, remoteDir);
                newFiles++;
            }

            System.out.println(String.format("[%s] Scan complete - New: %d, Skipped: %d",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                newFiles, skippedFiles));

        } finally {
            if (channel != null) {
                channel.exit();
            }
            if (session != null) {
                session.disconnect();
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

    private void downloadAndProcess(ChannelSftp channel, ChannelSftp.LsEntry entry, String remoteDir) throws Exception {
        String fileName = entry.getFilename();
        Path localDir = Paths.get(configuration.getLocalLogDir());
        Files.createDirectories(localDir);
        Path localFile = localDir.resolve(fileName);

        try (OutputStream outputStream = new FileOutputStream(localFile.toFile())) {
            channel.get(remoteDir + "/" + fileName, outputStream);
        }

        System.out.println("Downloaded: " + fileName + " (" + entry.getAttrs().getSize() + " bytes)");

        boolean s3Success = uploadToS3(localFile.toFile(), fileName, s3Client);
        recordDownloadedFile(fileName, entry.getAttrs().getSize(), entry.getAttrs().getMTime(), s3Success);
    }

    private static boolean uploadToS3(File file, String fileName, S3Client s3Client) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(Configuration.getInstance().getS3Bucket())
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

    private void recordDownloadedFile(String fileName, long fileSize, int mTimeSeconds, boolean s3Uploaded) throws SQLException {
        String sql = """
            INSERT INTO downloaded_files (filename, file_size, ftp_modified_time, s3_uploaded) 
            VALUES (?, ?, ?, ?)
            """;

        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
            pstmt.setString(1, fileName);
            pstmt.setLong(2, fileSize);

            Timestamp ts = mTimeSeconds > 0 ?
                new Timestamp(mTimeSeconds * 1000L) : null;
            pstmt.setTimestamp(3, ts);

            pstmt.setBoolean(4, s3Uploaded);
            pstmt.executeUpdate();
        }
    }
}