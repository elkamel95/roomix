ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS search_items_json TEXT;
