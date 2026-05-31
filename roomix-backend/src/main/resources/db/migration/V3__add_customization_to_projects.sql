-- V3 : Ajout des options de personnalisation (canapé, palette couleurs)
ALTER TABLE projects ADD COLUMN IF NOT EXISTS sofa_color    VARCHAR(80);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS sofa_type     VARCHAR(80);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS sofa_material VARCHAR(80);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS color_palette VARCHAR(120);
