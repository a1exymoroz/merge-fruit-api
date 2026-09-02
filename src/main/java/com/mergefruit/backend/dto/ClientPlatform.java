package com.mergefruit.backend.dto;

/*
 Learning Notes

 What: Which client app started a request — sent as the "X-Client-Platform" header.
 Why: The verification email link differs per platform (web URL vs. app deep link),
      so the API needs to know who called signup. Header, not body, keeps it out of
      SignUpRequest validation and lets native apps set it in one interceptor.

 Unknown or missing header -> WEB, so older clients keep working unchanged.
*/
public enum ClientPlatform {
    WEB,
    ANDROID,
    IOS;

    public static ClientPlatform fromHeader(String value) {
        if (value == null || value.isBlank()) {
            return WEB;
        }
        try {
            return ClientPlatform.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return WEB;
        }
    }
}
