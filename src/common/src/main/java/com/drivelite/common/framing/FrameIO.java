package com.drivelite.common.framing;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * FrameIO - Đóng khung gói tin TCP với length-prefix.
 * 
 * TCP là "stream" (luồng byte liên tục) không có ranh giới message.
 * Để biết message bắt đầu/kết thúc ở đâu, ta dùng "framing":
 * - Gửi 4 bytes độ dài (big-endian) trước
 * - Sau đó gửi N bytes payload (JSON)
 * 
 * Format: [4 bytes length][N bytes payload]
 * 
 * Ví dụ: Message "Hello" (5 bytes)
 * → Gửi: [00 00 00 05][H e l l o]
 */
public class FrameIO {

    /**
     * Giới hạn kích thước frame tối đa (10MB).
     * Chống tấn công gửi length cực lớn làm server hết RAM.
     */
    public static final int MAX_FRAME_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * Gửi một frame (message) qua OutputStream.
     * 
     * @param out OutputStream để gửi
     * @param payload Nội dung message (JSON string)
     * @throws IOException nếu ghi thất bại
     * @throws IllegalArgumentException nếu payload quá lớn
     */
    public static void sendFrame(OutputStream out, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        
        if (bytes.length > MAX_FRAME_SIZE) {
            throw new IllegalArgumentException(
                "Frame size " + bytes.length + " exceeds max " + MAX_FRAME_SIZE);
        }
        
        DataOutputStream dos = new DataOutputStream(out);
        
        // Gửi 4 bytes length (big-endian)
        dos.writeInt(bytes.length);
        
        // Gửi payload
        dos.write(bytes);
        
        // Flush để đảm bảo data được gửi ngay
        dos.flush();
    }

    /**
     * Đọc một frame (message) từ InputStream.
     * 
     * QUAN TRỌNG: Method này xử lý "partial reads" (đọc từng phần).
     * TCP có thể trả về ít bytes hơn yêu cầu, nên phải loop đến khi đủ.
     * 
     * @param in InputStream để đọc
     * @return Nội dung message (JSON string)
     * @throws IOException nếu đọc thất bại hoặc connection đóng
     * @throws IllegalArgumentException nếu frame size không hợp lệ
     */
    public static String readFrame(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        
        // Đọc 4 bytes length (big-endian)
        int length = dis.readInt();
        
        // Validate length
        if (length < 0) {
            throw new IllegalArgumentException("Invalid frame length: " + length);
        }
        if (length > MAX_FRAME_SIZE) {
            throw new IllegalArgumentException(
                "Frame size " + length + " exceeds max " + MAX_FRAME_SIZE);
        }
        if (length == 0) {
            return "";
        }
        
        // Đọc payload với partial read handling
        byte[] buffer = new byte[length];
        int totalRead = 0;
        
        while (totalRead < length) {
            int bytesRead = dis.read(buffer, totalRead, length - totalRead);
            if (bytesRead == -1) {
                throw new IOException(
                    "Connection closed while reading frame. Expected " + length + 
                    " bytes, got " + totalRead);
            }
            totalRead += bytesRead;
        }
        
        return new String(buffer, StandardCharsets.UTF_8);
    }

    /**
     * Gửi raw bytes (dùng cho file transfer).
     * Không có length prefix - caller phải biết trước số bytes cần đọc.
     * 
     * @param out OutputStream
     * @param data byte array
     * @param offset vị trí bắt đầu trong array
     * @param length số bytes cần gửi
     */
    public static void sendRawBytes(OutputStream out, byte[] data, int offset, int length) 
            throws IOException {
        out.write(data, offset, length);
        out.flush();
    }

    /**
     * Đọc chính xác N bytes từ InputStream.
     * Xử lý partial reads - loop đến khi đủ bytes.
     * 
     * @param in InputStream
     * @param buffer buffer để chứa data
     * @param offset vị trí bắt đầu ghi vào buffer
     * @param length số bytes cần đọc
     * @return số bytes đã đọc (luôn = length nếu thành công)
     * @throws IOException nếu connection đóng trước khi đọc đủ
     */
    public static int readExactly(InputStream in, byte[] buffer, int offset, int length) 
            throws IOException {
        int totalRead = 0;
        
        while (totalRead < length) {
            int bytesRead = in.read(buffer, offset + totalRead, length - totalRead);
            if (bytesRead == -1) {
                throw new IOException(
                    "Connection closed. Expected " + length + " bytes, got " + totalRead);
            }
            totalRead += bytesRead;
        }
        
        return totalRead;
    }

    /**
     * Đọc chính xác N bytes vào buffer mới.
     * 
     * @param in InputStream
     * @param length số bytes cần đọc
     * @return byte array chứa data
     */
    public static byte[] readExactly(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        readExactly(in, buffer, 0, length);
        return buffer;
    }
}
