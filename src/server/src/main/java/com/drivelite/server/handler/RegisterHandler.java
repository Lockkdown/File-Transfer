package com.drivelite.server.handler;

import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Pattern;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.common.protocol.ResponseCode;
import com.drivelite.server.db.entity.User;
import com.drivelite.server.db.repository.UserRepository;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.net.RequestHandler;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Handler cho REGISTER request.
 * 
 * Input: { email, password, displayName }
 * Output: { userId, email, displayName }
 * 
 * Flow:
 * 1. Validate input (email format, password strength, displayName)
 * 2. Kiểm tra email đã tồn tại chưa
 * 3. Hash password với BCrypt
 * 4. Tạo user mới
 * 5. Trả về user info (không tự động login)
 */
public class RegisterHandler implements RequestHandler {

    // BCrypt cost factor (12 là balance giữa security và performance)
    private static final int BCRYPT_COST = 12;
    
    // Email regex pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    // Password requirements
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;
    
    // Display name requirements
    private static final int MIN_DISPLAY_NAME_LENGTH = 2;
    private static final int MAX_DISPLAY_NAME_LENGTH = 50;

    private final UserRepository userRepository;

    public RegisterHandler() {
        this.userRepository = new UserRepository();
    }

    public RegisterHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
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
            String displayName = (String) data.get("displayName");

            // 2. Validate email
            if (email == null || email.trim().isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Email is required");
            }
            email = email.trim().toLowerCase();
            
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Invalid email format");
            }

            // 3. Validate password
            if (password == null || password.isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Password is required");
            }
            if (password.length() < MIN_PASSWORD_LENGTH) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            }
            if (password.length() > MAX_PASSWORD_LENGTH) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "Password must be at most " + MAX_PASSWORD_LENGTH + " characters");
            }

            // 4. Validate displayName
            if (displayName == null || displayName.trim().isEmpty()) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Display name is required");
            }
            displayName = displayName.trim();
            
            if (displayName.length() < MIN_DISPLAY_NAME_LENGTH) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "Display name must be at least " + MIN_DISPLAY_NAME_LENGTH + " characters");
            }
            if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "Display name must be at most " + MAX_DISPLAY_NAME_LENGTH + " characters");
            }

            // 5. Kiểm tra email đã tồn tại chưa
            if (userRepository.existsByEmail(email)) {
                return Response.error(ResponseCode.CONFLICT, "Email already registered");
            }

            // 6. Hash password với BCrypt
            String passwordHash = BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray());

            // 7. Tạo user mới
            User user = userRepository.create(email, passwordHash, displayName);

            // 8. Trả về response
            System.out.println("[REGISTER] Success: " + email + " from " + context.getClientAddress());

            return Response.success("Registration successful", Map.of(
                "userId", user.getUserId(),
                "email", user.getEmail(),
                "displayName", user.getDisplayName()
            ));

        } catch (SQLException e) {
            System.err.println("[REGISTER] Database error: " + e.getMessage());
            // Có thể là duplicate key nếu race condition
            if (e.getMessage().contains("duplicate") || e.getMessage().contains("UNIQUE")) {
                return Response.error(ResponseCode.CONFLICT, "Email already registered");
            }
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[REGISTER] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Internal error");
        }
    }
}
