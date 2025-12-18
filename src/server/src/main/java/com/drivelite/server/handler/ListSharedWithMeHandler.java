package com.drivelite.server.handler;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Handler cho LIST_SHARED_WITH_ME request.
 * Trả về danh sách files được share cho user (VIEW hoặc EDIT, không phải OWNER).
 */
public class ListSharedWithMeHandler implements RequestHandler {

    private final FileRepository fileRepository;
    private final FileVersionRepository versionRepository;
    private final FilePermissionRepository permissionRepository;
    private final UserRepository userRepository;

    public ListSharedWithMeHandler() {
        this.fileRepository = new FileRepository();
        this.versionRepository = new FileVersionRepository();
        this.permissionRepository = new FilePermissionRepository();
        this.userRepository = new UserRepository();
    }

    @Override
    public Response handle(Request request, ClientContext context) {
        try {
            // 1. Lấy userId từ context
            int userId = context.getUserId();

            // 2. Lấy danh sách files được share cho user
            List<Map<String, Object>> sharedFiles = permissionRepository.findSharedFilesForUser(userId);

            // 3. Build response với thông tin chi tiết
            List<Map<String, Object>> fileList = new ArrayList<>();
            for (Map<String, Object> shared : sharedFiles) {
                int fileId = (int) shared.get("fileId");
                String permission = (String) shared.get("permission");

                // Lấy thông tin file
                FileMetadata file = fileRepository.findById(fileId).orElse(null);
                if (file == null) continue; // File đã bị xóa

                Map<String, Object> fileData = new HashMap<>();
                fileData.put("fileId", file.getFileId());
                fileData.put("fileName", file.getOriginalName());
                fileData.put("currentVersion", file.getCurrentVersion());
                fileData.put("createdAt", file.getCreatedAt() != null ? file.getCreatedAt().toString() : null);
                fileData.put("permission", permission);
                
                // Lấy email của owner
                User owner = userRepository.findById(file.getOwnerUserId()).orElse(null);
                fileData.put("ownerEmail", owner != null ? owner.getEmail() : "Unknown");

                // Lấy thông tin version hiện tại
                FileVersion currentVersion = versionRepository
                    .findByFileIdAndVersion(file.getFileId(), file.getCurrentVersion())
                    .orElse(null);
                
                if (currentVersion != null) {
                    fileData.put("sizeBytes", currentVersion.getSizeBytes());
                    fileData.put("sha256", currentVersion.getSha256());
                    fileData.put("uploadedAt", currentVersion.getUploadedAt() != null ? 
                                              currentVersion.getUploadedAt().toString() : null);
                }

                fileList.add(fileData);
            }

            System.out.println("[LIST_SHARED_WITH_ME] userId=" + userId + ", count=" + fileList.size());

            return Response.success("Shared files retrieved", Map.of(
                "files", fileList,
                "count", fileList.size()
            ));

        } catch (SQLException e) {
            System.err.println("[LIST_SHARED_WITH_ME] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[LIST_SHARED_WITH_ME] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Failed to list shared files");
        }
    }
}
