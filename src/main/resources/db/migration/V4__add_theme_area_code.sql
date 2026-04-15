-- Theme にエリアコードを追加（47都道府県 + その他）
ALTER TABLE themes ADD COLUMN area_code VARCHAR(10);
CREATE INDEX IF NOT EXISTS idx_themes_area_code ON themes (area_code);
CREATE INDEX IF NOT EXISTS idx_themes_tag_area_created ON themes (tag_id, area_code, created_at);
