package com.drivelite.server.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.drivelite.server.db.DatabaseManager;
import com.drivelite.server.db.entity.Session;

/**
 * Repository để thao tác với bảng Sessions.
 * Quản lý session token sau khi user login.
 */
public class SessionRepository {

    /**
     * Tạo session mới cho user.
     * 
     * @param userId ID của user
     * @param expiryHours số giờ session có hiệu lực
     * @return Session mới với token
     */
    public Session create(int userId, int expiryHours) throws SQLException {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(expiryHours);
        
        String sql = "INSERT INTO Sessions (UserId, Token, ExpiresAt) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, token);
            stmt.setTimestamp(3, Timestamp.valueOf(expiresAt));
            
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Tạo session thất bại.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    Session session = new Session();
                    session.setSessionId(generatedKeys.getInt(1));
                    session.setUserId(userId);
                    session.setToken(token);
                    session.setExpiresAt(expiresAt);
                    session.setCreatedAt(LocalDateTime.now());
                    return session;
                } else {
                    throw new SQLException("Tạo session thất bại, không lấy được ID.");
                }
            }
        }
    }

    /**
     * Tìm session theo token.
     * Chỉ trả về session còn hiệu lực (chưa hết hạn).
     * 
     * @param token session token
     * @return Optional chứa Session nếu tìm thấy và còn hiệu lực
     */
    public Optional<Session> findByToken(String token) throws SQLException {
        String sql = "SELECT SessionId, UserId, Token, ExpiresAt, CreatedAt " +
                     "FROM Sessions WHERE Token = ? AND ExpiresAt > GETDATE()";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, token);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToSession(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Xóa session (logout).
     * 
     * @param token session token cần xóa
     * @return true nếu xóa thành công
     */
    public boolean deleteByToken(String token) throws SQLException {
        String sql = "DELETE FROM Sessions WHERE Token = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, token);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Xóa tất cả session của user (force logout all devices).
     * 
     * @param userId ID user
     * @return số session đã xóa
     */
    public int deleteAllByUserId(int userId) throws SQLException {
        String sql = "DELETE FROM Sessions WHERE UserId = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            return stmt.executeUpdate();
        }
    }

    /**
     * Xóa các session đã hết hạn (cleanup).
     * Nên chạy định kỳ để giữ bảng Sessions gọn.
     * 
     * @return số session đã xóa
     */
    public int deleteExpiredSessions() throws SQLException {
        String sql = "DELETE FROM Sessions WHERE ExpiresAt < GETDATE()";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            return stmt.executeUpdate();
        }
    }

    /**
     * Map ResultSet sang Session object.
     */
    private Session mapResultSetToSession(ResultSet rs) throws SQLException {
        Session session = new Session();
        session.setSessionId(rs.getInt("SessionId"));
        session.setUserId(rs.getInt("UserId"));
        session.setToken(rs.getString("Token"));
        
        Timestamp expiresAt = rs.getTimestamp("ExpiresAt");
        if (expiresAt != null) {
            session.setExpiresAt(expiresAt.toLocalDateTime());
        }
        
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            session.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        return session;
    }
}
