-- Best に fork 元を記録するカラムを追加
ALTER TABLE bests ADD COLUMN forked_from_best_id VARCHAR(36) REFERENCES bests(id);
