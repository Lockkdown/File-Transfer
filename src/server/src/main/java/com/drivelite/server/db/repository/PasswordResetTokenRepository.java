package com.drivelite.server.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import com.drivelite.server.db.DatabaseManager;
import com.drivelite.server.db.entity.PasswordResetToken;

/**
 * Repository để thao tác với bảng PasswordResetTokens.
 * Quản lý token để reset password.
 */
public class PasswordResetTokenRepository {

    private static final int TOKEN_EXPIRY_MINUTES = 15;

    /**
     * Tạo reset token (OTP) mới cho user.
     * Schema: Token là PRIMARY KEY, UsedAt là nullable datetime.
     * 
     * @param userId ID của user cần reset password
     * @param otp Mã OTP 6 số
     * @return PasswordResetToken mới
     */
    public PasswordResetToken create(int userId, String otp) throws SQLException {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES);
        
        // Schema: Token (PK), UserId, ExpiresAt, UsedAt (nullable), CreatedAt
        String sql = "INSERT INTO PasswordResetTokens (Token, UserId, ExpiresAt) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, otp);
            stmt.setInt(2, userId);
            stmt.setTimestamp(3, Timestamp.valueOf(expiresAt));
            
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Tạo reset token thất bại.");
            }
            
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setTokenId(0); // Schema không có TokenId auto-increment
            resetToken.setUserId(userId);
            resetToken.setToken(otp);
            resetToken.setExpiresAt(expiresAt);
            resetToken.setUsed(false);
            resetToken.setCreatedAt(LocalDateTime.now());
            return resetToken;
        }
    }

    /**
     * Tìm reset token theo token string.
     * Chỉ trả về token chưa dùng (UsedAt IS NULL) và còn hiệu lực.
     * 
     * @param token token string
     * @return Optional chứa PasswordResetToken nếu tìm thấy và còn hiệu lực
     */
    public Optional<PasswordResetToken> findByToken(String token) throws SQLException {
        // Schema: UsedAt IS NULL = chưa dùng
        String sql = "SELECT UserId, Token, ExpiresAt, UsedAt, CreatedAt " +
                     "FROM PasswordResetTokens WHERE Token = ? AND UsedAt IS NULL AND ExpiresAt > GETDATE()";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, token);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToToken(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Đánh dấu token đã được sử dụng.
     * Schema: UsedAt = GETDATE() thay vì Used = 1
     * 
     * @param token Token string (là PRIMARY KEY)
     * @return true nếu cập nhật thành công
     */
    public boolean markAsUsed(String token) throws SQLException {
        String sql = "UPDATE PasswordResetTokens SET UsedAt = GETDATE() WHERE Token = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, token);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Xóa tất cả token của user (khi tạo token mới hoặc sau khi reset thành công).
     * 
     * @param userId ID user
     * @return số token đã xóa
     */
    public int deleteAllByUserId(int userId) throws SQLException {
        String sql = "DELETE FROM PasswordResetTokens WHERE UserId = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            return stmt.executeUpdate();
        }
    }

    /**
     * Xóa các token đã hết hạn hoặc đã dùng (cleanup).
     * 
     * @return số token đã xóa
     */
    public int deleteExpiredTokens() throws SQLException {
        // Schema: UsedAt IS NOT NULL = đã dùng
        String sql = "DELETE FROM PasswordResetTokens WHERE ExpiresAt < GETDATE() OR UsedAt IS NOT NULL";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            return stmt.executeUpdate();
        }
    }

    /**
     * Map ResultSet sang PasswordResetToken object.
     * Schema: UsedAt (nullable datetime) thay vì Used (bit)
     */
    private PasswordResetToken mapResultSetToToken(ResultSet rs) throws SQLException {
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenId(0); // Schema không có TokenId
        token.setUserId(rs.getInt("UserId"));
        token.setToken(rs.getString("Token"));
        
        Timestamp expiresAt = rs.getTimestamp("ExpiresAt");
        if (expiresAt != null) {
            token.setExpiresAt(expiresAt.toLocalDateTime());
        }
        
        // UsedAt IS NULL = chưa dùng (used = false)
        Timestamp usedAt = rs.getTimestamp("UsedAt");
        token.setUsed(usedAt != null);
        
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            token.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        return token;
    }
}
