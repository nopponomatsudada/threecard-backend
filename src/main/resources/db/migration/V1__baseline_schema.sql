-- Baseline schema as it existed before the device-secret rollout (BE-1..BE-7).
-- Existing production databases get baselined to this version via
-- baselineOnMigrate=true; fresh databases get this migration applied directly.

CREATE TABLE IF NOT EXISTS users (
    id              VARCHAR(36)  PRIMARY KEY,
    device_id       VARCHAR(255) NOT NULL,
    display_id      VARCHAR(10)  NOT NULL,
    plan            VARCHAR(10)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT users_device_id_key  UNIQUE (device_id),
    CONSTRAINT users_display_id_key UNIQUE (display_id)
);

CREATE TABLE IF NOT EXISTS themes (
    id          VARCHAR(36)  PRIMARY KEY,
    title       VARCHAR(100) NOT NULL,
    description VARCHAR(140),
    tag_id      VARCHAR(50)  NOT NULL,
    author_id   VARCHAR(36)  NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_themes_tag_id     ON themes (tag_id);
CREATE INDEX IF NOT EXISTS idx_themes_created_at ON themes (created_at);
CREATE INDEX IF NOT EXISTS idx_themes_tag_created ON themes (tag_id, created_at);

CREATE TABLE IF NOT EXISTS bests (
    id         VARCHAR(36) PRIMARY KEY,
    theme_id   VARCHAR(36) NOT NULL REFERENCES themes(id),
    author_id  VARCHAR(36) NOT NULL REFERENCES users(id),
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT uq_bests_theme_author UNIQUE (theme_id, author_id)
);
CREATE INDEX IF NOT EXISTS idx_bests_theme_id   ON bests (theme_id);
CREATE INDEX IF NOT EXISTS idx_bests_created_at ON bests (created_at);

CREATE TABLE IF NOT EXISTS best_items (
    id          VARCHAR(36)  PRIMARY KEY,
    best_id     VARCHAR(36)  NOT NULL REFERENCES bests(id),
    rank        INTEGER      NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(140),
    CONSTRAINT uq_best_items_best_rank UNIQUE (best_id, rank)
);
CREATE INDEX IF NOT EXISTS idx_best_items_best_id ON best_items (best_id);

CREATE TABLE IF NOT EXISTS collections (
    id         VARCHAR(36)  PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL REFERENCES users(id),
    title      VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_collections_user_id    ON collections (user_id);
CREATE INDEX IF NOT EXISTS idx_collections_created_at ON collections (created_at);

CREATE TABLE IF NOT EXISTS collection_cards (
    id            VARCHAR(36) PRIMARY KEY,
    collection_id VARCHAR(36) NOT NULL REFERENCES collections(id),
    best_id       VARCHAR(36) NOT NULL REFERENCES bests(id),
    CONSTRAINT uq_collection_cards_collection_best UNIQUE (collection_id, best_id)
);
CREATE INDEX IF NOT EXISTS idx_collection_cards_collection_id ON collection_cards (collection_id);
