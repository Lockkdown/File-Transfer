package com.drivelite.server.db.entity;

import java.time.LocalDateTime;

/**
 * Entity đại diện cho bảng Sessions trong database.
 * Lưu session token sau khi user login.
 */
public class Session {
    private int sessionId;
    private int userId;
    private String token;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    public Session() {}

    public Session(int sessionId, int userId, String token, LocalDateTime expiresAt, LocalDateTime createdAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * Kiểm tra session đã hết hạn chưa.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    @Override
    public String toString() {
        return String.format("Session{id=%d, userId=%d, expired=%s}", sessionId, userId, isExpired());
    }
}
