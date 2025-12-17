package com.drivelite.server.db.entity;

import java.time.LocalDateTime;

/**
 * Entity đại diện cho bảng Files trong database.
 * Lưu metadata của file (không lưu nội dung).
 */
public class FileMetadata {
    private int fileId;
    private int ownerUserId;
    private String originalName;
    private int currentVersion;
    private LocalDateTime createdAt;
    private boolean isDeleted;

    public FileMetadata() {}

    // Getters and Setters
    public int getFileId() { return fileId; }
    public void setFileId(int fileId) { this.fileId = fileId; }

    public int getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(int ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public int getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}
