package com.drivelite.server.handler;

import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Map;

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

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Handler cho UPLOAD_BEGIN request.
 * 
 * Flow:
 * 1. Client gửi UPLOAD_BEGIN { fileName, fileSize, sha256 }
 * 2. Server validate (auth, size limit, filename)
 * 3. Server trả về READY
 * 4. Client stream raw bytes (exactly fileSize bytes)
 * 5. Server lưu file, verify SHA256
 * 6. Server trả về UPLOAD_OK { fileId, versionNumber }
 */
public class UploadHandler {

    private static final long MAX_FILE_SIZE;
    
    static {
        Dotenv dotenv = Dotenv.configure()
                .directory(findEnvDirectory())
                .ignoreIfMissing()
                .load();
        MAX_FILE_SIZE = Long.parseLong(dotenv.get("MAX_FILE_SIZE", "524288000")); // 500MB default
    }

    private final FileRepository fileRepository;
    private final FileVersionRepository versionRepository;
    private final FilePermissionRepository permissionRepository;
    private final StorageService storageService;

    public UploadHandler() {
        this.fileRepository = new FileRepository();
        this.versionRepository = new FileVersionRepository();
        this.permissionRepository = new FilePermissionRepository();
        this.storageService = StorageService.getInstance();
    }

    /**
     * Xử lý UPLOAD_BEGIN request.
     * Trả về READY response và chờ client stream file bytes.
     * 
     * @return Response READY nếu ok, hoặc error response
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

            String fileName = (String) data.get("fileName");
            Object fileSizeObj = data.get("fileSize");
            String sha256 = (String) data.get("sha256");

            // 3. Validate input
            if (fileName == null || fileName.trim().isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "fileName is required");
            }
            if (fileSizeObj == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "fileSize is required");
            }
            if (sha256 == null || sha256.trim().isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "sha256 is required");
            }

            // Sanitize filename (remove path components)
            fileName = sanitizeFileName(fileName);
            if (fileName.isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Invalid fileName");
            }

            long fileSize = ((Number) fileSizeObj).longValue();

            // 4. Validate file size
            if (fileSize <= 0) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "fileSize must be positive");
            }
            if (fileSize > MAX_FILE_SIZE) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "File too large. Maximum size is " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
            }

            // 5. Validate SHA256 format (64 hex chars)
            if (!sha256.matches("^[a-fA-F0-9]{64}$")) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Invalid sha256 format");
            }

            // 6. Store upload context for later use
            context.setUploadContext(fileName, fileSize, sha256.toLowerCase());

            System.out.println("[UPLOAD] BEGIN from userId=" + userId + 
                             ", fileName=" + fileName + ", size=" + fileSize);

            // 7. Return READY response
            return Response.success("READY", Map.of(
                "status", "READY",
                "message", "Ready to receive file bytes"
            ));

        } catch (Exception e) {
            System.err.println("[UPLOAD] Error in handleBegin: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Upload initialization failed");
        }
    }

    /**
     * Xử lý file bytes sau khi client nhận READY.
     * Được gọi từ RequestDispatcher sau khi gửi READY response.
     * 
     * @param inputStream InputStream để đọc file bytes
     * @param outputStream OutputStream để gửi response
     * @param context ClientContext chứa upload info
     * @return true nếu thành công
     */
    public boolean handleFileBytes(InputStream inputStream, OutputStream outputStream, ClientContext context) {
        String fileName = context.getUploadFileName();
        long fileSize = context.getUploadFileSize();
        String expectedSha256 = context.getUploadSha256();
        int userId = context.getUserId();

        try {
            // 1. Tạo file record trong DB (để lấy fileId)
            FileMetadata file = fileRepository.create(userId, fileName);
            int fileId = file.getFileId();
            int versionNumber = 1;

            System.out.println("[UPLOAD] Receiving " + fileSize + " bytes for fileId=" + fileId);

            // 2. Lưu file vào disk và tính SHA256
            String actualSha256 = storageService.saveFile(fileId, versionNumber, inputStream, fileSize);

            // 3. Verify SHA256
            if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                // Hash mismatch - xóa file và rollback
                storageService.deleteFile(fileId, versionNumber);
                System.err.println("[UPLOAD] SHA256 mismatch for fileId=" + fileId);
                
                Response errorResponse = Response.error(ResponseCode.VALIDATION_ERROR, 
                    "SHA256 mismatch. Expected: " + expectedSha256 + ", Got: " + actualSha256);
                sendResponse(outputStream, errorResponse);
                return false;
            }

            // 4. Tạo version record
            String storedPath = storageService.getStoredPath(fileId, versionNumber);
            FileVersion version = versionRepository.create(
                fileId, versionNumber, storedPath, fileSize, actualSha256, userId, null);

            // 5. Thêm OWNER permission
            permissionRepository.addPermission(fileId, userId, "OWNER", userId);

            // 6. Gửi success response
            Response successResponse = Response.success("Upload successful", Map.of(
                "fileId", fileId,
                "versionNumber", versionNumber,
                "fileName", fileName,
                "sizeBytes", fileSize,
                "sha256", actualSha256
            ));
            sendResponse(outputStream, successResponse);

            System.out.println("[UPLOAD] SUCCESS fileId=" + fileId + ", version=" + versionNumber + 
                             ", sha256=" + actualSha256);

            // 7. Clear upload context
            context.clearUploadContext();

            return true;

        } catch (SQLException e) {
            System.err.println("[UPLOAD] Database error: " + e.getMessage());
            try {
                Response errorResponse = Response.error(ResponseCode.SERVER_ERROR, "Database error");
                sendResponse(outputStream, errorResponse);
            } catch (Exception ex) {
                System.err.println("[UPLOAD] Failed to send error response: " + ex.getMessage());
            }
            return false;
        } catch (Exception e) {
            System.err.println("[UPLOAD] Error: " + e.getMessage());
            try {
                Response errorResponse = Response.error(ResponseCode.SERVER_ERROR, "Upload failed: " + e.getMessage());
                sendResponse(outputStream, errorResponse);
            } catch (Exception ex) {
                System.err.println("[UPLOAD] Failed to send error response: " + ex.getMessage());
            }
            return false;
        }
    }

    /**
     * Sanitize filename để tránh path traversal.
     * Chỉ giữ tên file, loại bỏ path components.
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
        
        // Không cho phép bắt đầu bằng dấu chấm (hidden files)
        while (fileName.startsWith(".")) {
            fileName = fileName.substring(1);
        }
        
        // Giới hạn độ dài
        if (fileName.length() > 255) {
            fileName = fileName.substring(0, 255);
        }
        
        return fileName.trim();
    }

    private void sendResponse(OutputStream out, Response response) throws Exception {
        String json = response.toJson();
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        // Write length prefix (4 bytes, big-endian)
        out.write((bytes.length >> 24) & 0xFF);
        out.write((bytes.length >> 16) & 0xFF);
        out.write((bytes.length >> 8) & 0xFF);
        out.write(bytes.length & 0xFF);
        
        // Write payload
        out.write(bytes);
        out.flush();
    }

    private static String findEnvDirectory() {
        String[] possiblePaths = { ".", "..", "../..", "../../..", System.getProperty("user.dir") };
        for (String path : possiblePaths) {
            java.io.File envFile = new java.io.File(path, ".env");
            if (envFile.exists()) {
                return path;
            }
        }
        return ".";
    }
}
