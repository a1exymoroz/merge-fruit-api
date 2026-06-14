package com.mergefruit.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/*
 Learning Notes

 What: Incoming JSON for registration — validated before reaching the service layer.
 Why: DTOs decouple API contract from DB schema; @Valid triggers Bean Validation.

 Try yourself:
 - Add @Pattern for password strength (uppercase, digit, etc.).

 Common mistake:
 - Trusting client input without validation — always validate at the boundary.
*/
public record SignUpRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 2, max = 50) String displayName
) {}
