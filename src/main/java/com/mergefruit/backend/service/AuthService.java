package com.mergefruit.backend.service;

import com.mergefruit.backend.dto.AuthResponse;
import com.mergefruit.backend.dto.LoginRequest;
import com.mergefruit.backend.dto.SignUpRequest;
import com.mergefruit.backend.entity.Role;
import com.mergefruit.backend.entity.User;
import com.mergefruit.backend.exception.ApiException;
import com.mergefruit.backend.repository.UserRepository;
import com.mergefruit.backend.security.JwtService;
import com.mergefruit.backend.security.UserPrincipal;
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

 Implemented for you: signUp(), login()

 TODO (Student) — implement in this class or a new TokenService:
 - logout() — invalidate token (hint: maintain a deny-list of jti claims in Redis/DB)
 - refreshToken() — issue new access token from a long-lived refresh token
 - requestPasswordReset() / resetPassword() — email flow with expiring reset tokens

 Common mistake:
 - Storing plaintext passwords — always use passwordEncoder.encode().
*/
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
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

        userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateToken(principal);
        return AuthResponse.of(token, jwtService.getExpirationMs(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(principal);

        User user = userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        return AuthResponse.of(token, jwtService.getExpirationMs(), user.getEmail(), user.getDisplayName(), user.getRole().name());
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
