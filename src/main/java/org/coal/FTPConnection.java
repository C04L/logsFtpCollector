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
        
        // Use counters as array to allow modification in recursive method
        int[] counters = new int[2]; // [0] = newFiles, [1] = skippedFiles
        
        scanDirectoryRecursively(channel, remoteDir, counters);

        System.out.println(String.format("[%s] Scan complete - New: %d, Skipped: %d",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            counters[0], counters[1]));

    } finally {
        if (channel != null) {
            channel.exit();
        }
        if (session != null) {
            session.disconnect();
        }
    }
}

private void scanDirectoryRecursively(ChannelSftp channel, String remotePath, int[] counters) throws SftpException {
    try {
        Vector<ChannelSftp.LsEntry> entries = channel.ls(remotePath);
        
        for (ChannelSftp.LsEntry entry : entries) {
            String fileName = entry.getFilename();
            Long fileLength = entry.getAttrs().getSize();
            
            // Skip current and parent directory references
            if (".".equals(fileName) || "..".equals(fileName)) {
                continue;
            }
            
            String fullPath = remotePath + "/" + fileName;
            
            if (entry.getAttrs().isDir()) {
                // Recursively scan subdirectories
                scanDirectoryRecursively(channel, fullPath, counters);
            } else if (fileName.endsWith(".log")) {
                // Process .log files
                if (isFileAlreadyDownloaded(fileName, fileLength, fullPath)) {
                    System.out.println(String.format("[%s] File %s already exists", remotePath, fileName));
                    counters[1]++; // skippedFiles
                    continue;
                }
                
                downloadAndProcess(channel, entry, remotePath);
                counters[0]++; // newFiles
            }
        }
    } catch (SftpException | SQLException e) {
        // Log the error but continue scanning other directories
        System.err.println("Error scanning directory " + remotePath + ": " + e.getMessage());
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}
    private boolean isFileAlreadyDownloaded(String fileName, Long fileSize, String fullPath) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM downloaded_files WHERE filename = ? and file_size = ? and source_path = ?)";
        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
            pstmt.setString(1, fileName);
            pstmt.setLong(2, fileSize);
            pstmt.setString(3, fullPath);
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

        System.out.println("Found: " + fileName);


        boolean s3Success = uploadToS3(localFile.toFile(), fileName, s3Client);


        System.out.println("Downloaded: " + fileName + " (" + entry.getAttrs().getSize() + " bytes)");

        recordDownloadedFile(fileName, entry.getAttrs().getSize(), entry.getAttrs().getMTime(), s3Success, remoteDir + "/" + fileName);
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

    private void recordDownloadedFile(String fileName, long fileSize, int mTimeSeconds, boolean s3Uploaded, String fullPath) throws SQLException {
        String sql = """
            INSERT INTO downloaded_files (filename, file_size, ftp_modified_time, s3_uploaded, source_path)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
            pstmt.setString(1, fileName);
            pstmt.setLong(2, fileSize);

            Timestamp ts = mTimeSeconds > 0 ?
                new Timestamp(mTimeSeconds * 1000L) : null;
            pstmt.setTimestamp(3, ts);

            pstmt.setBoolean(4, s3Uploaded);
            pstmt.setString(5, fullPath);
            pstmt.executeUpdate();
        }
    }
}