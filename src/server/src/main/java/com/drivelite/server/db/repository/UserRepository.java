package com.drivelite.server.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import com.drivelite.server.db.DatabaseManager;
import com.drivelite.server.db.entity.User;

/**
 * Repository để thao tác với bảng Users.
 * Sử dụng PreparedStatement để chống SQL Injection.
 */
public class UserRepository {

    /**
     * Tìm user theo email.
     * 
     * @param email email cần tìm
     * @return Optional chứa User nếu tìm thấy, empty nếu không
     */
    public Optional<User> findByEmail(String email) throws SQLException {
        String sql = "SELECT UserId, Email, PasswordHash, DisplayName, CreatedAt, IsActive " +
                     "FROM Users WHERE Email = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, email);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Tìm user theo ID.
     * 
     * @param userId ID cần tìm
     * @return Optional chứa User nếu tìm thấy
     */
    public Optional<User> findById(int userId) throws SQLException {
        String sql = "SELECT UserId, Email, PasswordHash, DisplayName, CreatedAt, IsActive " +
                     "FROM Users WHERE UserId = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Tạo user mới.
     * 
     * @param email email
     * @param passwordHash BCrypt hash của password
     * @param displayName tên hiển thị
     * @return User mới với ID được gán
     */
    public User create(String email, String passwordHash, String displayName) throws SQLException {
        String sql = "INSERT INTO Users (Email, PasswordHash, DisplayName) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, email);
            stmt.setString(2, passwordHash);
            stmt.setString(3, displayName);
            
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Tạo user thất bại, không có row nào được insert.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    User user = new User();
                    user.setUserId(generatedKeys.getInt(1));
                    user.setEmail(email);
                    user.setPasswordHash(passwordHash);
                    user.setDisplayName(displayName);
                    user.setCreatedAt(LocalDateTime.now());
                    user.setActive(true);
                    return user;
                } else {
                    throw new SQLException("Tạo user thất bại, không lấy được ID.");
                }
            }
        }
    }

    /**
     * Kiểm tra email đã tồn tại chưa.
     * 
     * @param email email cần kiểm tra
     * @return true nếu đã tồn tại
     */
    public boolean existsByEmail(String email) throws SQLException {
        String sql = "SELECT 1 FROM Users WHERE Email = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, email);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Cập nhật password hash của user.
     * 
     * @param userId ID user
     * @param newPasswordHash hash mới
     * @return true nếu cập nhật thành công
     */
    public boolean updatePassword(int userId, String newPasswordHash) throws SQLException {
        String sql = "UPDATE Users SET PasswordHash = ? WHERE UserId = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, newPasswordHash);
            stmt.setInt(2, userId);
            
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Map ResultSet sang User object.
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("UserId"));
        user.setEmail(rs.getString("Email"));
        user.setPasswordHash(rs.getString("PasswordHash"));
        user.setDisplayName(rs.getString("DisplayName"));
        
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        user.setActive(rs.getBoolean("IsActive"));
        return user;
    }
}
