package com.drivelite.server.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.drivelite.server.db.DatabaseManager;

/**
 * Repository để thao tác với bảng FilePermissions.
 * Permission values: OWNER, EDIT, VIEW
 */
public class FilePermissionRepository {

    /**
     * Thêm permission cho user trên file.
     */
    public boolean addPermission(int fileId, int userId, String permission, int grantedBy) throws SQLException {
        String sql = "INSERT INTO FilePermissions (FileId, UserId, Permission, GrantedBy) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, userId);
            stmt.setString(3, permission);
            stmt.setInt(4, grantedBy);
            
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Lấy permission của user trên file.
     * Returns: OWNER, EDIT, VIEW, or empty if no permission.
     */
    public Optional<String> getPermission(int fileId, int userId) throws SQLException {
        String sql = "SELECT Permission FROM FilePermissions WHERE FileId = ? AND UserId = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("Permission"));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Kiểm tra user có permission tối thiểu trên file không.
     * Permission hierarchy: OWNER > EDIT > VIEW
     */
    public boolean hasMinimumPermission(int fileId, int userId, String minPermission) throws SQLException {
        Optional<String> permOpt = getPermission(fileId, userId);
        if (permOpt.isEmpty()) {
            return false;
        }
        
        String perm = permOpt.get();
        int userLevel = permissionLevel(perm);
        int minLevel = permissionLevel(minPermission);
        
        return userLevel >= minLevel;
    }

    /**
     * Kiểm tra user có phải OWNER của file không.
     */
    public boolean isOwner(int fileId, int userId) throws SQLException {
        Optional<String> permOpt = getPermission(fileId, userId);
        return permOpt.isPresent() && "OWNER".equals(permOpt.get());
    }

    /**
     * Cập nhật permission của user trên file.
     */
    public boolean updatePermission(int fileId, int userId, String newPermission) throws SQLException {
        String sql = "UPDATE FilePermissions SET Permission = ? WHERE FileId = ? AND UserId = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, newPermission);
            stmt.setInt(2, fileId);
            stmt.setInt(3, userId);
            
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Xóa permission của user trên file.
     */
    public boolean removePermission(int fileId, int userId) throws SQLException {
        String sql = "DELETE FROM FilePermissions WHERE FileId = ? AND UserId = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, userId);
            
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Permission level: OWNER=3, EDIT=2, VIEW=1
     */
    private int permissionLevel(String permission) {
        if (permission == null) return 0;
        switch (permission.toUpperCase()) {
            case "OWNER": return 3;
            case "EDIT": return 2;
            case "VIEW": return 1;
            default: return 0;
        }
    }

    /**
     * Lấy tất cả permissions của một file (để hiển thị danh sách người được share).
     * Returns list of maps with userId, email, displayName, permission, grantedAt.
     */
    public List<Map<String, Object>> findAllByFileId(int fileId) throws SQLException {
        String sql = "SELECT fp.UserId, u.Email, u.DisplayName, fp.Permission, fp.GrantedAt " +
                     "FROM FilePermissions fp " +
                     "INNER JOIN Users u ON fp.UserId = u.UserId " +
                     "WHERE fp.FileId = ? " +
                     "ORDER BY fp.Permission DESC, fp.GrantedAt ASC";
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("userId", rs.getInt("UserId"));
                    item.put("email", rs.getString("Email"));
                    item.put("displayName", rs.getString("DisplayName"));
                    item.put("permission", rs.getString("Permission"));
                    item.put("grantedAt", rs.getTimestamp("GrantedAt").toLocalDateTime().toString());
                    results.add(item);
                }
            }
        }
        return results;
    }

    /**
     * Kiểm tra permission đã tồn tại chưa.
     */
    public boolean exists(int fileId, int userId) throws SQLException {
        String sql = "SELECT 1 FROM FilePermissions WHERE FileId = ? AND UserId = ?";
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Lấy danh sách files được share cho user (VIEW hoặc EDIT, không phải OWNER).
     * Returns list of maps with fileId and permission.
     */
    public List<Map<String, Object>> findSharedFilesForUser(int userId) throws SQLException {
        String sql = "SELECT fp.FileId, fp.Permission " +
                     "FROM FilePermissions fp " +
                     "INNER JOIN Files f ON fp.FileId = f.FileId " +
                     "WHERE fp.UserId = ? AND fp.Permission IN ('VIEW', 'EDIT') AND f.IsDeleted = 0 " +
                     "ORDER BY fp.GrantedAt DESC";
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("fileId", rs.getInt("FileId"));
                    item.put("permission", rs.getString("Permission"));
                    results.add(item);
                }
            }
        }
        return results;
    }
}
