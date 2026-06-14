package com.mergefruit.backend.service;

import com.mergefruit.backend.dto.PageResponse;
import com.mergefruit.backend.dto.ScoreResponse;
import com.mergefruit.backend.dto.SubmitScoreRequest;
import com.mergefruit.backend.dto.SubmitScoreResponse;
import com.mergefruit.backend.dto.UpdateScoreRequest;
import com.mergefruit.backend.entity.Score;
import com.mergefruit.backend.entity.User;
import com.mergefruit.backend.exception.ApiException;
import com.mergefruit.backend.repository.ScoreRepository;
import com.mergefruit.backend.repository.UserRepository;
import com.mergefruit.backend.security.UserPrincipal;
import com.mergefruit.backend.util.InputSanitizer;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/*
 Learning Notes

 What: Business logic for leaderboard scores.
 Why: Keeps controllers thin; encapsulates ranking and sanitization rules.

 Implemented for you: getLeaderboard(), submitScore()

 TODO (Student): Implement deleteScore() and updateScore()
*/
@Service
public class ScoreService {

    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final int maxDisplayNameLength;
    private final int maxLeaderboardSize;
    private final String anonymousUserPassword;

    public ScoreService(
            ScoreRepository scoreRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.scores.max-display-name-length}") int maxDisplayNameLength,
            @Value("${app.scores.max-leaderboard-size}") int maxLeaderboardSize,
            @Value("${app.anonymous-user.password}") String anonymousUserPassword) {
        this.scoreRepository = scoreRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.maxDisplayNameLength = maxDisplayNameLength;
        this.maxLeaderboardSize = maxLeaderboardSize;
        this.anonymousUserPassword = anonymousUserPassword;
    }

    @Transactional(readOnly = true)
    public PageResponse<ScoreResponse> getLeaderboard(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), maxLeaderboardSize);
        int safePage = Math.max(page, 0);

        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Score> scores = scoreRepository.findLeaderboard(pageable);

        Page<ScoreResponse> mapped = scores.map(ScoreResponse::from);
        return PageResponse.from(mapped);
    }

    @Transactional
    public SubmitScoreResponse submitScore(SubmitScoreRequest request) {
        String displayName = InputSanitizer.sanitizeDisplayName(request.name(), maxDisplayNameLength);
        User owner = resolveScoreOwner();

        Score score = new Score();
        score.setUser(owner);
        score.setDisplayName(displayName);
        score.setPoints(request.score());
        scoreRepository.save(score);

        int rank = (int) scoreRepository.countByPointsGreaterThan(request.score()) + 1;

        Page<Score> topScores = scoreRepository.findLeaderboard(PageRequest.of(0, 10));
        List<ScoreResponse> leaderboard = topScores.map(ScoreResponse::from).getContent();

        return new SubmitScoreResponse(true, rank, leaderboard);
    }

    // TODO (Student): Implement deleteScore(Long id, UserPrincipal principal)
    // Steps:
    // 1. Find score by id or throw 404
    // 2. Check ownership: score.getUser().getId().equals(principal.getId()) OR principal has ROLE_ADMIN
    // 3. scoreRepository.delete(score)
    // Hint: Follow submitScore() pattern for transaction boundaries.

    public void deleteScore(Long id, UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        Score score = scoreRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Score not found"));

        boolean isOwner = score.getUser().getId().equals(principal.getId());

        if (!isAdmin && !isOwner) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not allowed to delete this score");
        }
        scoreRepository.delete(score);
    }

    // TODO (Student): Implement updateScore(Long id, UpdateScoreRequest request, UserPrincipal principal)
    // Steps:
    // 1. Find score, verify ownership (same as delete)
    // 2. If request.name() != null, sanitize and set display name
    // 3. If request.score() != null, validate and update points
    // 4. Return ScoreResponse.from(saved)

    private User resolveScoreOwner() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        }

        return getOrCreateAnonymousUser();
    }

    private User getOrCreateAnonymousUser() {
        return userRepository.findByEmailIgnoreCase("anonymous@mergefruit.local")
                .orElseGet(() -> {
                    User anonymous = new User();
                    anonymous.setEmail("anonymous@mergefruit.local");
                    anonymous.setPassword(passwordEncoder.encode(anonymousUserPassword));
                    anonymous.setDisplayName("Anonymous");
                    return userRepository.save(anonymous);
                });
    }
}
