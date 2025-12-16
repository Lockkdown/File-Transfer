package com.drivelite.server;

import com.drivelite.common.protocol.MessageType;
import com.drivelite.common.protocol.Response;
import com.drivelite.server.db.DatabaseConfig;
import com.drivelite.server.db.DatabaseManager;
import com.drivelite.server.net.RequestDispatcher;
import com.drivelite.server.net.TcpServer;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Entry point cho Server application.
 */
public class ServerMain {

    private static final int DEFAULT_PORT = 9000;
    private static final int DEFAULT_MAX_CLIENTS = 50;

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("  Drive-lite Server v1.0.0");
        System.out.println("=================================");

        try {
            // Load config từ .env
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();

            int port = Integer.parseInt(dotenv.get("SERVER_PORT", String.valueOf(DEFAULT_PORT)));
            int maxClients = DEFAULT_MAX_CLIENTS;

            // Khởi tạo Database
            System.out.println("[INIT] Initializing database connection...");
            DatabaseConfig dbConfig = new DatabaseConfig();
            DatabaseManager.initialize(dbConfig);
            
            if (DatabaseManager.getInstance().testConnection()) {
                System.out.println("[INIT] Database connection OK");
            } else {
                System.err.println("[INIT] Database connection FAILED!");
                return;
            }

            // Tạo RequestDispatcher và đăng ký handlers
            RequestDispatcher dispatcher = new RequestDispatcher();
            registerHandlers(dispatcher);

            // Tạo và start TCP Server
            TcpServer server = new TcpServer(port, maxClients, dispatcher);

            // Xử lý shutdown gracefully (Ctrl+C)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SHUTDOWN] Received shutdown signal...");
                server.stop();
            }));

            // Start server (blocking)
            server.start();

        } catch (Exception e) {
            System.err.println("[ERROR] Server failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Đăng ký các request handlers.
     * Hiện tại chỉ có PING handler để test.
     * Các handler khác sẽ được thêm ở M4.
     */
    private static void registerHandlers(RequestDispatcher dispatcher) {
        // PING handler - để test connection
        dispatcher.registerHandler(MessageType.PING, (request, context) -> {
            return Response.success("PONG", null);
        });

        // Placeholder cho các handler khác (sẽ implement ở M4)
        System.out.println("[INIT] Handlers registered. Ready to accept connections.");
    }
}
