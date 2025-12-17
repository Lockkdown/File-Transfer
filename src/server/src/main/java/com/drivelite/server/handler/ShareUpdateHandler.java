package com.drivelite.server.handler;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.common.protocol.ResponseCode;
import com.drivelite.server.db.entity.FileMetadata;
import com.drivelite.server.db.entity.User;
import com.drivelite.server.db.repository.FilePermissionRepository;
import com.drivelite.server.db.repository.FileRepository;
import com.drivelite.server.db.repository.UserRepository;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.net.RequestHandler;

/**
 * Handler cho SHARE_UPDATE request.
 * Cho phép OWNER cập nhật permission của user đã được share.
 * 
 * Request: { fileId, targetUserId, newPermission }
 * - newPermission: "VIEW" hoặc "EDIT"
 * 
 * Response: { ok, code, message, data: { userId, permission } }
 */
public class ShareUpdateHandler implements RequestHandler {

    private final FileRepository fileRepository;
    private final FilePermissionRepository permissionRepository;
    private final UserRepository userRepository;

    public ShareUpdateHandler() {
        this.fileRepository = new FileRepository();
        this.permissionRepository = new FilePermissionRepository();
        this.userRepository = new UserRepository();
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

            // Validate newPermission
            String newPermission = (String) data.get("newPermission");
            if (newPermission == null || newPermission.isBlank()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "newPermission is required");
            }
            newPermission = newPermission.toUpperCase();

            // Chỉ cho phép VIEW hoặc EDIT
            if (!newPermission.equals("VIEW") && !newPermission.equals("EDIT")) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "newPermission must be VIEW or EDIT");
            }

            // 3. Kiểm tra file tồn tại
            Optional<FileMetadata> fileOpt = fileRepository.findById(fileId);
            if (fileOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, "File not found");
            }

            // 4. Kiểm tra quyền OWNER
            if (!permissionRepository.isOwner(fileId, currentUserId)) {
                System.out.println("[SHARE_UPDATE] Forbidden: userId=" + currentUserId + 
                                 " is not OWNER of fileId=" + fileId);
                return Response.error(ResponseCode.FORBIDDEN, 
                    "Only file owner can update shares");
            }

            // 5. Không thể update permission của chính mình (OWNER)
            if (targetUserId == currentUserId) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "Cannot update your own permission");
            }

            // 6. Kiểm tra target user tồn tại
            Optional<User> targetUserOpt = userRepository.findById(targetUserId);
            if (targetUserOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, "Target user not found");
            }

            // 7. Kiểm tra target user có permission không
            Optional<String> currentPermOpt = permissionRepository.getPermission(fileId, targetUserId);
            if (currentPermOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, 
                    "User does not have permission on this file. Use SHARE_ADD first.");
            }

            // 8. Không thể update OWNER permission
            if ("OWNER".equals(currentPermOpt.get())) {
                return Response.error(ResponseCode.FORBIDDEN, 
                    "Cannot update OWNER permission");
            }

            // 9. Update permission
            boolean updated = permissionRepository.updatePermission(fileId, targetUserId, newPermission);
            if (!updated) {
                return Response.error(ResponseCode.SERVER_ERROR, "Failed to update permission");
            }

            System.out.println("[SHARE_UPDATE] SUCCESS: userId=" + currentUserId + 
                             " updated fileId=" + fileId + 
                             " targetUserId=" + targetUserId + 
                             " newPermission=" + newPermission);

            // 10. Return success
            return Response.success("Permission updated successfully", Map.of(
                "userId", targetUserId,
                "permission", newPermission
            ));

        } catch (SQLException e) {
            System.err.println("[SHARE_UPDATE] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[SHARE_UPDATE] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Update share failed");
        }
    }
}
