package com.mergefruit.backend.dto;

import java.util.List;

public record SubmitScoreResponse(
        boolean success,
        int rank,
        List<ScoreResponse> leaderboard
) {}
