package com.mergefruit.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/*
 Learning Notes

 What: Sends transactional email via Brevo SMTP (Spring Mail).
 Why: Keeps mail transport out of AuthService — signup, verification, password reset.

 Configure in .env.local / Render:
   MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM, MAIL_ENABLED=true
   FRONTEND_URL — verify link points at the React app, not this API.

 Brevo: the "Login" is MAIL_USERNAME; create an SMTP key in the dashboard for MAIL_PASSWORD.
 MAIL_FROM must be a sender verified in Brevo (your own email works while learning).
*/
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean enabled;
    private final String frontendUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.enabled = enabled;
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
    }

    public void sendPlainText(String to, String subject, String body) {
        if (!enabled) {
            log.info("Mail disabled (MAIL_ENABLED=false). Would send to {}: {}", to, subject);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Sent email to {}", to);
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
}
