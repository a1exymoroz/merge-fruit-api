package com.mergefruit.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/*
 Learning Notes — YOUR EXERCISE

 What: Request body for PUT /api/scores/{id} (you will implement).
 Hint: Allow updating display name and/or score — at least one field required.
*/
public record UpdateScoreRequest(
        @Size(max = 20) String name,
        @Min(0) @Max(1_000_000) Integer score
) {}
