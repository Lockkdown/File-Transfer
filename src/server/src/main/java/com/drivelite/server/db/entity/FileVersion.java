package com.drivelite.server.db.entity;

import java.time.LocalDateTime;

/**
 * Entity đại diện cho bảng FileVersions trong database.
 * Lưu từng version của file.
 */
public class FileVersion {
    private int versionId;
    private int fileId;
    private int versionNumber;
    private String storedPath;
    private long sizeBytes;
    private String sha256;
    private int uploadedBy;
    private LocalDateTime uploadedAt;
    private String note;

    public FileVersion() {}

    // Getters and Setters
    public int getVersionId() { return versionId; }
    public void setVersionId(int versionId) { this.versionId = versionId; }

    public int getFileId() { return fileId; }
    public void setFileId(int fileId) { this.fileId = fileId; }

    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }

    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public int getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(int uploadedBy) { this.uploadedBy = uploadedBy; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
