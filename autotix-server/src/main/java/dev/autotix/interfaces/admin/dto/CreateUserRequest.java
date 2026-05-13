package dev.autotix.interfaces.admin.dto;

/**
 * TODO: Payload for admin creating a new user.
 *  password is plaintext on the wire — TLS in prod is mandatory.
 */
public class CreateUserRequest {
    public String email;
    public String displayName;
    public String password;
    public String role;            // ADMIN / AGENT / VIEWER
}
