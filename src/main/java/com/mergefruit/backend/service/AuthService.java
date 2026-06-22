package com.mergefruit.backend.service;

import com.mergefruit.backend.dto.AuthResponse;
import com.mergefruit.backend.dto.LoginRequest;
import com.mergefruit.backend.dto.SignUpRequest;
import com.mergefruit.backend.entity.EmailVerificationToken;
import com.mergefruit.backend.entity.Role;
import com.mergefruit.backend.entity.User;
import com.mergefruit.backend.exception.ApiException;
import com.mergefruit.backend.repository.EmailVerificationTokenRepository;
import com.mergefruit.backend.repository.UserRepository;
import com.mergefruit.backend.security.JwtService;
import com.mergefruit.backend.security.UserPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 Learning Notes

 What: Business logic for authentication — signup and login.
 Why: Controllers should be thin; services hold rules (duplicate email checks, hashing).

 Implemented for you: signUp(), login(), verifyEmail()

 TODO (Student) — implement in this class or a new TokenService:
 - logout() — invalidate token (hint: maintain a deny-list of jti claims in Redis/DB)
 - refreshToken() — issue new access token from a long-lived refresh token
 - requestPasswordReset() / resetPassword() — email flow with expiring reset tokens

 Common mistake:
 - Storing plaintext passwords — always use passwordEncoder.encode().
 - Reusing JWT as email verification token — use a separate random token in the DB.
*/
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final int verificationTokenExpirationHours;

    public AuthService(
            UserRepository userRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            EmailService emailService,
            @Value("${app.auth.verification-token-expiration-hours:24}") int verificationTokenExpirationHours) {
        this.userRepository = userRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.verificationTokenExpirationHours = verificationTokenExpirationHours;
    }

    @Transactional
    public AuthResponse signUp(SignUpRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setRole(Role.USER);
        user.setEmailVerified(false);

        userRepository.save(user);

        EmailVerificationToken verificationToken = createVerificationToken(user);
        emailService.sendVerificationEmail(
                user.getEmail(), verificationToken.getToken(), verificationToken.getCode());

        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtService.generateToken(principal);

        return AuthResponse.of(
                accessToken,
                jwtService.getExpirationMs(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getEmailVerified());
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(principal);

        User user = userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (Boolean.FALSE.equals(user.getEmailVerified())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email not verified");
        }

        return AuthResponse.of(
                token,
                jwtService.getExpirationMs(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getEmailVerified());
    }

    @Transactional
    public AuthResponse verifyEmail(String token, String code) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid verification token"));

        if (!verificationToken.getCode().equals(code.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid verification code");
        }

        if (verificationToken.getExpiresAt().isBefore(Instant.now())) {
            emailVerificationTokenRepository.delete(verificationToken);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verification token has expired");
        }

        User user = verificationToken.getUser();
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            emailVerificationTokenRepository.deleteByUser_Id(user.getId());
            return buildAuthResponse(user);
        }

        user.setEmailVerified(true);
        userRepository.save(user);
        emailVerificationTokenRepository.deleteByUser_Id(user.getId());

        return buildAuthResponse(user);
    }

    private EmailVerificationToken createVerificationToken(User user) {
        emailVerificationTokenRepository.deleteByUser_Id(user.getId());

        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUser(user);
        verificationToken.setToken(UUID.randomUUID().toString().replace("-", ""));
        verificationToken.setCode(String.format("%04d", ThreadLocalRandom.current().nextInt(10_000)));
        verificationToken.setExpiresAt(
                Instant.now().plus(verificationTokenExpirationHours, ChronoUnit.HOURS));

        return emailVerificationTokenRepository.save(verificationToken);
    }

    private AuthResponse buildAuthResponse(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtService.generateToken(principal);
        return AuthResponse.of(
                accessToken,
                jwtService.getExpirationMs(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getEmailVerified());
    }

    // TODO (Student): Implement logout
    // Hint: With stateless JWT, "logout" means the client deletes the token.
    //       For server-side logout, store revoked token IDs until they expire.

    // TODO (Student): Implement refreshToken(String refreshToken)
    // Hint: Validate refresh token separately (longer expiry, stored in httpOnly cookie).

    // TODO (Student): Implement password reset flow
    // Hint: POST /api/auth/forgot-password → generate token → email link
    //       POST /api/auth/reset-password → validate token → update password
}
