package com.drivelite.server.handler;

import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.common.protocol.ResponseCode;
import com.drivelite.server.db.entity.FileMetadata;
import com.drivelite.server.db.entity.FileVersion;
import com.drivelite.server.db.repository.FilePermissionRepository;
import com.drivelite.server.db.repository.FileRepository;
import com.drivelite.server.db.repository.FileVersionRepository;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.service.StorageService;

/**
 * Handler cho DOWNLOAD_BEGIN request.
 * 
 * Flow:
 * 1. Client gửi DOWNLOAD_BEGIN { fileId, versionNumber? }
 * 2. Server validate (auth, permission, file exists)
 * 3. Server trả về FILE_META { fileName, fileSize, sha256, versionNumber }
 * 4. Client gửi READY
 * 5. Server stream raw bytes (exactly fileSize bytes)
 */
public class DownloadHandler {

    private static final int BUFFER_SIZE = 8192; // 8KB buffer

    private final FileRepository fileRepository;
    private final FileVersionRepository versionRepository;
    private final FilePermissionRepository permissionRepository;
    private final StorageService storageService;

    public DownloadHandler() {
        this.fileRepository = new FileRepository();
        this.versionRepository = new FileVersionRepository();
        this.permissionRepository = new FilePermissionRepository();
        this.storageService = StorageService.getInstance();
    }

    /**
     * Xử lý DOWNLOAD_BEGIN request.
     * Trả về FILE_META response với thông tin file.
     * 
     * @return Response FILE_META nếu ok, hoặc error response
     */
    public Response handleBegin(Request request, ClientContext context) {
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

            Object fileIdObj = data.get("fileId");
            if (fileIdObj == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "fileId is required");
            }

            int fileId = ((Number) fileIdObj).intValue();
            
            // Optional: versionNumber (default = current version)
            Integer versionNumber = null;
            Object versionObj = data.get("versionNumber");
            if (versionObj != null) {
                versionNumber = ((Number) versionObj).intValue();
            }

            // 3. Kiểm tra file tồn tại
            Optional<FileMetadata> fileOpt = fileRepository.findById(fileId);
            if (fileOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, "File not found");
            }

            FileMetadata file = fileOpt.get();

            // 4. Kiểm tra quyền (VIEW, EDIT, hoặc OWNER)
            if (!permissionRepository.hasMinimumPermission(fileId, userId, "VIEW")) {
                System.out.println("[DOWNLOAD] Forbidden: userId=" + userId + 
                                 " has no permission on fileId=" + fileId);
                return Response.error(ResponseCode.FORBIDDEN, "No permission to download this file");
            }

            // 5. Lấy version info
            if (versionNumber == null) {
                versionNumber = file.getCurrentVersion();
            }

            Optional<FileVersion> versionOpt = versionRepository.findByFileIdAndVersion(fileId, versionNumber);
            if (versionOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, "Version not found");
            }

            FileVersion version = versionOpt.get();

            // 6. Store download context
            context.setDownloadContext(fileId, versionNumber, version.getSizeBytes(), version.getSha256());

            System.out.println("[DOWNLOAD] BEGIN from userId=" + userId + 
                             ", fileId=" + fileId + ", version=" + versionNumber +
                             ", size=" + version.getSizeBytes());

            // 7. Return FILE_META response
            return Response.success("FILE_META", Map.of(
                "fileName", file.getOriginalName(),
                "fileSize", version.getSizeBytes(),
                "sha256", version.getSha256(),
                "versionNumber", versionNumber
            ));

        } catch (SQLException e) {
            System.err.println("[DOWNLOAD] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[DOWNLOAD] Error in handleBegin: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Download initialization failed");
        }
    }

    /**
     * Stream file bytes sau khi client gửi READY.
     * Được gọi từ RequestDispatcher sau khi nhận READY message.
     * 
     * @param outputStream OutputStream để gửi file bytes
     * @param context ClientContext chứa download info
     * @return true nếu thành công
     */
    public boolean streamFileBytes(OutputStream outputStream, ClientContext context) {
        int fileId = context.getDownloadFileId();
        int versionNumber = context.getDownloadVersionNumber();
        long fileSize = context.getDownloadFileSize();

        try {
            System.out.println("[DOWNLOAD] Streaming " + fileSize + " bytes for fileId=" + fileId);

            // Đọc file từ disk và stream ra client
            try (InputStream fileStream = storageService.readFile(fileId, versionNumber)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long totalSent = 0;
                int bytesRead;

                while ((bytesRead = fileStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;
                }

                outputStream.flush();

                System.out.println("[DOWNLOAD] SUCCESS fileId=" + fileId + 
                                 ", sent=" + totalSent + " bytes");
            }

            // Clear download context
            context.clearDownloadContext();

            return true;

        } catch (Exception e) {
            System.err.println("[DOWNLOAD] Error streaming file: " + e.getMessage());
            context.clearDownloadContext();
            return false;
        }
    }
}
