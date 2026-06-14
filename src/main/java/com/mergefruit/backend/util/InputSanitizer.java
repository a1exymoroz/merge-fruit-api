package com.mergefruit.backend.util;

/*
 Learning Notes

 What: Sanitizes user-provided display names before persistence.
 Why: Defense in depth — even with validation, strip unexpected characters.

 Note: JPA parameterized queries already prevent SQL injection.
       This protects against stored XSS if names are rendered in HTML later.

 Common mistake:
 - Thinking sanitization replaces validation — you need both.
*/
public final class InputSanitizer {

    private InputSanitizer() {}

    public static String sanitizeDisplayName(String name, int maxLength) {
        if (name == null) {
            return "Anonymous";
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "Anonymous";
        }
        String clean = trimmed.replaceAll("[^a-zA-Z0-9 _-]", "");
        if (clean.isEmpty()) {
            return "Anonymous";
        }
        return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
    }
}
