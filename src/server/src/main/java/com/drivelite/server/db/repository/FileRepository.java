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
import com.drivelite.server.db.entity.FileMetadata;

/**
 * Repository để thao tác với bảng Files.
 */
public class FileRepository {

    /**
     * Tạo file mới.
     */
    public FileMetadata create(int ownerUserId, String originalName) throws SQLException {
        String sql = "INSERT INTO Files (OwnerUserId, OriginalName, CurrentVersion) VALUES (?, ?, 1)";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, ownerUserId);
            stmt.setString(2, originalName);
            
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating file failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    FileMetadata file = new FileMetadata();
                    file.setFileId(generatedKeys.getInt(1));
                    file.setOwnerUserId(ownerUserId);
                    file.setOriginalName(originalName);
                    file.setCurrentVersion(1);
                    file.setDeleted(false);
                    return file;
                } else {
                    throw new SQLException("Creating file failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Tìm file theo ID.
     */
    public Optional<FileMetadata> findById(int fileId) throws SQLException {
        String sql = "SELECT FileId, OwnerUserId, OriginalName, CurrentVersion, CreatedAt, IsDeleted " +
                     "FROM Files WHERE FileId = ? AND IsDeleted = 0";
        
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

    /**
     * Lấy tất cả files của user (owned by user).
     */
    public List<FileMetadata> findByOwner(int userId) throws SQLException {
        String sql = "SELECT FileId, OwnerUserId, OriginalName, CurrentVersion, CreatedAt, IsDeleted " +
                     "FROM Files WHERE OwnerUserId = ? AND IsDeleted = 0 ORDER BY CreatedAt DESC";
        
        List<FileMetadata> files = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapResultSet(rs));
                }
            }
        }
        return files;
    }

    /**
     * Cập nhật current version của file.
     */
    public boolean updateCurrentVersion(int fileId, int newVersion) throws SQLException {
        String sql = "UPDATE Files SET CurrentVersion = ? WHERE FileId = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, newVersion);
            stmt.setInt(2, fileId);
            
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Soft delete file.
     */
    public boolean delete(int fileId) throws SQLException {
        String sql = "UPDATE Files SET IsDeleted = 1 WHERE FileId = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Đổi tên file.
     */
    public boolean updateFileName(int fileId, String newName) throws SQLException {
        String sql = "UPDATE Files SET OriginalName = ? WHERE FileId = ? AND IsDeleted = 0";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, newName);
            stmt.setInt(2, fileId);
            
            return stmt.executeUpdate() > 0;
        }
    }

    private FileMetadata mapResultSet(ResultSet rs) throws SQLException {
        FileMetadata file = new FileMetadata();
        file.setFileId(rs.getInt("FileId"));
        file.setOwnerUserId(rs.getInt("OwnerUserId"));
        file.setOriginalName(rs.getString("OriginalName"));
        file.setCurrentVersion(rs.getInt("CurrentVersion"));
        
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            file.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        file.setDeleted(rs.getBoolean("IsDeleted"));
        return file;
    }
}
