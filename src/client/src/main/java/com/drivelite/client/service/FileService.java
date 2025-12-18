package com.drivelite.client.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.drivelite.client.model.FileItem;
import com.drivelite.client.model.ShareInfo;
import com.drivelite.client.model.VersionInfo;
import com.drivelite.client.net.TcpClient;
import com.drivelite.common.protocol.MessageType;
import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;

/**
 * Service xử lý file operations: List, Upload, Download, Share, Versions.
 */
public class FileService {

    private final TcpClient client;

    public FileService(TcpClient client) {
        this.client = client;
    }

    /**
     * Lấy danh sách files của user.
     */
    public List<FileItem> listMyFiles() throws IOException, ServiceException {
        Request request = new Request(
            MessageType.LIST_MY_FILES,
            client.getSessionToken(),
            generateRequestId(),
            null
        );
        
        Response response = client.sendRequest(request);
        
        if (!response.isOk()) {
            throw new ServiceException(response.getMessage());
        }
        
        return parseFileList(response.getData());
    }

    /**
     * Lấy danh sách files được share cho user.
     */
    public List<FileItem> listSharedWithMe() throws IOException, ServiceException {
        Request request = new Request(
            MessageType.LIST_SHARED_WITH_ME,
            client.getSessionToken(),
            generateRequestId(),
            null
        );
        
        Response response = client.sendRequest(request);
        
        if (!response.isOk()) {
            throw new ServiceException(response.getMessage());
        }
        
        return parseFileList(response.getData());
    }

    /**
     * Upload file mới.
     */
    public FileItem uploadFile(File file, ProgressCallback callback) throws IOException, ServiceException {
        // Calculate SHA256
        String sha256 = calculateSha256(file);
        
        // Send UPLOAD_BEGIN
        Request beginRequest = new Request(
            MessageType.UPLOAD_BEGIN,
            client.getSessionToken(),
            generateRequestId(),
            Map.of(
                "fileName", file.getName(),
                "fileSize", file.length(),
                "sha256", sha256
            )
        );
        
        Response readyResponse = client.sendRequest(beginRequest);
        
        if (!readyResponse.isOk()) {
            throw new ServiceException(readyResponse.getMessage());
        }
        
        // Send file bytes
        long totalSent = 0;
        long fileSize = file.length();
        byte[] buffer = new byte[64 * 1024]; // 64KB buffer
        
        try (FileInputStream fis = new FileInputStream(file)) {
            int read;
            while ((read = fis.read(buffer)) > 0) {
                client.sendRawBytes(buffer, 0, read);
                totalSent += read;
                if (callback != null) {
                    callback.onProgress(totalSent, fileSize);
                }
            }
        }
        
        // Read final response
        Response uploadResponse = readResponse();
        
        if (!uploadResponse.isOk()) {
            throw new ServiceException(uploadResponse.getMessage());
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) uploadResponse.getData();
        
        FileItem result = new FileItem();
        result.setFileId(((Number) data.get("fileId")).intValue());
        result.setFileName((String) data.get("fileName"));
        result.setFileSize(((Number) data.get("sizeBytes")).longValue());
        return result;
    }

    /**
     * Download file.
     */
    public void downloadFile(int fileId, File destination, ProgressCallback callback) throws IOException, ServiceException {
        downloadFile(fileId, null, destination, callback);
    }

    /**
     * Download file với version cụ thể.
     */
    public void downloadFile(int fileId, Integer versionNumber, File destination, ProgressCallback callback) throws IOException, ServiceException {
        // Send DOWNLOAD_BEGIN
        Map<String, Object> requestData = versionNumber != null
            ? Map.of("fileId", fileId, "versionNumber", versionNumber)
            : Map.of("fileId", fileId);
            
        Request beginRequest = new Request(
            MessageType.DOWNLOAD_BEGIN,
            client.getSessionToken(),
            generateRequestId(),
            requestData
        );
        
        Response metaResponse = client.sendRequest(beginRequest);
        
        if (!metaResponse.isOk()) {
            throw new ServiceException(metaResponse.getMessage());
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) metaResponse.getData();
        long fileSize = ((Number) meta.get("fileSize")).longValue();
        String expectedSha256 = (String) meta.get("sha256");
        
        // Send READY signal (không đọc response vì server sẽ gửi file bytes trực tiếp)
        Request readyRequest = new Request(
            MessageType.READY,
            client.getSessionToken(),
            generateRequestId(),
            null
        );
        client.sendRequestOnly(readyRequest);
        
        // Receive file bytes
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 not available", e);
        }
        
        long totalReceived = 0;
        byte[] buffer = new byte[64 * 1024];
        
        // Phải đọc hết file bytes từ socket, kể cả khi có lỗi ghi file
        // Nếu không đọc hết, connection sẽ bị "desync"
        FileOutputStream fos = null;
        IOException writeError = null;
        
        try {
            fos = new FileOutputStream(destination);
        } catch (IOException e) {
            writeError = e;
            // Vẫn phải đọc hết bytes từ socket để giữ connection sync
        }
        
        try {
            while (totalReceived < fileSize) {
                int toRead = (int) Math.min(buffer.length, fileSize - totalReceived);
                int read = client.readRawBytes(buffer, 0, toRead);
                if (read < 0) {
                    throw new IOException("Connection closed during download");
                }
                if (fos != null) {
                    fos.write(buffer, 0, read);
                }
                digest.update(buffer, 0, read);
                totalReceived += read;
                if (callback != null) {
                    callback.onProgress(totalReceived, fileSize);
                }
            }
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {}
            }
        }
        
        // Nếu có lỗi ghi file, throw exception sau khi đã đọc hết bytes
        if (writeError != null) {
            destination.delete();
            throw new ServiceException(destination.getAbsolutePath() + " (Access is denied)");
        }
        
        // Verify SHA256
        String actualSha256 = bytesToHex(digest.digest());
        if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
            destination.delete();
            throw new ServiceException("SHA256 mismatch! File corrupted.");
        }
    }

    /**
     * Xóa file.
     */
    public void deleteFile(int fileId) throws IOException, ServiceException {
        Request request = new Request(
            MessageType.DELETE_FILE,
            client.getSessionToken(),
            generateRequestId(),
            Map.of("fileId", fileId)
        );
        
        Response response = client.sendRequest(request);
        
        if (!response.isOk()) {
            throw new ServiceException(response.getMessage());
        }
    }

    /**
     * Đổi tên file.
     */
    public void renameFile(int fileId, String newName) throws IOException, ServiceException {
        Request request = new Request(
            MessageType.RENAME_FILE,
            client.getSessionToken(),
            generateRequestId(),
            Map.of("fileId", fileId, "newName", newName)
        );
        
        Response response = client.sendRequest(request);
        
        if (!response.isOk()) {
            throw new ServiceException(response.getMessage());
        }
    }

    /**
     * Share file.
     */
    public void shareFile(int fileId, String targetEmail, String permission) throws IOException, ServiceException {
        Request request = new Request(
            MessageType.SHARE_ADD,
            client.getSessionToken(),
            generateRequestId(),
            Map.of("fileId", fileId, "targetEmail", targetEmail, "permission", permission)
        );
        
        Response response = client.sendRequest(request);
        
        if (!response.isOk()) {
            throw new ServiceException(response.getMessage());
        }
    }

    /**
     * Lấy danh sách người được share file.
     */
    public List<ShareInfo> listShares(int fileId) throws IOException, ServiceException {
        Request request = new Request(
            MessageType.LIST_SHARES_OF_FILE,
            client.getSessionToken(),
            generateRequestId(),
            Map.of("fileId", fileId)
        );
        
        Response response = client.sendRequest(request);
        
        if (!response.isOk()) {
            throw new ServiceException(response.getMessage());
        }
        
        return parseShareList(response.getData());
    }

    /**
     * Lấy danh sách versions của file.
     */
    public List<VersionInfo> getVersions(int fileId) throws IOException, ServiceException {
        Request request = new Request(
            MessageType.GET_VERSIONS,
            client.getSessionToken(),
            generateRequestId(),
            Map.of("fileId", fileId)
        );
        
        Response response = client.sendRequest(request);
        
        if (!response.isOk()) {
            throw new ServiceException(response.getMessage());
        }
        
        return parseVersionList(response.getData());
    }

    // ========== Helper Methods ==========

    private Response readResponse() throws IOException {
        // Read length prefix
        byte[] lengthBytes = new byte[4];
        int totalRead = 0;
        while (totalRead < 4) {
            int read = client.readRawBytes(lengthBytes, totalRead, 4 - totalRead);
            if (read < 0) throw new IOException("Connection closed");
            totalRead += read;
        }
        
        int length = ((lengthBytes[0] & 0xFF) << 24) |
                     ((lengthBytes[1] & 0xFF) << 16) |
                     ((lengthBytes[2] & 0xFF) << 8) |
                     (lengthBytes[3] & 0xFF);
        
        // Read payload
        byte[] payload = new byte[length];
        totalRead = 0;
        while (totalRead < length) {
            int read = client.readRawBytes(payload, totalRead, length - totalRead);
            if (read < 0) throw new IOException("Connection closed");
            totalRead += read;
        }
        
        try {
            return Response.fromJson(new String(payload, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    private String calculateSha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("Failed to calculate SHA256", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<FileItem> parseFileList(Object data) {
        List<FileItem> result = new ArrayList<>();
        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            List<Map<String, Object>> files = (List<Map<String, Object>>) map.get("files");
            if (files != null) {
                for (Map<String, Object> f : files) {
                    FileItem item = new FileItem();
                    item.setFileId(((Number) f.get("fileId")).intValue());
                    item.setFileName((String) f.get("fileName"));
                    // Server trả về sizeBytes, có thể null nếu chưa có version
                    Number size = (Number) f.get("sizeBytes");
                    item.setFileSize(size != null ? size.longValue() : 0);
                    item.setOwnerEmail((String) f.get("ownerEmail"));
                    item.setPermission((String) f.get("permission"));
                    result.add(item);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<ShareInfo> parseShareList(Object data) {
        List<ShareInfo> result = new ArrayList<>();
        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            List<Map<String, Object>> shares = (List<Map<String, Object>>) map.get("shares");
            if (shares != null) {
                for (Map<String, Object> s : shares) {
                    ShareInfo info = new ShareInfo();
                    info.setUserId(((Number) s.get("userId")).intValue());
                    info.setEmail((String) s.get("email"));
                    info.setDisplayName((String) s.get("displayName"));
                    info.setPermission((String) s.get("permission"));
                    result.add(info);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<VersionInfo> parseVersionList(Object data) {
        List<VersionInfo> result = new ArrayList<>();
        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            List<Map<String, Object>> versions = (List<Map<String, Object>>) map.get("versions");
            if (versions != null) {
                for (Map<String, Object> v : versions) {
                    VersionInfo info = new VersionInfo();
                    info.setVersionNumber(((Number) v.get("versionNumber")).intValue());
                    // Server trả về sizeBytes, có thể null
                    Number size = (Number) v.get("sizeBytes");
                    info.setFileSize(size != null ? size.longValue() : 0);
                    info.setSha256((String) v.get("sha256"));
                    info.setUploaderEmail((String) v.get("uploaderEmail"));
                    info.setNote((String) v.get("note"));
                    result.add(info);
                }
            }
        }
        return result;
    }

    /**
     * Upload phiên bản mới của file đã tồn tại.
     * Yêu cầu quyền EDIT hoặc OWNER.
     */
    public void uploadNewVersion(int fileId, File file, String note, ProgressCallback callback) throws IOException, ServiceException {
        // Calculate SHA256
        String sha256 = calculateSha256(file);
        
        // Send UPLOAD_NEW_VERSION_BEGIN
        Map<String, Object> requestData = new java.util.HashMap<>();
        requestData.put("fileId", fileId);
        requestData.put("fileSize", file.length());
        requestData.put("sha256", sha256);
        if (note != null && !note.isEmpty()) {
            requestData.put("note", note);
        }
        
        Request beginRequest = new Request(
            MessageType.UPLOAD_NEW_VERSION_BEGIN,
            client.getSessionToken(),
            generateRequestId(),
            requestData
        );
        
        Response readyResponse = client.sendRequest(beginRequest);
        
        if (!readyResponse.isOk()) {
            throw new ServiceException(readyResponse.getMessage());
        }
        
        // Send file bytes
        long totalSent = 0;
        long fileSize = file.length();
        byte[] buffer = new byte[64 * 1024]; // 64KB buffer
        
        try (FileInputStream fis = new FileInputStream(file)) {
            int read;
            while ((read = fis.read(buffer)) > 0) {
                client.sendRawBytes(buffer, 0, read);
                totalSent += read;
                if (callback != null) {
                    callback.onProgress(totalSent, fileSize);
                }
            }
        }
        
        // Read final response
        Response uploadResponse = readResponse();
        
        if (!uploadResponse.isOk()) {
            throw new ServiceException(uploadResponse.getMessage());
        }
    }

    private String generateRequestId() {
        return "req-" + System.currentTimeMillis();
    }

    /**
     * Callback interface cho progress updates.
     */
    public interface ProgressCallback {
        void onProgress(long current, long total);
    }
}
