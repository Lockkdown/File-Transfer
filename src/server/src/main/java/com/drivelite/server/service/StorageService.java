package com.drivelite.server.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Service để lưu và đọc file từ disk.
 * Files được lưu theo cấu trúc: storage/{fileId}/{versionNumber}
 * 
 * Security:
 * - Path được tạo từ fileId/versionNumber (integers), không từ user input
 * - Không chấp nhận path traversal
 */
public class StorageService {

    private static StorageService instance;
    private final String storagePath;
    private static final int BUFFER_SIZE = 8192; // 8KB buffer

    private StorageService() {
        Dotenv dotenv = Dotenv.configure()
                .directory(findEnvDirectory())
                .ignoreIfMissing()
                .load();
        
        this.storagePath = dotenv.get("STORAGE_PATH", "./storage");
        
        // Tạo thư mục storage nếu chưa có
        File storageDir = new File(storagePath);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        
        System.out.println("[STORAGE] Initialized at: " + storageDir.getAbsolutePath());
    }

    public static synchronized StorageService getInstance() {
        if (instance == null) {
            instance = new StorageService();
        }
        return instance;
    }

    /**
     * Lưu file từ InputStream vào disk.
     * 
     * @param fileId ID của file
     * @param versionNumber Version number
     * @param inputStream Stream chứa file data
     * @param expectedSize Kích thước file expected (bytes)
     * @return SHA256 hash của file đã lưu
     */
    public String saveFile(int fileId, int versionNumber, InputStream inputStream, long expectedSize) 
            throws IOException, NoSuchAlgorithmException {
        
        // Tạo đường dẫn an toàn (không dùng user input)
        Path filePath = getFilePath(fileId, versionNumber);
        
        // Tạo thư mục cha nếu chưa có
        Files.createDirectories(filePath.getParent());
        
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        long totalBytesRead = 0;
        
        try (OutputStream out = new FileOutputStream(filePath.toFile())) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while (totalBytesRead < expectedSize) {
                // Đọc tối đa số bytes còn lại
                int toRead = (int) Math.min(BUFFER_SIZE, expectedSize - totalBytesRead);
                bytesRead = inputStream.read(buffer, 0, toRead);
                
                if (bytesRead == -1) {
                    throw new IOException("Unexpected end of stream. Expected " + expectedSize + 
                                        " bytes, got " + totalBytesRead);
                }
                
                out.write(buffer, 0, bytesRead);
                sha256.update(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
        }
        
        // Verify size
        if (totalBytesRead != expectedSize) {
            // Xóa file nếu size không khớp
            Files.deleteIfExists(filePath);
            throw new IOException("Size mismatch. Expected " + expectedSize + 
                                " bytes, got " + totalBytesRead);
        }
        
        return bytesToHex(sha256.digest());
    }

    /**
     * Đọc file từ disk.
     * 
     * @param fileId ID của file
     * @param versionNumber Version number
     * @return InputStream của file
     */
    public InputStream readFile(int fileId, int versionNumber) throws IOException {
        Path filePath = getFilePath(fileId, versionNumber);
        
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filePath);
        }
        
        return new FileInputStream(filePath.toFile());
    }

    /**
     * Lấy kích thước file.
     */
    public long getFileSize(int fileId, int versionNumber) throws IOException {
        Path filePath = getFilePath(fileId, versionNumber);
        return Files.size(filePath);
    }

    /**
     * Xóa file.
     */
    public boolean deleteFile(int fileId, int versionNumber) throws IOException {
        Path filePath = getFilePath(fileId, versionNumber);
        return Files.deleteIfExists(filePath);
    }

    /**
     * Tính SHA256 của file trên disk.
     */
    public String computeSha256(int fileId, int versionNumber) throws IOException, NoSuchAlgorithmException {
        Path filePath = getFilePath(fileId, versionNumber);
        
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        
        try (InputStream in = new FileInputStream(filePath.toFile())) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                sha256.update(buffer, 0, bytesRead);
            }
        }
        
        return bytesToHex(sha256.digest());
    }

    /**
     * Lấy đường dẫn file (safe path, không dùng user input).
     */
    private Path getFilePath(int fileId, int versionNumber) {
        // Path: storage/{fileId}/{versionNumber}
        // fileId và versionNumber là integers, không thể path traversal
        return Paths.get(storagePath, String.valueOf(fileId), String.valueOf(versionNumber));
    }

    /**
     * Lấy stored path relative (để lưu vào DB).
     */
    public String getStoredPath(int fileId, int versionNumber) {
        return fileId + "/" + versionNumber;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String findEnvDirectory() {
        String[] possiblePaths = { ".", "..", "../..", "../../..", System.getProperty("user.dir") };
        for (String path : possiblePaths) {
            File envFile = new File(path, ".env");
            if (envFile.exists()) {
                return path;
            }
        }
        return ".";
    }
}
