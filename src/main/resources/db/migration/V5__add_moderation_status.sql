-- Add moderation status to bests and themes.
-- DEFAULT 'approved' ensures existing records are treated as approved.
-- New records will be explicitly set to 'pending' by the application.

ALTER TABLE bests ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'approved';
CREATE INDEX idx_bests_moderation_status ON bests (moderation_status);

ALTER TABLE themes ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'approved';
CREATE INDEX idx_themes_moderation_status ON themes (moderation_status);
