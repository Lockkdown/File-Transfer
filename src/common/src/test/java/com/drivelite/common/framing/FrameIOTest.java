package com.drivelite.common.framing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests cho FrameIO - Length-prefix framing protocol.
 */
class FrameIOTest {

    @Test
    @DisplayName("sendFrame + readFrame: basic round-trip")
    void testBasicRoundTrip() throws IOException {
        String original = "{\"type\":\"LOGIN\",\"data\":{\"email\":\"test@example.com\"}}";
        
        // Gửi frame vào ByteArrayOutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FrameIO.sendFrame(baos, original);
        
        // Đọc frame từ ByteArrayInputStream
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        String received = FrameIO.readFrame(bais);
        
        assertEquals(original, received);
    }

    @Test
    @DisplayName("sendFrame: correct length prefix (big-endian)")
    void testLengthPrefix() throws IOException {
        String payload = "Hello";  // 5 bytes
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FrameIO.sendFrame(baos, payload);
        
        byte[] result = baos.toByteArray();
        
        // Kiểm tra tổng length: 4 (prefix) + 5 (payload) = 9
        assertEquals(9, result.length);
        
        // Kiểm tra 4 bytes đầu là length = 5 (big-endian)
        ByteBuffer bb = ByteBuffer.wrap(result, 0, 4);
        assertEquals(5, bb.getInt());
        
        // Kiểm tra payload
        String payloadPart = new String(result, 4, 5, StandardCharsets.UTF_8);
        assertEquals("Hello", payloadPart);
    }

    @Test
    @DisplayName("readFrame: handles empty payload")
    void testEmptyPayload() throws IOException {
        // Tạo frame với length = 0
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FrameIO.sendFrame(baos, "");
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        String received = FrameIO.readFrame(bais);
        
        assertEquals("", received);
    }

    @Test
    @DisplayName("readFrame: rejects negative length")
    void testNegativeLength() {
        // Tạo frame với length = -1 (invalid)
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(-1);
        
        ByteArrayInputStream bais = new ByteArrayInputStream(bb.array());
        
        assertThrows(IllegalArgumentException.class, () -> {
            FrameIO.readFrame(bais);
        });
    }

    @Test
    @DisplayName("readFrame: rejects oversized frame")
    void testOversizedFrame() {
        // Tạo frame với length > MAX_FRAME_SIZE
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(FrameIO.MAX_FRAME_SIZE + 1);
        
        ByteArrayInputStream bais = new ByteArrayInputStream(bb.array());
        
        assertThrows(IllegalArgumentException.class, () -> {
            FrameIO.readFrame(bais);
        });
    }

    @Test
    @DisplayName("sendFrame: rejects oversized payload")
    void testSendOversizedPayload() {
        // Tạo payload > MAX_FRAME_SIZE
        byte[] largeBytes = new byte[FrameIO.MAX_FRAME_SIZE + 1];
        String largePayload = new String(largeBytes, StandardCharsets.UTF_8);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        assertThrows(IllegalArgumentException.class, () -> {
            FrameIO.sendFrame(baos, largePayload);
        });
    }

    @Test
    @DisplayName("Multiple frames back-to-back")
    void testMultipleFrames() throws IOException {
        String[] messages = {
            "{\"type\":\"LOGIN\"}",
            "{\"type\":\"LIST_FILES\"}",
            "{\"type\":\"LOGOUT\"}"
        };
        
        // Gửi nhiều frames liên tiếp
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (String msg : messages) {
            FrameIO.sendFrame(baos, msg);
        }
        
        // Đọc lại từng frame
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        for (String expected : messages) {
            String received = FrameIO.readFrame(bais);
            assertEquals(expected, received);
        }
    }

    @Test
    @DisplayName("readFrame: throws on incomplete frame (connection closed)")
    void testIncompleteFrame() {
        // Tạo frame header nói length = 100, nhưng chỉ có 10 bytes payload
        ByteBuffer bb = ByteBuffer.allocate(14);  // 4 + 10
        bb.putInt(100);  // Nói sẽ có 100 bytes
        bb.put("0123456789".getBytes(StandardCharsets.UTF_8));  // Chỉ có 10 bytes
        
        ByteArrayInputStream bais = new ByteArrayInputStream(bb.array());
        
        assertThrows(IOException.class, () -> {
            FrameIO.readFrame(bais);
        });
    }

    @Test
    @DisplayName("readExactly: reads exact number of bytes")
    void testReadExactly() throws IOException {
        byte[] data = "Hello World".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        
        byte[] result = FrameIO.readExactly(bais, 5);
        
        assertEquals("Hello", new String(result, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("readExactly: throws on insufficient data")
    void testReadExactlyInsufficient() {
        byte[] data = "Hi".getBytes(StandardCharsets.UTF_8);  // Chỉ có 2 bytes
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        
        assertThrows(IOException.class, () -> {
            FrameIO.readExactly(bais, 10);  // Yêu cầu 10 bytes
        });
    }

    @Test
    @DisplayName("Unicode payload (Vietnamese)")
    void testUnicodePayload() throws IOException {
        String original = "{\"message\":\"Xin chào Việt Nam 🇻🇳\"}";
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FrameIO.sendFrame(baos, original);
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        String received = FrameIO.readFrame(bais);
        
        assertEquals(original, received);
    }

    @Test
    @DisplayName("Large payload (1MB)")
    void testLargePayload() throws IOException {
        // Tạo payload 1MB
        StringBuilder sb = new StringBuilder();
        sb.append("{\"data\":\"");
        for (int i = 0; i < 1024 * 1024; i++) {
            sb.append('A');
        }
        sb.append("\"}");
        String original = sb.toString();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FrameIO.sendFrame(baos, original);
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        String received = FrameIO.readFrame(bais);
        
        assertEquals(original, received);
    }
}
