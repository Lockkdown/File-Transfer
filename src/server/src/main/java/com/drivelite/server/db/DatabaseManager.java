package com.drivelite.server.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Quản lý kết nối database.
 * Sử dụng pattern Singleton để đảm bảo chỉ có 1 instance.
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private final DatabaseConfig config;

    private DatabaseManager(DatabaseConfig config) {
        this.config = config;
    }

    /**
     * Khởi tạo DatabaseManager với config.
     * Gọi 1 lần khi server start.
     */
    public static synchronized void initialize(DatabaseConfig config) {
        if (instance == null) {
            instance = new DatabaseManager(config);
        }
    }

    /**
     * Lấy instance của DatabaseManager.
     * Phải gọi initialize() trước.
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("DatabaseManager chưa được khởi tạo. Gọi initialize() trước.");
        }
        return instance;
    }

    /**
     * Tạo một Connection mới đến database.
     * Caller có trách nhiệm đóng connection sau khi dùng xong (dùng try-with-resources).
     * 
     * @return Connection mới
     * @throws SQLException nếu không kết nối được
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
            config.getJdbcUrl(),
            config.getUsername(),
            config.getPassword()
        );
    }

    /**
     * Test kết nối database.
     * 
     * @return true nếu kết nối thành công
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5); // timeout 5 giây
        } catch (SQLException e) {
            System.err.println("Lỗi kết nối database: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy thông tin config (để debug/log).
     */
    public DatabaseConfig getConfig() {
        return config;
    }

    /**
     * Reset instance (chỉ dùng cho testing).
     */
    public static synchronized void reset() {
        instance = null;
    }
}
