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
 * Handler cho DELETE_FILE request.
 * Soft delete file (set IsDeleted = true).
 * Chỉ OWNER mới có quyền xóa.
 */
public class DeleteFileHandler implements RequestHandler {

    private final FileRepository fileRepository;
    private final FilePermissionRepository permissionRepository;

    public DeleteFileHandler() {
        this.fileRepository = new FileRepository();
        this.permissionRepository = new FilePermissionRepository();
    }

    @Override
    public Response handle(Request request, ClientContext context) {
        try {
            // 1. Lấy userId từ context (đã được authenticate bởi AuthMiddleware)
            int userId = context.getUserId();

            // 2. Parse request data
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) request.getData();
            
            if (data == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Missing request data");
            }

            Object fileIdObj = data.get("fileId");
            if (fileIdObj == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "fileId is required");
            }

            int fileId = ((Number) fileIdObj).intValue();

            // 3. Kiểm tra file tồn tại
            Optional<FileMetadata> fileOpt = fileRepository.findById(fileId);
            if (fileOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, "File not found");
            }

            FileMetadata file = fileOpt.get();

            // 4. Kiểm tra quyền OWNER
            if (!permissionRepository.isOwner(fileId, userId)) {
                System.out.println("[DELETE_FILE] Forbidden: userId=" + userId + 
                                 " is not OWNER of fileId=" + fileId);
                return Response.error(ResponseCode.FORBIDDEN, "Only OWNER can delete file");
            }

            // 5. Soft delete file
            boolean deleted = fileRepository.delete(fileId);
            if (!deleted) {
                return Response.error(ResponseCode.SERVER_ERROR, "Failed to delete file");
            }

            System.out.println("[DELETE_FILE] Success: fileId=" + fileId + 
                             ", fileName=" + file.getOriginalName() + 
                             ", by userId=" + userId);

            return Response.success("File deleted successfully", Map.of(
                "fileId", fileId,
                "fileName", file.getOriginalName()
            ));

        } catch (SQLException e) {
            System.err.println("[DELETE_FILE] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[DELETE_FILE] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Delete failed");
        }
    }
}
