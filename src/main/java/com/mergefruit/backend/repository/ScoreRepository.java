package com.mergefruit.backend.repository;

import com.mergefruit.backend.entity.Score;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/*
 Learning Notes

 What: Data access for leaderboard scores.
 Why: Custom @Query with JOIN FETCH can prevent N+1 when you need user data.

 Index recommendation (see V1__init.sql):
 - idx_scores_points_desc — speeds up ORDER BY points DESC queries.
*/
public interface ScoreRepository extends JpaRepository<Score, Long> {

    @Query("SELECT s FROM Score s ORDER BY s.points DESC, s.createdAt ASC")
    Page<Score> findLeaderboard(Pageable pageable);

    long countByPointsGreaterThan(int points);

    Optional<Score> findByUser_Id(Long userId);
}
