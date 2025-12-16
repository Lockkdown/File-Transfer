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

    @Override
    public String toString() {
        return "ClientContext{" +
               "address=" + getClientAddress() +
               ", authenticated=" + isAuthenticated() +
               ", userId=" + userId +
               '}';
    }
}
