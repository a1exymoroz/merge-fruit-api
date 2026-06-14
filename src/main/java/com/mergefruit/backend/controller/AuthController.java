package com.mergefruit.backend.controller;

import com.mergefruit.backend.dto.AuthResponse;
import com.mergefruit.backend.dto.LoginRequest;
import com.mergefruit.backend.dto.SignUpRequest;
import com.mergefruit.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/*
 Learning Notes

 What: REST controller for authentication endpoints.
 Why: Maps HTTP verbs + paths to service methods; returns JSON DTOs.

 Implemented for you:
 - POST /api/auth/signup
 - POST /api/auth/login

 TODO (Student): Add these endpoints (hints in AuthService):
 - POST /api/auth/logout
 - POST /api/auth/refresh
 - POST /api/auth/forgot-password
 - POST /api/auth/reset-password

 Try yourself:
 - Add @GetMapping("/me") returning the current user's profile (requires JWT).

 Common mistake:
 - Putting business logic in the controller instead of the service.
*/
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return authService.signUp(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // TODO (Student):
    // @PostMapping("/logout")
    // public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) { ... }

    // TODO (Student):
    // @PostMapping("/refresh")
    // public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) { ... }

    // TODO (Student):
    // Implement forgot-password and reset-password endpoints.
    // Remember: never reveal whether an email exists (return 202 either way).
}
