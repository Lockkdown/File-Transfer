package com.drivelite.server.db.entity;

import java.time.LocalDateTime;

/**
 * Entity đại diện cho bảng Users trong database.
 */
public class User {
    private int userId;
    private String email;
    private String passwordHash;
    private String displayName;
    private LocalDateTime createdAt;
    private boolean isActive;

    public User() {}

    public User(int userId, String email, String passwordHash, String displayName, 
                LocalDateTime createdAt, boolean isActive) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.createdAt = createdAt;
        this.isActive = isActive;
    }

    // Getters and Setters
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    @Override
    public String toString() {
        return String.format("User{id=%d, email='%s', displayName='%s', active=%s}",
                userId, email, displayName, isActive);
    }
}
