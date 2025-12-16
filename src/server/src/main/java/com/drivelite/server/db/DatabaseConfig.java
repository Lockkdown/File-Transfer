package com.drivelite.server.db;

import java.io.File;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Cấu hình kết nối database từ file .env
 */
public class DatabaseConfig {
    private final String host;
    private final int port;
    private final String databaseName;
    private final String username;
    private final String password;

    public DatabaseConfig() {
        // Load từ file .env ở thư mục gốc project (File_Transfer/)
        Dotenv dotenv = Dotenv.configure()
                .directory(findEnvDirectory())
                .ignoreIfMissing()
                .load();

        this.host = dotenv.get("DB_HOST", "localhost");
        this.port = Integer.parseInt(dotenv.get("DB_PORT", "1433"));
        this.databaseName = dotenv.get("DB_NAME", "FileTransfer");
        this.username = dotenv.get("DB_USER", "sa");
        this.password = dotenv.get("DB_PASSWORD", "");
    }

    /**
     * Tìm thư mục chứa file .env
     * Thử nhiều đường dẫn để tương thích khi chạy từ các vị trí khác nhau
     */
    private static String findEnvDirectory() {
        // Các đường dẫn có thể chứa .env
        String[] possiblePaths = {
            ".",                           // Current directory
            "..",                          // Parent (nếu chạy từ src/)
            "../..",                       // Nếu chạy từ src/server/
            "../../..",                    // Nếu chạy từ target/classes
            System.getProperty("user.dir") // Working directory
        };
        
        for (String path : possiblePaths) {
            File envFile = new File(path, ".env");
            if (envFile.exists()) {
                return path;
            }
        }
        
        // Default: current directory
        return ".";
    }

    public DatabaseConfig(String host, int port, String databaseName, String username, String password) {
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
    }

    /**
     * Tạo JDBC URL cho SQL Server
     * Format: jdbc:sqlserver://host:port;databaseName=xxx;encrypt=false;trustServerCertificate=true
     */
    public String getJdbcUrl() {
        return String.format(
            "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false;trustServerCertificate=true",
            host, port, databaseName
        );
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabaseName() { return databaseName; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    @Override
    public String toString() {
        return String.format("DatabaseConfig{host='%s', port=%d, db='%s', user='%s'}",
                host, port, databaseName, username);
    }
}
