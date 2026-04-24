-- V7: Change bookmarks from best-level to best_item-level without dropping existing data.
-- Existing best-level bookmarks are migrated to the rank=1 item of each best as the closest
-- item-level equivalent while preserving user bookmark counts.

CREATE TABLE bookmarks_new (
    id            VARCHAR(36) PRIMARY KEY,
    user_id       VARCHAR(36) NOT NULL REFERENCES users(id),
    best_item_id  VARCHAR(36) NOT NULL REFERENCES best_items(id),
    created_at    TIMESTAMP   NOT NULL,
    CONSTRAINT uq_bookmarks_user_best_item UNIQUE (user_id, best_item_id)
);

INSERT INTO bookmarks_new (id, user_id, best_item_id, created_at)
SELECT
    b.id,
    b.user_id,
    bi.id,
    b.created_at
FROM bookmarks b
JOIN best_items bi
    ON bi.best_id = b.best_id
   AND bi.rank = 1
WHERE NOT EXISTS (
    SELECT 1 FROM bookmarks_new bn
    WHERE bn.user_id = b.user_id AND bn.best_item_id = bi.id
);

DROP TABLE bookmarks;

ALTER TABLE bookmarks_new RENAME TO bookmarks;

CREATE INDEX IF NOT EXISTS idx_bookmarks_user_id    ON bookmarks (user_id);
CREATE INDEX IF NOT EXISTS idx_bookmarks_created_at ON bookmarks (created_at);
