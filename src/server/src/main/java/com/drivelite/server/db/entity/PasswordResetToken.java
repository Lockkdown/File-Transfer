package com.drivelite.server.db.entity;

import java.time.LocalDateTime;

/**
 * Entity đại diện cho một password reset token.
 * Token có thời hạn 15 phút và chỉ dùng được 1 lần.
 */
public class PasswordResetToken {
    private int tokenId;
    private int userId;
    private String token;
    private LocalDateTime expiresAt;
    private boolean used;
    private LocalDateTime createdAt;

    public PasswordResetToken() {}

    // Getters and Setters
    public int getTokenId() { return tokenId; }
    public void setTokenId(int tokenId) { this.tokenId = tokenId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * Kiểm tra token còn hiệu lực không.
     */
    public boolean isValid() {
        return !used && expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
    }
}
