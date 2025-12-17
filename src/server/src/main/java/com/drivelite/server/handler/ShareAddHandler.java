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
 * Handler cho SHARE_ADD request.
 * Cho phép OWNER share file với user khác.
 * 
 * Request: { fileId, targetEmail, permission }
 * - permission: "VIEW" hoặc "EDIT" (không thể share OWNER)
 * 
 * Response: { ok, code, message, data: { userId, email, permission } }
 */
public class ShareAddHandler implements RequestHandler {

    private final FileRepository fileRepository;
    private final FilePermissionRepository permissionRepository;
    private final UserRepository userRepository;

    public ShareAddHandler() {
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

            // Validate targetEmail
            String targetEmail = (String) data.get("targetEmail");
            if (targetEmail == null || targetEmail.isBlank()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "targetEmail is required");
            }
            targetEmail = targetEmail.trim().toLowerCase();

            // Validate permission
            String permission = (String) data.get("permission");
            if (permission == null || permission.isBlank()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "permission is required");
            }
            permission = permission.toUpperCase();

            // Chỉ cho phép VIEW hoặc EDIT
            if (!permission.equals("VIEW") && !permission.equals("EDIT")) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "permission must be VIEW or EDIT");
            }

            // 3. Kiểm tra file tồn tại
            Optional<FileMetadata> fileOpt = fileRepository.findById(fileId);
            if (fileOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, "File not found");
            }

            // 4. Kiểm tra quyền OWNER
            if (!permissionRepository.isOwner(fileId, currentUserId)) {
                System.out.println("[SHARE_ADD] Forbidden: userId=" + currentUserId + 
                                 " is not OWNER of fileId=" + fileId);
                return Response.error(ResponseCode.FORBIDDEN, 
                    "Only file owner can share");
            }

            // 5. Tìm target user
            Optional<User> targetUserOpt = userRepository.findByEmail(targetEmail);
            if (targetUserOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, 
                    "User not found: " + targetEmail);
            }

            User targetUser = targetUserOpt.get();
            int targetUserId = targetUser.getUserId();

            // 6. Không thể share với chính mình
            if (targetUserId == currentUserId) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "Cannot share with yourself");
            }

            // 7. Kiểm tra đã có permission chưa
            if (permissionRepository.exists(fileId, targetUserId)) {
                return Response.error(ResponseCode.CONFLICT, 
                    "User already has permission. Use SHARE_UPDATE to change.");
            }

            // 8. Thêm permission
            boolean added = permissionRepository.addPermission(fileId, targetUserId, permission, currentUserId);
            if (!added) {
                return Response.error(ResponseCode.SERVER_ERROR, "Failed to add permission");
            }

            System.out.println("[SHARE_ADD] SUCCESS: userId=" + currentUserId + 
                             " shared fileId=" + fileId + 
                             " with userId=" + targetUserId + 
                             " permission=" + permission);

            // 9. Return success
            return Response.success("Share added successfully", Map.of(
                "userId", targetUserId,
                "email", targetEmail,
                "displayName", targetUser.getDisplayName(),
                "permission", permission
            ));

        } catch (SQLException e) {
            System.err.println("[SHARE_ADD] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[SHARE_ADD] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Share failed");
        }
    }
}
