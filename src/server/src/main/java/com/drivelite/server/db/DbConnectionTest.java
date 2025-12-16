package com.drivelite.server.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Class để test kết nối database.
 * Chạy trực tiếp để kiểm tra kết nối SQL Server.
 */
public class DbConnectionTest {

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("  Database Connection Test");
        System.out.println("=================================");
        
        try {
            // Load config từ .env
            DatabaseConfig config = new DatabaseConfig();
            System.out.println("Config: " + config);
            System.out.println("JDBC URL: " + config.getJdbcUrl());
            System.out.println();
            
            // Khởi tạo DatabaseManager
            DatabaseManager.initialize(config);
            
            // Test kết nối
            System.out.println("Testing connection...");
            if (DatabaseManager.getInstance().testConnection()) {
                System.out.println("✓ Kết nối thành công!");
            } else {
                System.out.println("✗ Kết nối thất bại!");
                return;
            }
            
            // Test query đơn giản
            System.out.println();
            System.out.println("Testing simple query...");
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT @@VERSION AS Version")) {
                
                if (rs.next()) {
                    System.out.println("SQL Server Version:");
                    System.out.println(rs.getString("Version"));
                }
            }
            
            // Kiểm tra tables đã tạo chưa
            System.out.println();
            System.out.println("Checking tables...");
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                     "WHERE TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME")) {
                
                System.out.println("Tables in database:");
                boolean hasTable = false;
                while (rs.next()) {
                    System.out.println("  - " + rs.getString("TABLE_NAME"));
                    hasTable = true;
                }
                if (!hasTable) {
                    System.out.println("  (No tables found - run V1__init_schema.sql first)");
                }
            }
            
            System.out.println();
            System.out.println("=================================");
            System.out.println("  Test completed successfully!");
            System.out.println("=================================");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
