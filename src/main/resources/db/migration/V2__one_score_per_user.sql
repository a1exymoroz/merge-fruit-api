-- One leaderboard row per user. Keep each user's highest score before enforcing uniqueness.

DELETE FROM scores s1
USING scores s2
WHERE s1.user_id = s2.user_id
  AND (s1.points < s2.points OR (s1.points = s2.points AND s1.id > s2.id));

ALTER TABLE scores ADD CONSTRAINT uq_scores_user_id UNIQUE (user_id);
