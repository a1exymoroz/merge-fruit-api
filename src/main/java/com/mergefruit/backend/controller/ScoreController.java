package com.mergefruit.backend.controller;

import com.mergefruit.backend.dto.PageResponse;
import com.mergefruit.backend.dto.ScoreResponse;
import com.mergefruit.backend.dto.SubmitScoreRequest;
import com.mergefruit.backend.dto.SubmitScoreResponse;
import com.mergefruit.backend.dto.UpdateScoreRequest;
import com.mergefruit.backend.security.UserPrincipal;
import com.mergefruit.backend.service.ScoreService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/*
 Learning Notes

 What: REST controller for leaderboard scores — mirrors your React app's Netlify function.
 Why: This is the API your frontend can call when VITE_API_URL points to this backend.

 Implemented for you (study these as reference):
 - GET  /api/scores?page=0&size=10
 - POST /api/scores  { "name": "Player", "score": 1234 }

 YOUR exercises:
 - DELETE /api/scores/{id}
 - PUT    /api/scores/{id}

 Try yourself:
 - Add @PreAuthorize("hasRole('ADMIN')") on delete for admin-only deletion.
 - Return 201 Created with Location header on POST.

 Common mistake:
 - Returning entities directly instead of DTOs.
 - Forgetting @Valid on @RequestBody.
*/
@RestController
@RequestMapping("/api/scores")
@Validated
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @GetMapping
    public PageResponse<ScoreResponse> getLeaderboard(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return scoreService.getLeaderboard(page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitScoreResponse submitScore(@Valid @RequestBody SubmitScoreRequest request) {
        return scoreService.submitScore(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScore(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        scoreService.deleteScore(id, principal);
    }

    @PutMapping("/{id}")
    public ScoreResponse updateScore(
            @PathVariable Long id,
            @Valid @RequestBody UpdateScoreRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return scoreService.updateScore(id, request, principal);
    }
}