package com.drivelite.server.net;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;

/**
 * Interface cho các request handler.
 * Mỗi MessageType sẽ có 1 handler implement interface này.
 */
@FunctionalInterface
public interface RequestHandler {
    
    /**
     * Xử lý request và trả về response.
     * 
     * @param request Request từ client
     * @param context Context chứa thông tin client (socket, session, userId...)
     * @return Response để gửi về client
     */
    Response handle(Request request, ClientContext context);
}
