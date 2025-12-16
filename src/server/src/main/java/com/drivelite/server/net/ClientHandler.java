package com.drivelite.server.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * ClientHandler - Xử lý 1 client connection trong thread riêng.
 * 
 * Mỗi client kết nối sẽ có 1 ClientHandler chạy trong thread pool.
 * Handler đọc request liên tục cho đến khi client disconnect.
 */
public class ClientHandler implements Runnable {

    private static final int SOCKET_TIMEOUT_MS = 5 * 60 * 1000; // 5 phút

    private final Socket socket;
    private final RequestDispatcher dispatcher;
    private final ClientContext context;

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
            // Set socket timeout để tránh client "treo" vĩnh viễn
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // Request loop - đọc và xử lý request liên tục
            boolean continueLoop = true;
            while (continueLoop) {
                continueLoop = dispatcher.processRequest(in, out, context);
            }

        } catch (IOException e) {
            System.err.println("[HANDLER] IO error for " + clientAddr + ": " + e.getMessage());
        } finally {
            // Cleanup
            closeSocket();
            System.out.println("[HANDLER] Ended for client: " + clientAddr);
        }
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
