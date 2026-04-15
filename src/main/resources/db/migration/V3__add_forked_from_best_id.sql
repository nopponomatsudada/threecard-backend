-- Best に fork 元を記録するカラムを追加
ALTER TABLE bests ADD COLUMN forked_from_best_id VARCHAR(36) REFERENCES bests(id);
CREATE INDEX IF NOT EXISTS idx_bests_forked_from ON bests (forked_from_best_id);
