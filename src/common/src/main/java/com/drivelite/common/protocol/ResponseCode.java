package com.drivelite.common.protocol;

/**
 * Enum định nghĩa các mã response code chuẩn.
 */
public enum ResponseCode {
    OK,                 // 200 - Success
    VALIDATION_ERROR,   // 400 - Invalid input
    UNAUTHORIZED,       // 401 - Missing/invalid/expired token
    FORBIDDEN,          // 403 - No permission
    NOT_FOUND,          // 404 - Resource not found
    CONFLICT,           // 409 - Already exists
    SERVER_ERROR        // 500 - Internal error
}
