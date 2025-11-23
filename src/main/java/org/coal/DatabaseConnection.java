package org.coal;

import lombok.Singular;

import java.sql.*;

public class DatabaseConnection {

    public static Connection dbConnection;


    public static void initDatabase() throws SQLException, ClassNotFoundException {

        dbConnection = DriverManager.getConnection("jdbc:sqlite:database.sqlite");

        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS downloaded_files (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                filename TEXT NOT NULL,
                file_size BIGINT,
                source_path TEXT UNIQUE NOT NULL,
                downloaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                ftp_modified_time TIMESTAMP,
                s3_uploaded BOOLEAN DEFAULT 0
            )
            """;

        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute(createTableSQL);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_filename ON downloaded_files(filename)");
        }

        System.out.println("Database initialized");
    }

}