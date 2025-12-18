package com.drivelite.server.security;

import java.util.regex.Pattern;

/**
 * Utility class tập trung các hàm validation để đảm bảo security consistency.
 * 
 * Security features:
 * - Path traversal protection (chống tấn công đường dẫn)
 * - Input length validation (giới hạn độ dài)
 * - Format validation (kiểm tra định dạng)
 * - Dangerous character filtering (lọc ký tự nguy hiểm)
 */
public final class ValidationUtils {

    // ========== EMAIL VALIDATION ==========
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final int MAX_EMAIL_LENGTH = 255;

    // ========== PASSWORD VALIDATION ==========
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;

    // ========== DISPLAY NAME VALIDATION ==========
    private static final int MIN_DISPLAY_NAME_LENGTH = 2;
    private static final int MAX_DISPLAY_NAME_LENGTH = 50;

    // ========== FILENAME VALIDATION ==========
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final Pattern DANGEROUS_FILENAME_CHARS = Pattern.compile("[<>:\"|?*\\x00-\\x1F]");
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile("\\.\\.[\\\\/]|[\\\\/]\\.\\.");

    // ========== SHA256 VALIDATION ==========
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");

    // ========== FILE SIZE VALIDATION ==========
    private static final long DEFAULT_MAX_FILE_SIZE = 500L * 1024 * 1024; // 500MB

    private ValidationUtils() {
        // Prevent instantiation
    }

    // ==================== EMAIL ====================

    /**
     * Validate email format và length.
     * @return null nếu valid, error message nếu invalid
     */
    public static String validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "Email is required";
        }
        email = email.trim();
        if (email.length() > MAX_EMAIL_LENGTH) {
            return "Email must be at most " + MAX_EMAIL_LENGTH + " characters";
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return "Invalid email format";
        }
        return null; // Valid
    }

    /**
     * Normalize email (trim + lowercase).
     */
    public static String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    // ==================== PASSWORD ====================

    /**
     * Validate password length.
     * @return null nếu valid, error message nếu invalid
     */
    public static String validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return "Password is required";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters";
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return "Password must be at most " + MAX_PASSWORD_LENGTH + " characters";
        }
        return null; // Valid
    }

    // ==================== DISPLAY NAME ====================

    /**
     * Validate display name length.
     * @return null nếu valid, error message nếu invalid
     */
    public static String validateDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "Display name is required";
        }
        displayName = displayName.trim();
        if (displayName.length() < MIN_DISPLAY_NAME_LENGTH) {
            return "Display name must be at least " + MIN_DISPLAY_NAME_LENGTH + " characters";
        }
        if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            return "Display name must be at most " + MAX_DISPLAY_NAME_LENGTH + " characters";
        }
        return null; // Valid
    }

    // ==================== FILENAME (Path Traversal Protection) ====================

    /**
     * Sanitize filename để chống path traversal attack.
     * 
     * Path traversal attack = kẻ tấn công gửi filename như "../../etc/passwd"
     * để truy cập file ngoài thư mục cho phép.
     * 
     * @param fileName Tên file từ client
     * @return Tên file đã được sanitize, hoặc null nếu invalid
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }

        // 1. Kiểm tra path traversal pattern trước
        if (PATH_TRAVERSAL_PATTERN.matcher(fileName).find()) {
            System.err.println("[SECURITY] Path traversal attempt detected: " + fileName);
            return null;
        }

        // 2. Loại bỏ path separators - chỉ giữ tên file
        fileName = fileName.replace("\\", "/");
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }

        // 3. Loại bỏ ký tự nguy hiểm
        fileName = DANGEROUS_FILENAME_CHARS.matcher(fileName).replaceAll("_");

        // 4. Không cho phép bắt đầu bằng dấu chấm (hidden files / special files)
        while (fileName.startsWith(".")) {
            fileName = fileName.substring(1);
        }

        // 5. Không cho phép các tên file đặc biệt Windows
        String upperName = fileName.toUpperCase();
        if (isReservedWindowsName(upperName)) {
            fileName = "_" + fileName;
        }

        // 6. Giới hạn độ dài
        if (fileName.length() > MAX_FILENAME_LENGTH) {
            // Giữ extension nếu có
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                String ext = fileName.substring(dotIndex);
                String name = fileName.substring(0, dotIndex);
                int maxNameLength = MAX_FILENAME_LENGTH - ext.length();
                if (maxNameLength > 0) {
                    fileName = name.substring(0, Math.min(name.length(), maxNameLength)) + ext;
                } else {
                    fileName = fileName.substring(0, MAX_FILENAME_LENGTH);
                }
            } else {
                fileName = fileName.substring(0, MAX_FILENAME_LENGTH);
            }
        }

        // 7. Kiểm tra kết quả cuối cùng
        fileName = fileName.trim();
        if (fileName.isEmpty()) {
            return null;
        }

        return fileName;
    }

    /**
     * Validate filename (không sanitize, chỉ check).
     * @return null nếu valid, error message nếu invalid
     */
    public static String validateFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "Filename is required";
        }
        if (PATH_TRAVERSAL_PATTERN.matcher(fileName).find()) {
            return "Invalid filename: path traversal not allowed";
        }
        if (fileName.length() > MAX_FILENAME_LENGTH) {
            return "Filename must be at most " + MAX_FILENAME_LENGTH + " characters";
        }
        return null; // Valid
    }

    /**
     * Kiểm tra tên file có phải là reserved name của Windows không.
     */
    private static boolean isReservedWindowsName(String name) {
        // Loại bỏ extension để check
        int dotIndex = name.indexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }
        
        // Windows reserved names
        String[] reserved = {
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        };
        
        for (String r : reserved) {
            if (name.equals(r)) {
                return true;
            }
        }
        return false;
    }

    // ==================== FILE SIZE ====================

    /**
     * Validate file size.
     * @param fileSize Size in bytes
     * @param maxSize Maximum allowed size (0 = use default)
     * @return null nếu valid, error message nếu invalid
     */
    public static String validateFileSize(long fileSize, long maxSize) {
        if (maxSize <= 0) {
            maxSize = DEFAULT_MAX_FILE_SIZE;
        }
        
        if (fileSize <= 0) {
            return "File size must be positive";
        }
        if (fileSize > maxSize) {
            return "File too large. Maximum size is " + (maxSize / 1024 / 1024) + "MB";
        }
        return null; // Valid
    }

    // ==================== SHA256 ====================

    /**
     * Validate SHA256 hash format.
     * @return null nếu valid, error message nếu invalid
     */
    public static String validateSha256(String sha256) {
        if (sha256 == null || sha256.trim().isEmpty()) {
            return "SHA256 hash is required";
        }
        if (!SHA256_PATTERN.matcher(sha256).matches()) {
            return "Invalid SHA256 format (must be 64 hex characters)";
        }
        return null; // Valid
    }

    /**
     * Normalize SHA256 (trim + lowercase).
     */
    public static String normalizeSha256(String sha256) {
        if (sha256 == null) return null;
        return sha256.trim().toLowerCase();
    }

    // ==================== GENERIC ====================

    /**
     * Validate integer ID (must be positive).
     * @return null nếu valid, error message nếu invalid
     */
    public static String validateId(Integer id, String fieldName) {
        if (id == null) {
            return fieldName + " is required";
        }
        if (id <= 0) {
            return fieldName + " must be positive";
        }
        return null; // Valid
    }

    /**
     * Validate string not empty.
     * @return null nếu valid, error message nếu invalid
     */
    public static String validateNotEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return fieldName + " is required";
        }
        return null; // Valid
    }

    /**
     * Validate string length.
     * @return null nếu valid, error message nếu invalid
     */
    public static String validateLength(String value, String fieldName, int min, int max) {
        if (value == null) {
            return fieldName + " is required";
        }
        if (value.length() < min) {
            return fieldName + " must be at least " + min + " characters";
        }
        if (value.length() > max) {
            return fieldName + " must be at most " + max + " characters";
        }
        return null; // Valid
    }
}
