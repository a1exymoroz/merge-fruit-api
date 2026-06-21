package com.mergefruit.backend.service;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/*
 Learning Notes

 What: Sends transactional email via Brevo.
 Why: Keeps mail transport out of AuthService — signup, verification, password reset.

 Transport:
 - Production (Render): Brevo REST API over HTTPS — Render blocks outbound SMTP port 587.
 - Local dev (optional): SMTP via Spring Mail when BREVO_API_KEY is not set.

 Configure in .env.local / Render:
   BREVO_API_KEY — from Brevo → SMTP & API → API keys (starts with xkeysib-)
   MAIL_FROM, MAIL_ENABLED=true, FRONTEND_URL

 Local-only SMTP fallback (if no API key):
   MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD
*/
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";

    private final JavaMailSender mailSender;
    private final RestClient brevoClient;
    private final String brevoApiKey;
    private final String fromAddress;
    private final boolean enabled;
    private final String frontendUrl;

    public EmailService(
            JavaMailSender mailSender,
            RestClient.Builder restClientBuilder,
            @Value("${app.mail.brevo-api-key:}") String brevoApiKey,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl) {
        this.mailSender = mailSender;
        this.brevoClient = restClientBuilder.build();
        this.brevoApiKey = brevoApiKey == null ? "" : brevoApiKey.trim();
        this.fromAddress = fromAddress;
        this.enabled = enabled;
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
    }

    public void sendPlainText(String to, String subject, String body) {
        if (!enabled) {
            log.info("Mail disabled (MAIL_ENABLED=false). Would send to {}: {}", to, subject);
            return;
        }

        if (!brevoApiKey.isBlank()) {
            sendViaBrevoApi(to, subject, body);
        } else {
            sendViaSmtp(to, subject, body);
        }
    }

    public void sendVerificationEmail(String to, String token, String code) {
        String verifyUrl = frontendUrl + "/verify?token=" + token;
        String subject = "Verify your email";
        String body = """
                Open this link to verify your email:
                %s

                Your verification code is: %s

                Enter the code on the verify page to complete registration.
                """.formatted(verifyUrl, code);
        sendPlainText(to, subject, body);
    }

    private void sendViaBrevoApi(String to, String subject, String body) {
        Sender sender = parseSender(fromAddress);

        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", sender.name(), "email", sender.email()),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", body);

        brevoClient.post()
                .uri(BREVO_SEND_URL)
                .header("api-key", brevoApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        log.info("Sent email to {} via Brevo API", to);
    }

    private void sendViaSmtp(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Sent email to {} via SMTP", to);
    }

    private Sender parseSender(String from) {
        int start = from.indexOf('<');
        int end = from.indexOf('>');
        if (start >= 0 && end > start) {
            String name = from.substring(0, start).trim();
            String email = from.substring(start + 1, end).trim();
            if (name.isBlank()) {
                name = "Merge Fruit";
            }
            return new Sender(name, email);
        }
        return new Sender("Merge Fruit", from.trim());
    }

    private record Sender(String name, String email) {}
}
