package com.drivelite.common.protocol;

/**
 * Enum định nghĩa tất cả các loại message trong protocol.
 */
public enum MessageType {
    // Auth
    REGISTER,
    LOGIN,
    LOGOUT,
    FORGOT_PASSWORD,
    RESET_PASSWORD,

    // Files
    LIST_MY_FILES,
    LIST_SHARED_WITH_ME,
    UPLOAD_BEGIN,
    UPLOAD_NEW_VERSION_BEGIN,
    DOWNLOAD_BEGIN,
    DOWNLOAD_VERSION,
    DELETE_FILE,
    RENAME_FILE,

    // Sharing
    SHARE_ADD,
    SHARE_UPDATE,
    SHARE_REMOVE,
    LIST_SHARES_OF_FILE,

    // Versioning
    GET_VERSIONS,

    // Control (internal)
    PING,
    READY,
    FILE_META
}
