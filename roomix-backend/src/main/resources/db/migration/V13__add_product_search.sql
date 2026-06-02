ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS product_search_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS preferred_brands       JSONB;
