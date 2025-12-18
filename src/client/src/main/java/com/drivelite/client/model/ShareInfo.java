package com.drivelite.client.model;

/**
 * DTO đại diện cho thông tin share của một file.
 */
public class ShareInfo {
    private int userId;
    private String email;
    private String displayName;
    private String permission;
    private String grantedAt;

    public ShareInfo() {}

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }

    public String getGrantedAt() { return grantedAt; }
    public void setGrantedAt(String grantedAt) { this.grantedAt = grantedAt; }
}
