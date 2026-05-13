package dev.autotix.interfaces.auth.dto;

/**
 * TODO: Token pair + user info for login response.
 */
public class LoginResponse {
    public String accessToken;
    public String refreshToken;
    public long accessExpiresAt;
    public long refreshExpiresAt;
    public UserInfo user;

    public static class UserInfo {
        public String id;
        public String email;
        public String displayName;
        public String role;     // ADMIN / AGENT / VIEWER
    }
}
