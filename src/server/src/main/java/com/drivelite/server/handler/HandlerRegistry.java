package com.drivelite.server.handler;

import com.drivelite.common.protocol.MessageType;
import com.drivelite.common.protocol.Response;
import com.drivelite.server.net.RequestDispatcher;

/**
 * Registry để đăng ký tất cả handlers.
 * Tách riêng để tránh circular dependency khi compile.
 */
public class HandlerRegistry {

    /**
     * Đăng ký tất cả handlers vào dispatcher.
     */
    public static void registerAll(RequestDispatcher dispatcher) {
        // PING handler - để test connection
        dispatcher.registerHandler(MessageType.PING, (request, context) -> {
            return Response.success("PONG", null);
        });

        // Auth handlers (M4)
        dispatcher.registerHandler(MessageType.LOGIN, new LoginHandler());
        dispatcher.registerHandler(MessageType.REGISTER, new RegisterHandler());
        dispatcher.registerHandler(MessageType.LOGOUT, new LogoutHandler());

        // Password reset handlers (M5)
        dispatcher.registerHandler(MessageType.FORGOT_PASSWORD, new ForgotPasswordHandler());
        dispatcher.registerHandler(MessageType.RESET_PASSWORD, new ResetPasswordHandler());

        System.out.println("[INIT] Handlers registered: PING, LOGIN, REGISTER, LOGOUT, FORGOT_PASSWORD, RESET_PASSWORD");
    }
}
