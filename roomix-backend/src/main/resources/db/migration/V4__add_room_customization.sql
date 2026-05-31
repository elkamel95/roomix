-- Nouvelles colonnes de personnalisation de la pièce
ALTER TABLE projects ADD COLUMN IF NOT EXISTS floor_material  VARCHAR(80);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS wall_finish      VARCHAR(80);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS table_material   VARCHAR(80);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS accessories      VARCHAR(500);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS keep_existing    BOOLEAN NOT NULL DEFAULT FALSE;
