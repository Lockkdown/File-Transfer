package com.drivelite.server.handler;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.common.protocol.ResponseCode;
import com.drivelite.server.db.entity.FileMetadata;
import com.drivelite.server.db.repository.FilePermissionRepository;
import com.drivelite.server.db.repository.FileRepository;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.net.RequestHandler;

/**
 * Handler cho RENAME_FILE request.
 * Đổi tên file (OriginalName trong DB).
 * Chỉ OWNER mới có quyền đổi tên.
 */
public class RenameFileHandler implements RequestHandler {

    private final FileRepository fileRepository;
    private final FilePermissionRepository permissionRepository;

    public RenameFileHandler() {
        this.fileRepository = new FileRepository();
        this.permissionRepository = new FilePermissionRepository();
    }

    @Override
    public Response handle(Request request, ClientContext context) {
        try {
            // 1. Lấy userId từ context
            int userId = context.getUserId();

            // 2. Parse request data
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) request.getData();
            
            if (data == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Missing request data");
            }

            Object fileIdObj = data.get("fileId");
            String newName = (String) data.get("newName");

            if (fileIdObj == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "fileId is required");
            }
            if (newName == null || newName.trim().isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "newName is required");
            }

            int fileId = ((Number) fileIdObj).intValue();

            // 3. Sanitize new name
            newName = sanitizeFileName(newName);
            if (newName.isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Invalid newName");
            }

            // 4. Kiểm tra file tồn tại
            Optional<FileMetadata> fileOpt = fileRepository.findById(fileId);
            if (fileOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, "File not found");
            }

            FileMetadata file = fileOpt.get();
            String oldName = file.getOriginalName();

            // 5. Kiểm tra quyền OWNER
            if (!permissionRepository.isOwner(fileId, userId)) {
                System.out.println("[RENAME_FILE] Forbidden: userId=" + userId + 
                                 " is not OWNER of fileId=" + fileId);
                return Response.error(ResponseCode.FORBIDDEN, "Only OWNER can rename file");
            }

            // 6. Update tên file
            boolean updated = fileRepository.updateFileName(fileId, newName);
            if (!updated) {
                return Response.error(ResponseCode.SERVER_ERROR, "Failed to rename file");
            }

            System.out.println("[RENAME_FILE] Success: fileId=" + fileId + 
                             ", oldName=" + oldName + " → newName=" + newName +
                             ", by userId=" + userId);

            return Response.success("File renamed successfully", Map.of(
                "fileId", fileId,
                "oldName", oldName,
                "newName", newName
            ));

        } catch (SQLException e) {
            System.err.println("[RENAME_FILE] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[RENAME_FILE] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Rename failed");
        }
    }

    /**
     * Sanitize filename để tránh path traversal.
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "";
        
        // Loại bỏ path separators
        fileName = fileName.replace("\\", "/");
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        
        // Loại bỏ các ký tự nguy hiểm
        fileName = fileName.replaceAll("[<>:\"|?*]", "_");
        
        // Không cho phép bắt đầu bằng dấu chấm
        while (fileName.startsWith(".")) {
            fileName = fileName.substring(1);
        }
        
        // Giới hạn độ dài
        if (fileName.length() > 255) {
            fileName = fileName.substring(0, 255);
        }
        
        return fileName.trim();
    }
}
