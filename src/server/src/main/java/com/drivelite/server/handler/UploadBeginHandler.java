package com.drivelite.server.handler;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.net.RequestHandler;

/**
 * Handler cho UPLOAD_BEGIN request.
 * Delegate sang UploadHandler.handleBegin()
 */
public class UploadBeginHandler implements RequestHandler {

    private final UploadHandler uploadHandler;

    public UploadBeginHandler() {
        this.uploadHandler = new UploadHandler();
    }

    public UploadBeginHandler(UploadHandler uploadHandler) {
        this.uploadHandler = uploadHandler;
    }

    @Override
    public Response handle(Request request, ClientContext context) {
        return uploadHandler.handleBegin(request, context);
    }

    /**
     * Lấy UploadHandler để xử lý file bytes sau khi READY được gửi.
     */
    public UploadHandler getUploadHandler() {
        return uploadHandler;
    }
}
