-- 4-digit code entered on the frontend verify page.

ALTER TABLE email_verification_tokens
    ADD COLUMN code VARCHAR(4) NOT NULL DEFAULT '0000';

ALTER TABLE email_verification_tokens
    ALTER COLUMN code DROP DEFAULT;
