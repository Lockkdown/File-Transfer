package com.drivelite.server.handler;

import java.sql.SQLException;
import java.util.Optional;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.common.protocol.ResponseCode;
import com.drivelite.server.db.entity.Session;
import com.drivelite.server.db.entity.User;
import com.drivelite.server.db.repository.SessionRepository;
import com.drivelite.server.db.repository.UserRepository;
import com.drivelite.server.net.ClientContext;

/**
 * Middleware để xác thực session token.
 * 
 * Dùng để wrap các handler cần authentication.
 * Kiểm tra token hợp lệ trước khi cho phép handler xử lý.
 */
public class AuthMiddleware {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    public AuthMiddleware() {
        this.sessionRepository = new SessionRepository();
        this.userRepository = new UserRepository();
    }

    public AuthMiddleware(SessionRepository sessionRepository, UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Xác thực request.
     * 
     * @param request Request cần xác thực
     * @param context Client context
     * @return null nếu hợp lệ, Response error nếu không hợp lệ
     */
    public Response authenticate(Request request, ClientContext context) {
        try {
            // 1. Kiểm tra context đã có session chưa (từ login trước đó trong cùng connection)
            if (context.isAuthenticated()) {
                return null; // OK
            }

            // 2. Lấy token từ request
            String token = request.getSessionToken();
            if (token == null || token.isEmpty()) {
                return Response.error(ResponseCode.UNAUTHORIZED, "Session token required");
            }

            // 3. Tìm session trong database
            Optional<Session> sessionOpt = sessionRepository.findByToken(token);
            if (sessionOpt.isEmpty()) {
                return Response.error(ResponseCode.UNAUTHORIZED, "Invalid or expired session");
            }

            Session session = sessionOpt.get();

            // 4. Lấy user info
            Optional<User> userOpt = userRepository.findById(session.getUserId());
            if (userOpt.isEmpty()) {
                return Response.error(ResponseCode.UNAUTHORIZED, "User not found");
            }

            User user = userOpt.get();

            // 5. Kiểm tra user còn active không
            if (!user.isActive()) {
                return Response.error(ResponseCode.UNAUTHORIZED, "Account is disabled");
            }

            // 6. Lưu session vào context để các request sau không cần query lại
            context.setSession(token, user.getUserId(), user.getEmail());

            return null; // OK

        } catch (SQLException e) {
            System.err.println("[AUTH] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Authentication error");
        }
    }

    /**
     * Kiểm tra request có cần authentication không.
     * Các request auth (LOGIN, REGISTER, FORGOT_PASSWORD, RESET_PASSWORD, PING) không cần token.
     */
    public static boolean requiresAuth(Request request) {
        if (request.getType() == null) {
            return true;
        }
        
        switch (request.getType()) {
            case LOGIN:
            case REGISTER:
            case FORGOT_PASSWORD:
            case RESET_PASSWORD:
            case PING:
                return false;
            default:
                return true;
        }
    }
}
