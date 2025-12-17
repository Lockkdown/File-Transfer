package com.drivelite.server.handler;

import com.drivelite.common.protocol.Request;
import com.drivelite.common.protocol.Response;
import com.drivelite.server.net.ClientContext;
import com.drivelite.server.net.RequestHandler;

/**
 * Handler cho DOWNLOAD_BEGIN request.
 * Delegate sang DownloadHandler.handleBegin()
 */
public class DownloadBeginHandler implements RequestHandler {

    private final DownloadHandler downloadHandler;

    public DownloadBeginHandler() {
        this.downloadHandler = new DownloadHandler();
    }

    public DownloadBeginHandler(DownloadHandler downloadHandler) {
        this.downloadHandler = downloadHandler;
    }

    @Override
    public Response handle(Request request, ClientContext context) {
        return downloadHandler.handleBegin(request, context);
    }

    /**
     * Lấy DownloadHandler để stream file bytes sau khi client gửi READY.
     */
    public DownloadHandler getDownloadHandler() {
        return downloadHandler;
    }
}
