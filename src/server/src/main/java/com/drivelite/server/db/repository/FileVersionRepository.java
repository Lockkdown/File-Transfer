package com.drivelite.server.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.drivelite.server.db.DatabaseManager;
import com.drivelite.server.db.entity.FileVersion;

/**
 * Repository để thao tác với bảng FileVersions.
 */
public class FileVersionRepository {

    /**
     * Tạo version mới cho file.
     */
    public FileVersion create(int fileId, int versionNumber, String storedPath, 
                              long sizeBytes, String sha256, int uploadedBy, String note) throws SQLException {
        String sql = "INSERT INTO FileVersions (FileId, VersionNumber, StoredPath, SizeBytes, Sha256, UploadedBy, Note) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, versionNumber);
            stmt.setString(3, storedPath);
            stmt.setLong(4, sizeBytes);
            stmt.setString(5, sha256);
            stmt.setInt(6, uploadedBy);
            stmt.setString(7, note);
            
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating file version failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    FileVersion version = new FileVersion();
                    version.setVersionId(generatedKeys.getInt(1));
                    version.setFileId(fileId);
                    version.setVersionNumber(versionNumber);
                    version.setStoredPath(storedPath);
                    version.setSizeBytes(sizeBytes);
                    version.setSha256(sha256);
                    version.setUploadedBy(uploadedBy);
                    version.setNote(note);
                    return version;
                } else {
                    throw new SQLException("Creating file version failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Tìm version theo fileId và versionNumber.
     */
    public Optional<FileVersion> findByFileIdAndVersion(int fileId, int versionNumber) throws SQLException {
        String sql = "SELECT VersionId, FileId, VersionNumber, StoredPath, SizeBytes, Sha256, UploadedBy, UploadedAt, Note " +
                     "FROM FileVersions WHERE FileId = ? AND VersionNumber = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, versionNumber);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Lấy tất cả versions của file.
     */
    public List<FileVersion> findByFileId(int fileId) throws SQLException {
        String sql = "SELECT VersionId, FileId, VersionNumber, StoredPath, SizeBytes, Sha256, UploadedBy, UploadedAt, Note " +
                     "FROM FileVersions WHERE FileId = ? ORDER BY VersionNumber DESC";
        
        List<FileVersion> versions = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    versions.add(mapResultSet(rs));
                }
            }
        }
        return versions;
    }

    /**
     * Lấy version mới nhất của file.
     */
    public Optional<FileVersion> findLatestVersion(int fileId) throws SQLException {
        String sql = "SELECT TOP 1 VersionId, FileId, VersionNumber, StoredPath, SizeBytes, Sha256, UploadedBy, UploadedAt, Note " +
                     "FROM FileVersions WHERE FileId = ? ORDER BY VersionNumber DESC";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    private FileVersion mapResultSet(ResultSet rs) throws SQLException {
        FileVersion version = new FileVersion();
        version.setVersionId(rs.getInt("VersionId"));
        version.setFileId(rs.getInt("FileId"));
        version.setVersionNumber(rs.getInt("VersionNumber"));
        version.setStoredPath(rs.getString("StoredPath"));
        version.setSizeBytes(rs.getLong("SizeBytes"));
        version.setSha256(rs.getString("Sha256"));
        version.setUploadedBy(rs.getInt("UploadedBy"));
        
        Timestamp uploadedAt = rs.getTimestamp("UploadedAt");
        if (uploadedAt != null) {
            version.setUploadedAt(uploadedAt.toLocalDateTime());
        }
        
        version.setNote(rs.getString("Note"));
        return version;
    }
}
