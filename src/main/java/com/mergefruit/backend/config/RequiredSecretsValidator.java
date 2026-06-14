package com.mergefruit.backend.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/*
 Learning Notes

 What: Fails fast at startup if required secrets are missing or too short.
 Why: Empty JWT_SECRET causes a cryptic WeakKeyException deep in the stack trace.
*/
@Component
public class RequiredSecretsValidator {

    @Value("${DB_PASSWORD:}")
    private String dbPassword;

    @Value("${JWT_SECRET:}")
    private String jwtSecret;

    @Value("${ANONYMOUS_USER_PASSWORD:}")
    private String anonymousUserPassword;

    @PostConstruct
    void validate() {
        List<String> errors = new ArrayList<>();

        if (isBlank(dbPassword)) {
            errors.add("DB_PASSWORD is missing");
        }
        if (isBlank(jwtSecret)) {
            errors.add("JWT_SECRET is missing");
        } else if (jwtSecret.length() < 32) {
            errors.add("JWT_SECRET must be at least 32 characters");
        }
        if (isBlank(anonymousUserPassword)) {
            errors.add("ANONYMOUS_USER_PASSWORD is missing");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("""
                    Required secrets are not configured:
                    %s

                    Fix:
                    1. cd backend
                    2. cp .env.example .env
                    3. Fill in DB_PASSWORD, JWT_SECRET (32+ chars), ANONYMOUS_USER_PASSWORD
                    4. Run with dev profile: SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
                    """.formatted(String.join("\n", errors.stream().map(e -> "  - " + e).toList())));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
