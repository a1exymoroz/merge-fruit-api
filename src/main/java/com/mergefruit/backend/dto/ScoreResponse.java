package com.mergefruit.backend.dto;

import com.mergefruit.backend.entity.Score;
import java.time.Instant;

public record ScoreResponse(
        Long id,
        String name,
        int score,
        String timestamp
) {
    public static ScoreResponse from(Score entity) {
        return new ScoreResponse(
                entity.getId(),
                entity.getDisplayName(),
                entity.getPoints(),
                entity.getCreatedAt().toString()
        );
    }
}
