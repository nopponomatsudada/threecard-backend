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
INSERT INTO bookmarks (id, user_id, best_id, created_at)
SELECT
    collection_cards.id,
    collections.user_id,
    collection_cards.best_id,
    CURRENT_TIMESTAMP
FROM collection_cards
JOIN collections ON collection_cards.collection_id = collections.id
ON CONFLICT (user_id, best_id) DO NOTHING;

-- Drop old tables (collection_cards first due to FK)
DROP TABLE IF EXISTS collection_cards;
DROP TABLE IF EXISTS collections;
