package com.mergefruit.backend.controller;

import com.mergefruit.backend.service.EmailService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 Dev-only: remove or disable before production hardening.
 Test: curl -X POST "http://localhost:8080/api/dev/test-email"
*/
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class DevMailController {

    private final EmailService emailService;
    private final String defaultRecipient;

    public DevMailController(
            EmailService emailService,
            @Value("${app.mail.from}") String defaultRecipient) {
        this.emailService = emailService;
        this.defaultRecipient = extractEmail(defaultRecipient);
    }

    @PostMapping("/test-email")
    public Map<String, String> sendTestEmail(
            @RequestParam(required = false) String to) {
        String recipient = (to == null || to.isBlank()) ? defaultRecipient : to.trim();
        emailService.sendPlainText(
                recipient,
                "Merge Fruit — Brevo test",
                "If you read this, Brevo API is configured correctly.");
        return Map.of("status", "sent", "to", recipient);
    }

    private String extractEmail(String fromHeader) {
        int start = fromHeader.indexOf('<');
        int end = fromHeader.indexOf('>');
        if (start >= 0 && end > start) {
            return fromHeader.substring(start + 1, end);
        }
        return fromHeader;
    }
}
