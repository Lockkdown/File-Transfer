-- ============================================
-- Drive-lite Database Schema
-- Version: 1.0
-- Database: SQL Server
-- ============================================

-- 1) Users table
-- Lưu thông tin người dùng đã đăng ký
CREATE TABLE Users (
    UserId INT IDENTITY(1,1) PRIMARY KEY,
    Email NVARCHAR(255) NOT NULL UNIQUE,
    PasswordHash NVARCHAR(255) NOT NULL,  -- BCrypt hash, không bao giờ lưu plaintext
    DisplayName NVARCHAR(100) NOT NULL,
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    IsActive BIT DEFAULT 1
);

-- Index để tìm user theo email nhanh hơn
CREATE INDEX IX_Users_Email ON Users(Email);

-- 2) Sessions table
-- Lưu session token sau khi login
CREATE TABLE Sessions (
    SessionId INT IDENTITY(1,1) PRIMARY KEY,
    UserId INT NOT NULL,
    Token NVARCHAR(255) NOT NULL UNIQUE,  -- UUID token
    ExpiresAt DATETIME2 NOT NULL,
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    CONSTRAINT FK_Sessions_Users FOREIGN KEY (UserId) REFERENCES Users(UserId)
);

-- Index để validate token nhanh
CREATE INDEX IX_Sessions_Token ON Sessions(Token);
CREATE INDEX IX_Sessions_UserId ON Sessions(UserId);

-- 3) Files table
-- Metadata của file (không lưu nội dung file)
CREATE TABLE Files (
    FileId INT IDENTITY(1,1) PRIMARY KEY,
    OwnerUserId INT NOT NULL,
    OriginalName NVARCHAR(255) NOT NULL,
    CurrentVersion INT DEFAULT 1,
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    IsDeleted BIT DEFAULT 0,
    CONSTRAINT FK_Files_Owner FOREIGN KEY (OwnerUserId) REFERENCES Users(UserId)
);

CREATE INDEX IX_Files_OwnerUserId ON Files(OwnerUserId);

-- 4) FileVersions table
-- Lưu từng version của file
CREATE TABLE FileVersions (
    VersionId INT IDENTITY(1,1) PRIMARY KEY,
    FileId INT NOT NULL,
    VersionNumber INT NOT NULL,
    StoredPath NVARCHAR(500) NOT NULL,  -- Đường dẫn file trên disk (server-side)
    SizeBytes BIGINT NOT NULL,
    Sha256 NVARCHAR(64) NOT NULL,       -- Checksum để verify file integrity
    UploadedBy INT NOT NULL,
    UploadedAt DATETIME2 DEFAULT GETDATE(),
    Note NVARCHAR(500) NULL,            -- Ghi chú khi upload version mới
    CONSTRAINT FK_FileVersions_File FOREIGN KEY (FileId) REFERENCES Files(FileId),
    CONSTRAINT FK_FileVersions_Uploader FOREIGN KEY (UploadedBy) REFERENCES Users(UserId),
    CONSTRAINT UQ_FileVersions_FileVersion UNIQUE (FileId, VersionNumber)
);

CREATE INDEX IX_FileVersions_FileId ON FileVersions(FileId);

-- 5) FilePermissions table
-- ACL: ai có quyền gì với file nào
-- Permission values: 'OWNER', 'EDIT', 'VIEW'
CREATE TABLE FilePermissions (
    FileId INT NOT NULL,
    UserId INT NOT NULL,
    Permission NVARCHAR(20) NOT NULL CHECK (Permission IN ('OWNER', 'EDIT', 'VIEW')),
    GrantedBy INT NOT NULL,
    GrantedAt DATETIME2 DEFAULT GETDATE(),
    PRIMARY KEY (FileId, UserId),
    CONSTRAINT FK_FilePermissions_File FOREIGN KEY (FileId) REFERENCES Files(FileId),
    CONSTRAINT FK_FilePermissions_User FOREIGN KEY (UserId) REFERENCES Users(UserId),
    CONSTRAINT FK_FilePermissions_Granter FOREIGN KEY (GrantedBy) REFERENCES Users(UserId)
);

CREATE INDEX IX_FilePermissions_UserId ON FilePermissions(UserId);

-- 6) PasswordResetTokens table
-- Token để reset password (forgot password flow)
CREATE TABLE PasswordResetTokens (
    Token NVARCHAR(255) PRIMARY KEY,
    UserId INT NOT NULL,
    ExpiresAt DATETIME2 NOT NULL,
    UsedAt DATETIME2 NULL,  -- NULL = chưa dùng, có giá trị = đã dùng
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    CONSTRAINT FK_PasswordResetTokens_User FOREIGN KEY (UserId) REFERENCES Users(UserId)
);

CREATE INDEX IX_PasswordResetTokens_UserId ON PasswordResetTokens(UserId);

-- 7) AuditLog table
-- Ghi lại mọi hành động quan trọng (upload, download, share, login...)
CREATE TABLE AuditLog (
    AuditId INT IDENTITY(1,1) PRIMARY KEY,
    UserId INT NULL,  -- NULL nếu action không cần login (vd: failed login attempt)
    Action NVARCHAR(50) NOT NULL,  -- LOGIN, LOGOUT, UPLOAD, DOWNLOAD, SHARE, REVOKE, etc.
    FileId INT NULL,
    MetaJson NVARCHAR(MAX) NULL,  -- JSON chứa thông tin bổ sung
    OccurredAt DATETIME2 DEFAULT GETDATE(),
    Status NVARCHAR(20) NOT NULL DEFAULT 'SUCCESS',  -- SUCCESS, FAILED
    ClientIp NVARCHAR(45) NULL,  -- IPv4 hoặc IPv6
    CONSTRAINT FK_AuditLog_User FOREIGN KEY (UserId) REFERENCES Users(UserId),
    CONSTRAINT FK_AuditLog_File FOREIGN KEY (FileId) REFERENCES Files(FileId)
);

CREATE INDEX IX_AuditLog_UserId ON AuditLog(UserId);
CREATE INDEX IX_AuditLog_FileId ON AuditLog(FileId);
CREATE INDEX IX_AuditLog_OccurredAt ON AuditLog(OccurredAt);

-- ============================================
-- Sample data for testing (optional)
-- ============================================
-- Uncomment below to insert test user
-- INSERT INTO Users (Email, PasswordHash, DisplayName) 
-- VALUES ('test@example.com', '$2a$10$...', 'Test User');
