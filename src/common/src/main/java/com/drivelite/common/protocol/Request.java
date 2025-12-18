package com.drivelite.common.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Request envelope - cấu trúc chung cho mọi request từ Client → Server.
 */
public class Request {
    @JsonProperty("type")
    private MessageType type;

    @JsonProperty("sessionToken")
    private String sessionToken;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("data")
    private Object data;

    public Request() {}

    public Request(MessageType type, String sessionToken, String requestId, Object data) {
        this.type = type;
        this.sessionToken = sessionToken;
        this.requestId = requestId;
        this.data = data;
    }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    /**
     * Serialize request to JSON string.
     */
    public String toJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(this);
    }

    /**
     * Deserialize request from JSON string.
     */
    public static Request fromJson(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, Request.class);
    }
}
