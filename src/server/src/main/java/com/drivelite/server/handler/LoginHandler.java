package com.drivelite.server.handler;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.common.protocol.ResponseCode;
import com.drivelite.server.db.entity.Session;
import com.drivelite.server.db.entity.User;
import com.drivelite.server.db.repository.SessionRepository;
import com.drivelite.server.db.repository.UserRepository;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.net.RequestHandler;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Handler cho LOGIN request.
 * 
 * Input: { email, password }
 * Output: { token, userId, displayName }
 * 
 * Flow:
 * 1. Validate input (email, password không rỗng)
 * 2. Tìm user theo email
 * 3. Verify password với BCrypt
 * 4. Tạo session token
 * 5. Lưu session vào context
 * 6. Trả về token + user info
 */
public class LoginHandler implements RequestHandler {

    private static final int SESSION_EXPIRY_HOURS = 24;

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;

    public LoginHandler() {
        this.userRepository = new UserRepository();
        this.sessionRepository = new SessionRepository();
    }

    public LoginHandler(UserRepository userRepository, SessionRepository sessionRepository) {
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

            String email = (String) data.get("email");
            String password = (String) data.get("password");

            // 2. Validate input
            if (email == null || email.trim().isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Email is required");
            }
            if (password == null || password.isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Password is required");
            }

            email = email.trim().toLowerCase();

            // 3. Tìm user theo email
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                // Không nói rõ "email không tồn tại" để tránh enumeration attack
                return Response.error(ResponseCode.UNAUTHORIZED, "Invalid email or password");
            }

            User user = userOpt.get();

            // 4. Kiểm tra user có active không
            if (!user.isActive()) {
                return Response.error(ResponseCode.UNAUTHORIZED, "Account is disabled");
            }

            // 5. Verify password với BCrypt
            BCrypt.Result result = BCrypt.verifyer().verify(
                password.toCharArray(), 
                user.getPasswordHash()
            );

            if (!result.verified) {
                return Response.error(ResponseCode.UNAUTHORIZED, "Invalid email or password");
            }

            // 6. Tạo session
            Session session = sessionRepository.create(user.getUserId(), SESSION_EXPIRY_HOURS);

            // 7. Lưu session vào context (để các request sau không cần gửi lại token)
            context.setSession(session.getToken(), user.getUserId(), user.getEmail());

            // 8. Trả về response
            System.out.println("[LOGIN] Success: " + email + " from " + context.getClientAddress());

            return Response.success("Login successful", Map.of(
                "token", session.getToken(),
                "userId", user.getUserId(),
                "displayName", user.getDisplayName(),
                "email", user.getEmail()
            ));

        } catch (SQLException e) {
            System.err.println("[LOGIN] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[LOGIN] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Internal error");
        }
    }
}
