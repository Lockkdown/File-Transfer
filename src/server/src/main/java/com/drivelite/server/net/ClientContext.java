package com.drivelite.server.net;

import java.net.Socket;

/**
 * ClientContext - Chứa thông tin về client đang kết nối.
 * 
 * Được truyền vào các handler để handler có thể:
 * - Biết client IP (cho logging/audit)
 * - Lưu session token sau login
 * - Lấy userId từ session
 */
public class ClientContext {

    private final Socket socket;
    private final String clientIp;
    private final int clientPort;
    
    // Session info (set sau khi login thành công)
    private String sessionToken;
    private Integer userId;
    private String userEmail;
    
    // Upload context (set khi bắt đầu upload)
    private String uploadFileName;
    private long uploadFileSize;
    private String uploadSha256;
    private boolean uploading;
    
    // Download context (set khi bắt đầu download)
    private int downloadFileId;
    private int downloadVersionNumber;
    private long downloadFileSize;
    private String downloadSha256;
    private boolean downloading;

    public ClientContext(Socket socket) {
        this.socket = socket;
        this.clientIp = socket.getInetAddress().getHostAddress();
        this.clientPort = socket.getPort();
    }

    // === Getters ===
    
    public Socket getSocket() {
        return socket;
    }

    public String getClientIp() {
        return clientIp;
    }

    public int getClientPort() {
        return clientPort;
    }

    public String getClientAddress() {
        return clientIp + ":" + clientPort;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    // === Setters (dùng sau login) ===

    public void setSession(String token, int userId, String email) {
        this.sessionToken = token;
        this.userId = userId;
        this.userEmail = email;
    }

    public void clearSession() {
        this.sessionToken = null;
        this.userId = null;
        this.userEmail = null;
    }

    /**
     * Kiểm tra client đã đăng nhập chưa.
     */
    public boolean isAuthenticated() {
        return sessionToken != null && userId != null;
    }

    // === Upload context methods ===
    
    public void setUploadContext(String fileName, long fileSize, String sha256) {
        this.uploadFileName = fileName;
        this.uploadFileSize = fileSize;
        this.uploadSha256 = sha256;
        this.uploading = true;
    }

    public void clearUploadContext() {
        this.uploadFileName = null;
        this.uploadFileSize = 0;
        this.uploadSha256 = null;
        this.uploading = false;
    }

    public boolean isUploading() {
        return uploading;
    }

    public String getUploadFileName() {
        return uploadFileName;
    }

    public long getUploadFileSize() {
        return uploadFileSize;
    }

    public String getUploadSha256() {
        return uploadSha256;
    }

    // === Download context methods ===
    
    public void setDownloadContext(int fileId, int versionNumber, long fileSize, String sha256) {
        this.downloadFileId = fileId;
        this.downloadVersionNumber = versionNumber;
        this.downloadFileSize = fileSize;
        this.downloadSha256 = sha256;
        this.downloading = true;
    }

    public void clearDownloadContext() {
        this.downloadFileId = 0;
        this.downloadVersionNumber = 0;
        this.downloadFileSize = 0;
        this.downloadSha256 = null;
        this.downloading = false;
    }

    public boolean isDownloading() {
        return downloading;
    }

    public int getDownloadFileId() {
        return downloadFileId;
    }

    public int getDownloadVersionNumber() {
        return downloadVersionNumber;
    }

    public long getDownloadFileSize() {
        return downloadFileSize;
    }

    public String getDownloadSha256() {
        return downloadSha256;
    }

    @Override
    public String toString() {
        return "ClientContext{" +
               "address=" + getClientAddress() +
               ", authenticated=" + isAuthenticated() +
               ", userId=" + userId +
               '}';
    }
}
