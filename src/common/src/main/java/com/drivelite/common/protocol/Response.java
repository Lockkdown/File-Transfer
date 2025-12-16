package com.drivelite.common.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response envelope - cấu trúc chung cho mọi response từ Server → Client.
 * Format: { ok: boolean, code: string, message: string, data?: object }
 */
public class Response {
    @JsonProperty("ok")
    private boolean ok;

    @JsonProperty("code")
    private ResponseCode code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private Object data;

    public Response() {}

    public Response(boolean ok, ResponseCode code, String message, Object data) {
        this.ok = ok;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // Factory methods for common responses
    public static Response success(Object data) {
        return new Response(true, ResponseCode.OK, "Success", data);
    }

    public static Response success(String message, Object data) {
        return new Response(true, ResponseCode.OK, message, data);
    }

    public static Response error(ResponseCode code, String message) {
        return new Response(false, code, message, null);
    }

    // Getters and setters
    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }

    public ResponseCode getCode() { return code; }
    public void setCode(ResponseCode code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
