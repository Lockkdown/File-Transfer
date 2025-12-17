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
import com.drivelite.server.db.repository.FilePermissionRepository;
import com.drivelite.server.db.repository.FileRepository;
import com.drivelite.server.db.repository.FileVersionRepository;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.net.RequestHandler;
import com.drivelite.server.service.StorageService;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Handler cho UPLOAD_NEW_VERSION_BEGIN request.
 * Cho phép user có quyền EDIT hoặc OWNER upload version mới của file đã tồn tại.
 * 
 * Flow:
 * 1. Client gửi UPLOAD_NEW_VERSION_BEGIN { fileId, fileSize, sha256, note? }
 * 2. Server validate (auth, permission EDIT/OWNER, file exists, size limit)
 * 3. Server trả về READY { versionNumber }
 * 4. Client stream raw bytes (exactly fileSize bytes)
 * 5. Server lưu file, verify SHA256
 * 6. Server trả về UPLOAD_OK { fileId, versionNumber }
 */
public class UploadNewVersionHandler implements RequestHandler {

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

    public UploadNewVersionHandler() {
        this.fileRepository = new FileRepository();
        this.versionRepository = new FileVersionRepository();
        this.permissionRepository = new FilePermissionRepository();
        this.storageService = StorageService.getInstance();
    }

    @Override
    public Response handle(Request request, ClientContext context) {
        return handleBegin(request, context);
    }

    /**
     * Xử lý UPLOAD_NEW_VERSION_BEGIN request.
     * Trả về READY response với versionNumber mới.
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

            // Validate fileId
            Object fileIdObj = data.get("fileId");
            if (fileIdObj == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "fileId is required");
            }
            int fileId = ((Number) fileIdObj).intValue();

            // Validate fileSize
            Object fileSizeObj = data.get("fileSize");
            if (fileSizeObj == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "fileSize is required");
            }
            long fileSize = ((Number) fileSizeObj).longValue();

            // Validate sha256
            String sha256 = (String) data.get("sha256");
            if (sha256 == null || sha256.trim().isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "sha256 is required");
            }

            // Optional: note
            String note = (String) data.get("note");

            // 3. Validate file size
            if (fileSize <= 0) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "fileSize must be positive");
            }
            if (fileSize > MAX_FILE_SIZE) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "File too large. Maximum size is " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
            }

            // 4. Validate SHA256 format (64 hex chars)
            if (!sha256.matches("^[a-fA-F0-9]{64}$")) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Invalid sha256 format");
            }

            // 5. Kiểm tra file tồn tại
            Optional<FileMetadata> fileOpt = fileRepository.findById(fileId);
            if (fileOpt.isEmpty()) {
                return Response.error(ResponseCode.NOT_FOUND, "File not found");
            }

            FileMetadata file = fileOpt.get();

            // 6. Kiểm tra quyền EDIT hoặc OWNER
            if (!permissionRepository.hasMinimumPermission(fileId, userId, "EDIT")) {
                System.out.println("[UPLOAD_NEW_VERSION] Forbidden: userId=" + userId + 
                                 " has no EDIT permission on fileId=" + fileId);
                return Response.error(ResponseCode.FORBIDDEN, 
                    "EDIT or OWNER permission required to upload new version");
            }

            // 7. Tính version number mới
            int newVersionNumber = file.getCurrentVersion() + 1;

            // 8. Store upload context
            context.setUploadNewVersionContext(fileId, newVersionNumber, fileSize, sha256.toLowerCase(), note);

            System.out.println("[UPLOAD_NEW_VERSION] BEGIN from userId=" + userId + 
                             ", fileId=" + fileId + ", newVersion=" + newVersionNumber + 
                             ", size=" + fileSize);

            // 9. Return READY response
            return Response.success("READY", Map.of(
                "status", "READY",
                "message", "Ready to receive file bytes",
                "versionNumber", newVersionNumber
            ));

        } catch (SQLException e) {
            System.err.println("[UPLOAD_NEW_VERSION] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[UPLOAD_NEW_VERSION] Error in handleBegin: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Upload initialization failed");
        }
    }

    /**
     * Xử lý file bytes sau khi client nhận READY.
     */
    public boolean handleFileBytes(InputStream inputStream, OutputStream outputStream, ClientContext context) {
        int fileId = context.getUploadNewVersionFileId();
        int versionNumber = context.getUploadNewVersionNumber();
        long fileSize = context.getUploadNewVersionFileSize();
        String expectedSha256 = context.getUploadNewVersionSha256();
        String note = context.getUploadNewVersionNote();
        int userId = context.getUserId();

        try {
            System.out.println("[UPLOAD_NEW_VERSION] Receiving " + fileSize + " bytes for fileId=" + fileId + 
                             ", version=" + versionNumber);

            // 1. Lưu file vào disk và tính SHA256
            String actualSha256 = storageService.saveFile(fileId, versionNumber, inputStream, fileSize);

            // 2. Verify SHA256
            if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                storageService.deleteFile(fileId, versionNumber);
                System.err.println("[UPLOAD_NEW_VERSION] SHA256 mismatch for fileId=" + fileId);
                
                Response errorResponse = Response.error(ResponseCode.VALIDATION_ERROR, 
                    "SHA256 mismatch. Expected: " + expectedSha256 + ", Got: " + actualSha256);
                sendResponse(outputStream, errorResponse);
                return false;
            }

            // 3. Tạo version record
            String storedPath = storageService.getStoredPath(fileId, versionNumber);
            versionRepository.create(fileId, versionNumber, storedPath, fileSize, actualSha256, userId, note);

            // 4. Cập nhật current version trong Files table
            fileRepository.updateCurrentVersion(fileId, versionNumber);

            // 5. Lấy tên file gốc
            Optional<FileMetadata> fileOpt = fileRepository.findById(fileId);
            String fileName = fileOpt.map(FileMetadata::getOriginalName).orElse("unknown");

            // 6. Gửi success response
            Response successResponse = Response.success("Upload successful", Map.of(
                "fileId", fileId,
                "versionNumber", versionNumber,
                "fileName", fileName,
                "sizeBytes", fileSize,
                "sha256", actualSha256
            ));
            sendResponse(outputStream, successResponse);

            System.out.println("[UPLOAD_NEW_VERSION] SUCCESS fileId=" + fileId + 
                             ", version=" + versionNumber + ", sha256=" + actualSha256);

            // 7. Clear upload context
            context.clearUploadNewVersionContext();

            return true;

        } catch (SQLException e) {
            System.err.println("[UPLOAD_NEW_VERSION] Database error: " + e.getMessage());
            try {
                Response errorResponse = Response.error(ResponseCode.SERVER_ERROR, "Database error");
                sendResponse(outputStream, errorResponse);
            } catch (Exception ex) {
                System.err.println("[UPLOAD_NEW_VERSION] Failed to send error response: " + ex.getMessage());
            }
            return false;
        } catch (Exception e) {
            System.err.println("[UPLOAD_NEW_VERSION] Error: " + e.getMessage());
            try {
                Response errorResponse = Response.error(ResponseCode.SERVER_ERROR, "Upload failed: " + e.getMessage());
                sendResponse(outputStream, errorResponse);
            } catch (Exception ex) {
                System.err.println("[UPLOAD_NEW_VERSION] Failed to send error response: " + ex.getMessage());
            }
            return false;
        }
    }

    private void sendResponse(OutputStream out, Response response) throws Exception {
        String json = response.toJson();
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        out.write((bytes.length >> 24) & 0xFF);
        out.write((bytes.length >> 16) & 0xFF);
        out.write((bytes.length >> 8) & 0xFF);
        out.write(bytes.length & 0xFF);
        
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
