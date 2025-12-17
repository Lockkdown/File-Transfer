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
import com.drivelite.server.db.repository.FileRepository;
import com.drivelite.server.db.repository.FileVersionRepository;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.net.RequestHandler;

/**
 * Handler cho LIST_MY_FILES request.
 * Trả về danh sách files mà user sở hữu (OWNER).
 */
public class ListMyFilesHandler implements RequestHandler {

    private final FileRepository fileRepository;
    private final FileVersionRepository versionRepository;

    public ListMyFilesHandler() {
        this.fileRepository = new FileRepository();
        this.versionRepository = new FileVersionRepository();
    }

    @Override
    public Response handle(Request request, ClientContext context) {
        try {
            // 1. Lấy userId từ context (đã được authenticate bởi AuthMiddleware)
            int userId = context.getUserId();

            // 2. Lấy danh sách files của user
            List<FileMetadata> files = fileRepository.findByOwner(userId);

            // 3. Build response data với thông tin version
            List<Map<String, Object>> fileList = new ArrayList<>();
            for (FileMetadata file : files) {
                Map<String, Object> fileData = new HashMap<>();
                fileData.put("fileId", file.getFileId());
                fileData.put("fileName", file.getOriginalName());
                fileData.put("currentVersion", file.getCurrentVersion());
                fileData.put("createdAt", file.getCreatedAt() != null ? file.getCreatedAt().toString() : null);
                fileData.put("permission", "OWNER");

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

            System.out.println("[LIST_MY_FILES] userId=" + userId + ", count=" + fileList.size());

            return Response.success("Files retrieved", Map.of(
                "files", fileList,
                "count", fileList.size()
            ));

        } catch (SQLException e) {
            System.err.println("[LIST_MY_FILES] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[LIST_MY_FILES] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Failed to list files");
        }
    }
}
