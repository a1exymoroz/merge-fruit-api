-- Applied automatically by Flyway in production (SPRING_PROFILES_ACTIVE=prod).
-- Indexes support fast leaderboard queries ordered by score.

CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS scores (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(20) NOT NULL,
    points      INTEGER NOT NULL CHECK (points >= 0 AND points <= 1000000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scores_points_desc ON scores (points DESC, created_at ASC);
CREATE INDEX idx_scores_user_id ON scores (user_id);
