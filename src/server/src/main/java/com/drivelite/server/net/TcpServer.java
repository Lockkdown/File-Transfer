package com.drivelite.server.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import com.drivelite.common.ssl.SSLContextFactory;

/**
 * TCP Server - Accept loop chính của ứng dụng.
 * 
 * Server lắng nghe trên 1 port, mỗi client kết nối sẽ được
 * xử lý bởi 1 thread riêng trong thread pool.
 * 
 * Thread pool giúp:
 * - Giới hạn số thread tối đa (tránh tạo quá nhiều thread)
 * - Tái sử dụng thread (không tạo/hủy liên tục)
 */
public class TcpServer {

    private final int port;
    private final int maxClients;
    private final RequestDispatcher dispatcher;
    
    private ServerSocket serverSocket;
    private SSLContext sslContext;
    private boolean sslEnabled = false;
    private ExecutorService threadPool;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Tạo TCP Server.
     * 
     * @param port Port để lắng nghe (ví dụ: 9000)
     * @param maxClients Số client tối đa đồng thời
     * @param dispatcher Dispatcher để xử lý request
     */
    public TcpServer(int port, int maxClients, RequestDispatcher dispatcher) {
        this.port = port;
        this.maxClients = maxClients;
        this.dispatcher = dispatcher;
    }

    /**
     * Enable SSL/TLS encryption.
     * Phải gọi trước start().
     * 
     * @param keystorePath Đường dẫn đến keystore (.p12)
     * @param keystorePassword Password của keystore
     */
    public void enableSSL(String keystorePath, String keystorePassword) throws Exception {
        this.sslContext = SSLContextFactory.createServerContext(keystorePath, keystorePassword);
        this.sslEnabled = true;
        System.out.println("[SERVER] SSL/TLS enabled");
    }

    /**
     * Start server và bắt đầu accept loop.
     * Method này sẽ BLOCK cho đến khi server stop.
     */
    public void start() throws IOException {
        if (sslEnabled && sslContext != null) {
            SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
            serverSocket = factory.createServerSocket(port);
            // Cấu hình SSL socket
            SSLServerSocket sslServerSocket = (SSLServerSocket) serverSocket;
            sslServerSocket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            System.out.println("[SERVER] Using SSL/TLS encryption");
        } else {
            serverSocket = new ServerSocket(port);
            System.out.println("[SERVER] WARNING: Running without SSL/TLS encryption!");
        }
        threadPool = Executors.newFixedThreadPool(maxClients);
        running.set(true);

        System.out.println("=================================");
        System.out.println("  Drive-lite Server Started");
        System.out.println("  Port: " + port);
        System.out.println("  SSL/TLS: " + (sslEnabled ? "ENABLED" : "DISABLED"));
        System.out.println("  Max clients: " + maxClients);
        System.out.println("=================================");

        // Accept loop - chạy liên tục cho đến khi stop()
        while (running.get()) {
            try {
                // Block đợi client kết nối
                Socket clientSocket = serverSocket.accept();
                
                // Log connection
                String clientInfo = clientSocket.getInetAddress().getHostAddress() + 
                                   ":" + clientSocket.getPort();
                System.out.println("[SERVER] New connection: " + clientInfo);

                // Tạo handler và submit vào thread pool
                ClientHandler handler = new ClientHandler(clientSocket, dispatcher);
                threadPool.submit(handler);
                
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("[SERVER] Accept error: " + e.getMessage());
                }
                // Nếu running = false, đây là do stop() đóng serverSocket
            }
        }
    }

    /**
     * Stop server gracefully.
     * - Đóng ServerSocket (ngừng accept)
     * - Đợi các client handler hoàn thành
     * - Shutdown thread pool
     */
    public void stop() {
        System.out.println("[SERVER] Stopping...");
        running.set(false);

        // Đóng ServerSocket để thoát khỏi accept() blocking
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("[SERVER] Error closing server socket: " + e.getMessage());
            }
        }

        // Shutdown thread pool
        if (threadPool != null) {
            threadPool.shutdown();
            try {
                // Đợi tối đa 30 giây cho các task hoàn thành
                if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("[SERVER] Stopped.");
    }

    /**
     * Kiểm tra server có đang chạy không.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Lấy port đang lắng nghe.
     */
    public int getPort() {
        return port;
    }
}
