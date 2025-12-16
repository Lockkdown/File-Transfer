package com.drivelite.server.net;

import com.drivelite.common.framing.FrameIO;
import com.drivelite.common.protocol.MessageType;
import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.common.protocol.ResponseCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * RequestDispatcher - Điều phối request đến handler tương ứng.
 * 
 * Nhận JSON request, parse, tìm handler theo MessageType,
 * gọi handler, và gửi response về client.
 */
public class RequestDispatcher {

    private final ObjectMapper objectMapper;
    private final Map<MessageType, RequestHandler> handlers;

    public RequestDispatcher() {
        this.objectMapper = new ObjectMapper();
        this.handlers = new HashMap<>();
    }

    /**
     * Đăng ký handler cho một MessageType.
     * 
     * @param type MessageType cần xử lý
     * @param handler Handler để xử lý request
     */
    public void registerHandler(MessageType type, RequestHandler handler) {
        handlers.put(type, handler);
        System.out.println("[DISPATCHER] Registered handler for: " + type);
    }

    /**
     * Xử lý một request từ client.
     * 
     * @param requestJson JSON string của request
     * @param context Context chứa thông tin client (socket, session...)
     * @return Response object
     */
    public Response dispatch(String requestJson, ClientContext context) {
        try {
            // Parse JSON thành Request object
            Request request = objectMapper.readValue(requestJson, Request.class);
            
            // Log request (không log password)
            System.out.println("[DISPATCHER] Request: type=" + request.getType() + 
                             ", requestId=" + request.getRequestId());

            // Tìm handler
            MessageType type = request.getType();
            if (type == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, "Missing message type");
            }

            RequestHandler handler = handlers.get(type);
            if (handler == null) {
                return Response.error(ResponseCode.VALIDATION_ERROR, 
                    "Unknown message type: " + type);
            }

            // Gọi handler
            return handler.handle(request, context);

        } catch (Exception e) {
            System.err.println("[DISPATCHER] Error processing request: " + e.getMessage());
            return Response.error(ResponseCode.SERVER_ERROR, 
                "Error processing request: " + e.getMessage());
        }
    }

    /**
     * Đọc request, dispatch, và gửi response.
     * Đây là method chính được gọi từ ClientHandler.
     * 
     * @param in InputStream từ client socket
     * @param out OutputStream đến client socket
     * @param context Client context
     * @return true nếu nên tiếp tục đọc request, false nếu nên đóng connection
     */
    public boolean processRequest(InputStream in, OutputStream out, ClientContext context) {
        try {
            // Đọc frame từ client
            String requestJson = FrameIO.readFrame(in);
            
            // Dispatch và lấy response
            Response response = dispatch(requestJson, context);
            
            // Serialize response thành JSON
            String responseJson = objectMapper.writeValueAsString(response);
            
            // Gửi response về client
            FrameIO.sendFrame(out, responseJson);
            
            // Log response
            System.out.println("[DISPATCHER] Response: ok=" + response.isOk() + 
                             ", code=" + response.getCode());

            return true;

        } catch (java.io.EOFException e) {
            // Client đóng connection gracefully
            System.out.println("[DISPATCHER] Client disconnected (EOF)");
            return false;
            
        } catch (java.net.SocketException e) {
            // Connection reset
            System.out.println("[DISPATCHER] Client disconnected: " + e.getMessage());
            return false;
            
        } catch (Exception e) {
            System.err.println("[DISPATCHER] Error: " + e.getMessage());
            try {
                Response errorResponse = Response.error(ResponseCode.SERVER_ERROR, e.getMessage());
                String errorJson = objectMapper.writeValueAsString(errorResponse);
                FrameIO.sendFrame(out, errorJson);
            } catch (Exception ignored) {
                // Không thể gửi error response, bỏ qua
            }
            return false;
        }
    }

    /**
     * Lấy ObjectMapper để các handler có thể dùng.
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
