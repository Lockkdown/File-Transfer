package com.drivelite.server.handler;

import java.sql.SQLException;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.common.protocol.ResponseCode;
import com.drivelite.server.db.repository.SessionRepository;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.net.RequestHandler;

/**
 * Handler cho LOGOUT request.
 * 
 * Input: (không cần data, dùng sessionToken từ request hoặc context)
 * Output: { message }
 * 
 * Flow:
 * 1. Lấy session token từ request hoặc context
 * 2. Xóa session khỏi database
 * 3. Clear session trong context
 * 4. Trả về success
 */
public class LogoutHandler implements RequestHandler {

    private final SessionRepository sessionRepository;

    public LogoutHandler() {
        this.sessionRepository = new SessionRepository();
    }

    public LogoutHandler(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Response handle(Request request, ClientContext context) {
        try {
            // 1. Lấy session token (ưu tiên từ request, fallback từ context)
            String token = request.getSessionToken();
            if (token == null || token.isEmpty()) {
                token = context.getSessionToken();
            }

            if (token == null || token.isEmpty()) {
                return Response.error(ResponseCode.UNAUTHORIZED, "No session to logout");
            }

            // 2. Xóa session khỏi database
            boolean deleted = sessionRepository.deleteByToken(token);

            // 3. Clear session trong context
            context.clearSession();

            // 4. Trả về response
            if (deleted) {
                System.out.println("[LOGOUT] Success from " + context.getClientAddress());
                return Response.success("Logout successful", null);
            } else {
                // Session không tồn tại hoặc đã hết hạn, vẫn coi như logout thành công
                System.out.println("[LOGOUT] Session not found, treating as success from " + context.getClientAddress());
                return Response.success("Logout successful", null);
            }

        } catch (SQLException e) {
            System.err.println("[LOGOUT] Database error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Database error");
        } catch (Exception e) {
            System.err.println("[LOGOUT] Error: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, "Internal error");
        }
    }
}
