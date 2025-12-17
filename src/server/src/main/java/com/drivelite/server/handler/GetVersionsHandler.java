package com.drivelite.server.handler;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.common.protocol.ResponseCode;
import com.drivelite.server.db.entity.FileMetadata;
import com.drivelite.server.db.entity.FileVersion;
import com.drivelite.server.db.entity.User;
import com.drivelite.server.db.repository.FilePermissionRepository;
import com.drivelite.server.db.repository.FileRepository;
import com.drivelite.server.db.repository.FileVersionRepository;
import com.drivelite.server.db.repository.UserRepository;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.net.RequestHandler;

/**
 * Handler cho GET_VERSIONS request.
 * Trả về danh sách tất cả versions của một file.
 * 
 * Request: { fileId }
 * Response: { ok, data: { versions: [...] } }
 * 
 * Yêu cầu: User phải có quyền VIEW, EDIT, hoặc OWNER trên file.
 */
public class GetVersionsHandler implements RequestHandler {

    private final FileRepository fileRepository;
    private final FileVersionRepository versionRepository;
    private final FilePermissionRepository permissionRepository;
    private final UserRepository userRepository;

    public GetVersionsHandler() {
        this.fileRepository = new FileRepository();
        this.versionRepository = new FileVersionRepository();
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

            int userId = context.getUserId();

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

            FileMetadata file = fileOpt.get();

            // 4. Kiểm tra quyền VIEW (minimum permission)
            if (!permissionRepository.hasMinimumPermission(fileId, userId, "VIEW")) {
                System.out.println("[GET_VERSIONS] Forbidden: userId=" + userId + 
                                 " has no permission on fileId=" + fileId);
                return Response.error(ResponseCode.FORBIDDEN, "No permission to view this file");
            }

            // 5. Lấy tất cả versions
            List<FileVersion> versions = versionRepository.findByFileId(fileId);

            // 6. Build response với thông tin uploader
            List<Map<String, Object>> versionList = new ArrayList<>();
            for (FileVersion v : versions) {
                Map<String, Object> versionInfo = new HashMap<>();
                versionInfo.put("versionNumber", v.getVersionNumber());
                versionInfo.put("sizeBytes", v.getSizeBytes());
                versionInfo.put("sha256", v.getSha256());
                versionInfo.put("uploadedBy", v.getUploadedBy());
                versionInfo.put("uploadedAt", v.getUploadedAt() != null ? v.getUploadedAt().toString() : null);
                versionInfo.put("note", v.getNote());
                
                // Lấy tên người upload
                Optional<User> uploaderOpt = userRepository.findById(v.getUploadedBy());
                if (uploaderOpt.isPresent()) {
                    versionInfo.put("uploaderName", uploaderOpt.get().getDisplayName());
                    versionInfo.put("uploaderEmail", uploaderOpt.get().getEmail());
                }
                
                versionList.add(versionInfo);
            }

            System.out.println("[GET_VERSIONS] SUCCESS: userId=" + userId + 
                             ", fileId=" + fileId + ", count=" + versions.size());

            // 7. Return success
            return Response.success("OK", Map.of(
                "fileId", fileId,
                "fileName", file.getOriginalName(),
                "currentVersion", file.getCurrentVersion(),
                "versions", versionList,
                "count", versions.size()
            ));

        } catch (SQLException e) {
            System.err.println("[GET_VERSIONS] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[GET_VERSIONS] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Get versions failed");
        }
    }
}
