package com.drivelite.client.service;

import java.io.IOException;
import java.util.Map;

import com.drivelite.client.net.TcpClient;
import com.drivelite.common.protocol.MessageType;
import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;

/**
 * Service xử lý authentication: Login, Register, ForgotPassword, ResetPassword.
 */
public class AuthService {

    private final TcpClient client;

    public AuthService(TcpClient client) {
        this.client = client;
    }

    /**
     * Đăng nhập.
     * @return SessionToken nếu thành công
     */
    public String login(String email, String password) throws IOException, ServiceException {
        Request request = new Request(
            MessageType.LOGIN,
            null,
            generateRequestId(),
            Map.of("email", email, "password", password)
        );
        
        Response response = client.sendRequest(request);
        
        if (!response.isOk()) {
            throw new ServiceException(response.getMessage());
        }
        
        Object rawData = response.getData();
        if (rawData == null) {
            throw new ServiceException("Server returned null data");
        }
        
        // Debug: print data type
        System.out.println("[AUTH] Response data type: " + rawData.getClass().getName());
        System.out.println("[AUTH] Response data: " + rawData);
        
        if (!(rawData instanceof Map)) {
            throw new ServiceException("Unexpected response format: " + rawData.getClass().getName());
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) rawData;
        String sessionToken = (String) data.get("token");
        
        // Lưu session token vào client
        client.setSessionToken(sessionToken);
        
        return sessionToken;
    }

    /**
     * Đăng ký tài khoản mới.
     */
    public void register(String email, String password, String displayName) throws IOException, ServiceException {
        Request request = new Request(
            MessageType.REGISTER,
            null,
            generateRequestId(),
            Map.of("email", email, "password", password, "displayName", displayName)
        );
        
        Response response = client.sendRequest(request);
        
        if (!response.isOk()) {
            throw new ServiceException(response.getMessage());
        }
    }

    /**
     * Gửi OTP quên mật khẩu.
     */
    public void forgotPassword(String email) throws IOException, ServiceException {
        Request request = new Request(
            MessageType.FORGOT_PASSWORD,
            null,
            generateRequestId(),
            Map.of("email", email)
        );
        
        Response response = client.sendRequest(request);
        
        if (!response.isOk()) {
            throw new ServiceException(response.getMessage());
        }
    }

    /**
     * Reset mật khẩu với OTP.
     */
    public void resetPassword(String email, String otp, String newPassword) throws IOException, ServiceException {
        Request request = new Request(
            MessageType.RESET_PASSWORD,
            null,
            generateRequestId(),
            Map.of("token", otp, "newPassword", newPassword)
        );
        
        Response response = client.sendRequest(request);
        
        if (!response.isOk()) {
            throw new ServiceException(response.getMessage());
        }
    }

    /**
     * Đăng xuất (clear session).
     */
    public void logout() {
        client.clearSession();
    }

    private String generateRequestId() {
        return "req-" + System.currentTimeMillis();
    }
}
