package com.drivelite.server.handler;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.common.protocol.ResponseCode;
import com.drivelite.server.db.entity.User;
import com.drivelite.server.db.repository.PasswordResetTokenRepository;
import com.drivelite.server.db.repository.UserRepository;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.net.RequestHandler;
import com.drivelite.server.service.EmailService;

/**
 * Handler cho FORGOT_PASSWORD request.
 * 
 * Input: { email }
 * Output: { message, expiresInMinutes }
 * 
 * Flow:
 * 1. Validate email
 * 2. Tìm user theo email
 * 3. Xóa các token cũ của user (nếu có)
 * 4. Tạo OTP 6 số
 * 5. Gửi OTP qua email
 * 6. Lưu OTP vào database
 * 7. Trả về success (không trả về OTP trực tiếp)
 */
public class ForgotPasswordHandler implements RequestHandler {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;

    public ForgotPasswordHandler() {
        this.userRepository = new UserRepository();
        this.tokenRepository = new PasswordResetTokenRepository();
        this.emailService = EmailService.getInstance();
    }

    public ForgotPasswordHandler(UserRepository userRepository, PasswordResetTokenRepository tokenRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
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

            String email = (String) data.get("email");

            // 2. Validate email
            if (email == null || email.trim().isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Email is required");
            }
            email = email.trim().toLowerCase();

            // 3. Tìm user theo email
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                // Không nói rõ "email không tồn tại" để tránh enumeration attack
                System.out.println("[FORGOT_PASSWORD] Email not found: " + email);
                return Response.success("If the email exists, an OTP has been sent", Map.of(
                    "message", "Check your email for the OTP code",
                    "expiresInMinutes", 15
                ));
            }

            User user = userOpt.get();

            // 4. Kiểm tra user có active không
            if (!user.isActive()) {
                return Response.error(ResponseCode.UNAUTHORIZED, "Account is disabled");
            }

            // 5. Xóa các token cũ của user
            tokenRepository.deleteAllByUserId(user.getUserId());

            // 6. Tạo OTP 6 số
            String otp = emailService.generateOTP();

            // 7. Lưu OTP vào database
            tokenRepository.create(user.getUserId(), otp);

            // 8. Gửi OTP qua email
            boolean emailSent = emailService.sendOTP(email, otp);
            
            if (!emailSent && emailService.isEnabled()) {
                System.err.println("[FORGOT_PASSWORD] Failed to send email to: " + email);
                return Response.error(ResponseCode.SERVER_ERROR, "Failed to send email");
            }

            // 9. Trả về response
            System.out.println("[FORGOT_PASSWORD] OTP created for: " + email + 
                             " (expires in 15 minutes)");

            return Response.success("OTP has been sent to your email", Map.of(
                "message", "Check your email for the OTP code",
                "expiresInMinutes", 15
            ));

        } catch (SQLException e) {
            System.err.println("[FORGOT_PASSWORD] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[FORGOT_PASSWORD] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Internal error");
        }
    }
}
