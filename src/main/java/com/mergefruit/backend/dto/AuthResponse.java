package com.mergefruit.backend.dto;

/*
 Learning Notes

 What: Outgoing JSON after successful login/signup.
 Why: Never return the User entity — only safe, intentional fields.

 Note: refreshToken is intentionally absent until YOU implement refresh tokens.
*/
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        String email,
        String displayName,
        String role,
        Boolean emailVerified
) {
    public static AuthResponse of(String accessToken, long expiresInMs, String email, String displayName, String role, Boolean emailVerified) {
        return new AuthResponse(accessToken, "Bearer", expiresInMs, email, displayName, role, emailVerified);
    }
}
