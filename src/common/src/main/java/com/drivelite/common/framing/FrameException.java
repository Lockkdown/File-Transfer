package com.drivelite.common.framing;

import java.io.IOException;

/**
 * Exception cho các lỗi liên quan đến framing protocol.
 */
public class FrameException extends IOException {
    
    public FrameException(String message) {
        super(message);
    }
    
    public FrameException(String message, Throwable cause) {
        super(message, cause);
    }
}
