package com.drivelite.server.handler;

import java.sql.SQLException;
import java.util.List;
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
 * Handler cho LIST_SHARES_OF_FILE request.
 * Cho phép OWNER xem danh sách tất cả người được share file.
 * 
 * Request: { fileId }
 * 
 * Response: { ok, code, message, data: { shares: [...] } }
 * Mỗi share item: { userId, email, displayName, permission, grantedAt }
 */
public class ListSharesOfFileHandler implements RequestHandler {

    private final FileRepository fileRepository;
    private final FilePermissionRepository permissionRepository;

    public ListSharesOfFileHandler() {
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

            // 3. Kiểm tra file tồn tại
            Optional<FileMetadata> fileOpt = fileRepository.findById(fileId);
            if (fileOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, "File not found");
            }

            // 4. Kiểm tra quyền OWNER
            if (!permissionRepository.isOwner(fileId, currentUserId)) {
                System.out.println("[LIST_SHARES] Forbidden: userId=" + currentUserId + 
                                 " is not OWNER of fileId=" + fileId);
                return Response.error(ResponseCode.FORBIDDEN, 
                    "Only file owner can view shares");
            }

            // 5. Lấy danh sách shares
            List<Map<String, Object>> shares = permissionRepository.findAllByFileId(fileId);

            System.out.println("[LIST_SHARES] SUCCESS: userId=" + currentUserId + 
                             " listed shares for fileId=" + fileId + 
                             ", count=" + shares.size());

            // 6. Return success
            return Response.success("OK", Map.of(
                "shares", shares,
                "fileId", fileId,
                "fileName", fileOpt.get().getOriginalName()
            ));

        } catch (SQLException e) {
            System.err.println("[LIST_SHARES] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[LIST_SHARES] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "List shares failed");
        }
    }
}
