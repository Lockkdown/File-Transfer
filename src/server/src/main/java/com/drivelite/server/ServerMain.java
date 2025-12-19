package com.drivelite.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.drivelite.common.util.NetworkUtils;
import com.drivelite.server.db.DatabaseConfig;
import com.drivelite.server.db.DatabaseManager;
import com.drivelite.server.handler.HandlerRegistry;
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
            // Khi chạy từ thư mục src, file .env thường nằm ở project root (thư mục cha).
            Path cwd = Paths.get("").toAbsolutePath();
            Path envInCwd = cwd.resolve(".env");
            Path envInParent = cwd.getParent() != null ? cwd.getParent().resolve(".env") : null;
            String dotenvDir;
            if (Files.exists(envInCwd)) {
                dotenvDir = cwd.toString();
            } else if (envInParent != null && Files.exists(envInParent)) {
                dotenvDir = cwd.getParent().toString();
            } else {
                dotenvDir = cwd.toString();
            }

            Dotenv dotenv = Dotenv.configure()
                    .directory(dotenvDir)
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

            // Enable SSL/TLS nếu có cấu hình
            String keystorePath = dotenv.get("SSL_KEYSTORE_PATH");
            String keystorePassword = dotenv.get("SSL_KEYSTORE_PASSWORD");
            if (keystorePath != null && !keystorePath.isEmpty() && 
                keystorePassword != null && !keystorePassword.isEmpty()) {
                try {
                    Path ksPath = Paths.get(keystorePath);
                    if (!ksPath.isAbsolute()) {
                        ksPath = Paths.get(dotenvDir).resolve(keystorePath).normalize();
                    }
                    server.enableSSL(ksPath.toString(), keystorePassword);
                } catch (Exception e) {
                    System.err.println("[SSL] Failed to enable SSL: " + e.getMessage());
                    System.err.println("[SSL] Running without encryption!");
                }
            } else {
                System.out.println("[SSL] No keystore configured. Running without encryption.");
                System.out.println("[SSL] To enable: set SSL_KEYSTORE_PATH and SSL_KEYSTORE_PASSWORD in .env");
            }

            // Hiển thị danh sách IP để client connect
            NetworkUtils.printAvailableAddresses();

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
     */
    private static void registerHandlers(RequestDispatcher dispatcher) {
        HandlerRegistry.registerAll(dispatcher);
    }
}
