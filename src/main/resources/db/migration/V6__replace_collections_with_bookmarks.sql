-- V6: Replace collections with direct bookmarks

CREATE TABLE IF NOT EXISTS bookmarks (
    id         VARCHAR(36) PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL REFERENCES users(id),
    best_id    VARCHAR(36) NOT NULL REFERENCES bests(id),
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT uq_bookmarks_user_best UNIQUE (user_id, best_id)
);

CREATE INDEX IF NOT EXISTS idx_bookmarks_user_id    ON bookmarks (user_id);
CREATE INDEX IF NOT EXISTS idx_bookmarks_created_at ON bookmarks (created_at);

-- Migrate existing collection_cards data into bookmarks (one bookmark per unique user+best pair)
-- Uses WHERE NOT EXISTS for H2 + PostgreSQL compatibility
INSERT INTO bookmarks (id, user_id, best_id, created_at)
SELECT
    cc.id,
    c.user_id,
    cc.best_id,
    CURRENT_TIMESTAMP
FROM collection_cards cc
JOIN collections c ON cc.collection_id = c.id
WHERE NOT EXISTS (
    SELECT 1 FROM bookmarks b
    WHERE b.user_id = c.user_id AND b.best_id = cc.best_id
);

-- Drop old tables (collection_cards first due to FK)
DROP TABLE IF EXISTS collection_cards;
DROP TABLE IF EXISTS collections;
