-- Theme に場所フィルタ用のカラムを追加
ALTER TABLE themes ADD COLUMN location VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_themes_location ON themes (location);
CREATE INDEX IF NOT EXISTS idx_themes_tag_location_created ON themes (tag_id, location, created_at);
