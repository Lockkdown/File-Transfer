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
 * Handler cho SHARE_REMOVE request.
 * Cho phép OWNER thu hồi quyền truy cập của user khác.
 * 
 * Request: { fileId, targetUserId }
 * 
 * Response: { ok, code, message }
 */
public class ShareRemoveHandler implements RequestHandler {

    private final FileRepository fileRepository;
    private final FilePermissionRepository permissionRepository;

    public ShareRemoveHandler() {
        this.fileRepository = new FileRepository();
        this.permissionRepository = new FilePermissionRepository();
    }

    @Override
    public Response handle(Request request, ClientContext context) {
        try {
            // 1. Kiểm tra authentication
            if (!context.isAuthenticated()) {
                return Response.error(ResponseCode.UNAUTHORIZED, "Authentication required");
            }

            int currentUserId = context.getUserId();

            // 2. Parse request data
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) request.getData();
            
            if (data == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Missing request data");
            }

            // Validate fileId
            Object fileIdObj = data.get("fileId");
            if (fileIdObj == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "fileId is required");
            }
            int fileId = ((Number) fileIdObj).intValue();

            // Validate targetUserId
            Object targetUserIdObj = data.get("targetUserId");
            if (targetUserIdObj == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "targetUserId is required");
            }
            int targetUserId = ((Number) targetUserIdObj).intValue();

            // 3. Kiểm tra file tồn tại
            Optional<FileMetadata> fileOpt = fileRepository.findById(fileId);
            if (fileOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, "File not found");
            }

            // 4. Kiểm tra quyền OWNER
            if (!permissionRepository.isOwner(fileId, currentUserId)) {
                System.out.println("[SHARE_REMOVE] Forbidden: userId=" + currentUserId + 
                                 " is not OWNER of fileId=" + fileId);
                return Response.error(ResponseCode.FORBIDDEN, 
                    "Only file owner can remove shares");
            }

            // 5. Không thể remove permission của chính mình (OWNER)
            if (targetUserId == currentUserId) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "Cannot remove your own permission");
            }

            // 6. Kiểm tra target user có permission không
            Optional<String> currentPermOpt = permissionRepository.getPermission(fileId, targetUserId);
            if (currentPermOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, 
                    "User does not have permission on this file");
            }

            // 7. Không thể remove OWNER permission (nếu có nhiều owner - edge case)
            if ("OWNER".equals(currentPermOpt.get())) {
                return Response.error(ResponseCode.FORBIDDEN, 
                    "Cannot remove OWNER permission");
            }

            // 8. Remove permission
            boolean removed = permissionRepository.removePermission(fileId, targetUserId);
            if (!removed) {
                return Response.error(ResponseCode.SERVER_ERROR, "Failed to remove permission");
            }

            System.out.println("[SHARE_REMOVE] SUCCESS: userId=" + currentUserId + 
                             " removed share for fileId=" + fileId + 
                             " targetUserId=" + targetUserId);

            // 9. Return success
            return Response.success("Share removed successfully", null);

        } catch (SQLException e) {
            System.err.println("[SHARE_REMOVE] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[SHARE_REMOVE] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Remove share failed");
        }
    }
}
