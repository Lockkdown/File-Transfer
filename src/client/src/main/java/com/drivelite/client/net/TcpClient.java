package com.drivelite.client.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.common.ssl.SSLContextFactory;

/**
 * TCP Client để kết nối tới server.
 * Thread-safe, sử dụng length-prefix framing.
 */
public class TcpClient implements AutoCloseable {

    private static final int CONNECT_TIMEOUT_MS = 10_000; // 10s
    private static final int READ_TIMEOUT_MS = 60_000;    // 60s

    private String host;
    private int port;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private String sessionToken;
    
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "TcpClient-Worker");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean connected = false;
    private Consumer<Boolean> connectionListener;
    
    private SSLContext sslContext;
    private boolean sslEnabled = false;

    public TcpClient() {
    }

    /**
     * Enable SSL/TLS encryption.
     * Phải gọi trước connect().
     * 
     * @param truststorePath Đường dẫn đến truststore (.p12)
     * @param truststorePassword Password của truststore
     */
    public void enableSSL(String truststorePath, String truststorePassword) throws Exception {
        this.sslContext = SSLContextFactory.createClientContext(truststorePath, truststorePassword);
        this.sslEnabled = true;
        System.out.println("[CLIENT] SSL/TLS enabled");
    }

    /**
     * Enable SSL/TLS với Trust All (CHỈ DÙNG CHO DEVELOPMENT).
     * KHÔNG DÙNG TRONG PRODUCTION!
     */
    public void enableSSLTrustAll() throws Exception {
        this.sslContext = SSLContextFactory.createTrustAllContext();
        this.sslEnabled = true;
        System.out.println("[CLIENT] SSL/TLS enabled (TRUST ALL - DEV ONLY!)");
    }

    /**
     * Kết nối tới server.
     */
    public synchronized void connect(String host, int port) throws IOException {
        if (connected) {
            disconnect();
        }
        
        this.host = host;
        this.port = port;
        
        if (sslEnabled && sslContext != null) {
            SSLSocketFactory factory = sslContext.getSocketFactory();
            socket = factory.createSocket();
            socket.connect(new java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            
            // Cấu hình SSL socket
            SSLSocket sslSocket = (SSLSocket) socket;
            sslSocket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            sslSocket.startHandshake();
            System.out.println("[CLIENT] SSL handshake completed");
        } else {
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            System.out.println("[CLIENT] WARNING: Connected without SSL/TLS encryption!");
        }
        socket.setSoTimeout(READ_TIMEOUT_MS);
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        connected = true;
        
        notifyConnectionChange(true);
        System.out.println("[CLIENT] Connected to " + host + ":" + port);
    }

    /**
     * Ngắt kết nối.
     */
    public synchronized void disconnect() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        connected = false;
        sessionToken = null;
        notifyConnectionChange(false);
        System.out.println("[CLIENT] Disconnected");
    }

    /**
     * Gửi request và nhận response (blocking).
     */
    public synchronized Response sendRequest(Request request) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to server");
        }
        
        // Set session token nếu có
        if (sessionToken != null && request.getSessionToken() == null) {
            request.setSessionToken(sessionToken);
        }
        
        try {
            // Send request
            String json;
            try {
                json = request.toJson();
            } catch (Exception e) {
                throw new IOException("Failed to serialize request", e);
            }
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            
            // Write length prefix (4 bytes big-endian)
            outputStream.write((payload.length >> 24) & 0xFF);
            outputStream.write((payload.length >> 16) & 0xFF);
            outputStream.write((payload.length >> 8) & 0xFF);
            outputStream.write(payload.length & 0xFF);
            outputStream.write(payload);
            outputStream.flush();
            
            // Read response
            return readResponse();
            
        } catch (SocketTimeoutException e) {
            throw new IOException("Request timed out", e);
        } catch (IOException e) {
            connected = false;
            notifyConnectionChange(false);
            throw e;
        }
    }

    /**
     * Gửi request async.
     */
    public CompletableFuture<Response> sendRequestAsync(Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendRequest(request);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Đọc response từ server.
     */
    private Response readResponse() throws IOException {
        // Read length prefix (4 bytes)
        byte[] lengthBytes = readExactly(4);
        int length = ((lengthBytes[0] & 0xFF) << 24) |
                     ((lengthBytes[1] & 0xFF) << 16) |
                     ((lengthBytes[2] & 0xFF) << 8) |
                     (lengthBytes[3] & 0xFF);
        
        if (length <= 0 || length > 10 * 1024 * 1024) { // Max 10MB response
            throw new IOException("Invalid response length: " + length);
        }
        
        // Read payload
        byte[] payload = readExactly(length);
        String json = new String(payload, StandardCharsets.UTF_8);
        
        // Debug: print raw JSON
        System.out.println("[TCP] Raw response JSON: " + json);
        
        try {
            Response response = Response.fromJson(json);
            System.out.println("[TCP] Parsed response - ok: " + response.isOk() + ", data type: " + 
                (response.getData() != null ? response.getData().getClass().getName() : "null"));
            return response;
        } catch (Exception e) {
            throw new IOException("Failed to parse response: " + e.getMessage(), e);
        }
    }

    /**
     * Đọc chính xác n bytes (xử lý partial reads).
     */
    private byte[] readExactly(int n) throws IOException {
        byte[] buffer = new byte[n];
        int totalRead = 0;
        while (totalRead < n) {
            int read = inputStream.read(buffer, totalRead, n - totalRead);
            if (read < 0) {
                throw new IOException("Connection closed by server");
            }
            totalRead += read;
        }
        return buffer;
    }

    /**
     * Gửi request mà không đọc response (dùng cho READY signal trước khi nhận file bytes).
     */
    public synchronized void sendRequestOnly(Request request) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to server");
        }
        
        if (sessionToken != null && request.getSessionToken() == null) {
            request.setSessionToken(sessionToken);
        }
        
        try {
            String json;
            try {
                json = request.toJson();
            } catch (Exception e) {
                throw new IOException("Failed to serialize request", e);
            }
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            
            outputStream.write((payload.length >> 24) & 0xFF);
            outputStream.write((payload.length >> 16) & 0xFF);
            outputStream.write((payload.length >> 8) & 0xFF);
            outputStream.write(payload.length & 0xFF);
            outputStream.write(payload);
            outputStream.flush();
        } catch (IOException e) {
            connected = false;
            notifyConnectionChange(false);
            throw e;
        }
    }

    /**
     * Gửi raw bytes (cho upload).
     */
    public void sendRawBytes(byte[] data, int offset, int length) throws IOException {
        outputStream.write(data, offset, length);
        outputStream.flush();
    }

    /**
     * Đọc raw bytes (cho download).
     */
    public int readRawBytes(byte[] buffer, int offset, int length) throws IOException {
        return inputStream.read(buffer, offset, length);
    }

    /**
     * Lấy OutputStream để upload file.
     */
    public OutputStream getOutputStream() {
        return outputStream;
    }

    /**
     * Lấy InputStream để download file.
     */
    public InputStream getInputStream() {
        return inputStream;
    }

    // ========== Session Management ==========

    public void setSessionToken(String token) {
        this.sessionToken = token;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void clearSession() {
        this.sessionToken = null;
    }

    // ========== Connection Status ==========

    public boolean isConnected() {
        boolean actuallyConnected = connected && socket != null && !socket.isClosed();
        if (!actuallyConnected && connected) {
            // Socket was closed but connected flag not updated
            connected = false;
            notifyConnectionChange(false);
        }
        return actuallyConnected;
    }

    /**
     * Kiểm tra và reconnect nếu đã disconnect.
     */
    public synchronized void ensureConnected() throws IOException {
        if (!isConnected() && host != null && port > 0) {
            System.out.println("[CLIENT] Reconnecting to " + host + ":" + port);
            connect(host, port);
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public void setConnectionListener(Consumer<Boolean> listener) {
        this.connectionListener = listener;
    }

    private void notifyConnectionChange(boolean connected) {
        if (connectionListener != null) {
            connectionListener.accept(connected);
        }
    }

    @Override
    public void close() {
        disconnect();
        executor.shutdownNow();
    }
}
