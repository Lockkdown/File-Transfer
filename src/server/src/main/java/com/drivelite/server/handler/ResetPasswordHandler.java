package com.drivelite.server.handler;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.common.protocol.ResponseCode;
import com.drivelite.server.db.entity.PasswordResetToken;
import com.drivelite.server.db.repository.PasswordResetTokenRepository;
import com.drivelite.server.db.repository.SessionRepository;
import com.drivelite.server.db.repository.UserRepository;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.net.RequestHandler;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Handler cho RESET_PASSWORD request.
 * 
 * Input: { token, newPassword }
 * Output: { message }
 * 
 * Flow:
 * 1. Validate input (token, newPassword)
 * 2. Tìm và validate reset token
 * 3. Hash password mới với BCrypt
 * 4. Cập nhật password trong database
 * 5. Đánh dấu token đã sử dụng
 * 6. Xóa tất cả session của user (force logout)
 * 7. Trả về success
 */
public class ResetPasswordHandler implements RequestHandler {

    private static final int BCRYPT_COST = 12;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;

    public ResetPasswordHandler() {
        this.tokenRepository = new PasswordResetTokenRepository();
        this.userRepository = new UserRepository();
        this.sessionRepository = new SessionRepository();
    }

    public ResetPasswordHandler(PasswordResetTokenRepository tokenRepository, 
                                 UserRepository userRepository,
                                 SessionRepository sessionRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Response handle(Request request, ClientContext context) {
        try {
            // 1. Lấy data từ request
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) request.getData();
            
            if (data == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Missing request data");
            }

            String token = (String) data.get("token");
            String newPassword = (String) data.get("newPassword");

            // 2. Validate token
            if (token == null || token.trim().isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Reset token is required");
            }

            // 3. Validate new password
            if (newPassword == null || newPassword.isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "New password is required");
            }
            if (newPassword.length() < MIN_PASSWORD_LENGTH) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            }
            if (newPassword.length() > MAX_PASSWORD_LENGTH) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "Password must be at most " + MAX_PASSWORD_LENGTH + " characters");
            }

            // 4. Tìm và validate reset token
            Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token.trim());
            if (tokenOpt.isEmpty()) {
                return Response.error(ResponseCode.UNAUTHORIZED, "Invalid or expired reset token");
            }

            PasswordResetToken resetToken = tokenOpt.get();

            // 5. Kiểm tra token còn hiệu lực không
            if (!resetToken.isValid()) {
                return Response.error(ResponseCode.UNAUTHORIZED, "Reset token has expired or already used");
            }

            // 6. Hash password mới với BCrypt
            String passwordHash = BCrypt.withDefaults().hashToString(BCRYPT_COST, newPassword.toCharArray());

            // 7. Cập nhật password trong database
            boolean updated = userRepository.updatePassword(resetToken.getUserId(), passwordHash);
            if (!updated) {
                return Response.error(ResponseCode.SERVER_ERROR, "Failed to update password");
            }

            // 8. Đánh dấu token đã sử dụng
            tokenRepository.markAsUsed(resetToken.getToken());

            // 9. Xóa tất cả session của user (force logout từ tất cả devices)
            int sessionsDeleted = sessionRepository.deleteAllByUserId(resetToken.getUserId());

            // 10. Trả về response
            System.out.println("[RESET_PASSWORD] Success for userId: " + resetToken.getUserId() + 
                             " (deleted " + sessionsDeleted + " sessions)");

            return Response.success("Password reset successful", Map.of(
                "message", "Your password has been reset. Please login with your new password."
            ));

        } catch (SQLException e) {
            System.err.println("[RESET_PASSWORD] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[RESET_PASSWORD] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Internal error");
        }
    }
}
