package com.mergefruit.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitScoreRequest(
        @Size(max = 20) String name,
        @NotNull @Min(0) @Max(1_000_000) Integer score
) {}
