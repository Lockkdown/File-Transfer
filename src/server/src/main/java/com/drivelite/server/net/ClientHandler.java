package com.drivelite.server.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * ClientHandler - Xử lý 1 client connection trong thread riêng.
 * 
 * Mỗi client kết nối sẽ có 1 ClientHandler chạy trong thread pool.
 * Handler đọc request liên tục cho đến khi client disconnect.
 * 
 * Security features:
 * - Socket timeout để tránh resource exhaustion
 * - Graceful disconnect handling
 * - Proper cleanup khi connection bị đóng
 */
public class ClientHandler implements Runnable {

    private static final int SOCKET_TIMEOUT_MS = 5 * 60 * 1000; // 5 phút idle timeout

    private final Socket socket;
    private final RequestDispatcher dispatcher;
    private final ClientContext context;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, RequestDispatcher dispatcher) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.context = new ClientContext(socket);
    }

    @Override
    public void run() {
        String clientAddr = context.getClientAddress();
        System.out.println("[HANDLER] Started for client: " + clientAddr);

        try {
            // Configure socket cho security và stability
            configureSocket();

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // Request loop - đọc và xử lý request liên tục
            while (running && !socket.isClosed()) {
                try {
                    boolean continueLoop = dispatcher.processRequest(in, out, context);
                    if (!continueLoop) {
                        break;
                    }
                } catch (Exception e) {
                    if (!handleRequestException(e, clientAddr)) {
                        break;
                    }
                }
            }

        } catch (SocketTimeoutException e) {
            System.out.println("[HANDLER] Timeout for " + clientAddr + " (idle too long)");
        } catch (SocketException e) {
            // Connection reset, broken pipe, etc.
            System.out.println("[HANDLER] Socket error for " + clientAddr + ": " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[HANDLER] IO error for " + clientAddr + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[HANDLER] Unexpected error for " + clientAddr + ": " + e.getMessage());
        } finally {
            // Cleanup
            cleanup(clientAddr);
        }
    }

    /**
     * Configure socket options cho security và stability.
     */
    private void configureSocket() throws SocketException {
        // Timeout để tránh client "treo" vĩnh viễn (resource exhaustion attack)
        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        
        // Keep-alive để detect dead connections
        socket.setKeepAlive(true);
        
        // TCP_NODELAY để giảm latency cho small messages
        socket.setTcpNoDelay(true);
    }

    /**
     * Xử lý exception trong request loop.
     * @return true nếu nên tiếp tục loop, false nếu nên dừng
     */
    private boolean handleRequestException(Exception e, String clientAddr) {
        if (e instanceof SocketTimeoutException) {
            System.out.println("[HANDLER] Read timeout for " + clientAddr);
            return false; // Dừng loop
        }
        
        if (e instanceof SocketException) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("reset") || msg.contains("closed") || msg.contains("broken"))) {
                System.out.println("[HANDLER] Connection lost for " + clientAddr);
                return false;
            }
        }
        
        if (e instanceof java.io.EOFException) {
            System.out.println("[HANDLER] Client disconnected: " + clientAddr);
            return false;
        }
        
        // Log unexpected errors nhưng vẫn tiếp tục nếu có thể
        System.err.println("[HANDLER] Error processing request for " + clientAddr + ": " + e.getMessage());
        return false; // Để an toàn, dừng loop khi có error
    }

    /**
     * Cleanup resources khi connection kết thúc.
     */
    private void cleanup(String clientAddr) {
        running = false;
        
        // Clear sensitive context data
        if (context != null) {
            context.clearSession();
            context.clearUploadContext();
            context.clearDownloadContext();
            context.clearUploadNewVersionContext();
        }
        
        // Close socket
        closeSocket();
        
        System.out.println("[HANDLER] Ended for client: " + clientAddr);
    }

    /**
     * Dừng handler (gọi từ bên ngoài khi cần shutdown).
     */
    public void stop() {
        running = false;
        closeSocket();
    }

    private void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("[HANDLER] Error closing socket: " + e.getMessage());
            }
        }
    }

    /**
     * Lấy context của client này.
     */
    public ClientContext getContext() {
        return context;
    }
}
