-- V9 : Colonnes de rendu gpt-image-2 complémentaires
-- (image_size et image_quality existent déjà depuis V8)
--
-- image_format      : format de sortie ('jpeg', 'png', 'webp')   default 'jpeg' (plus rapide)
-- image_compression : compression 0-100 pour jpeg/webp           default 85
-- image_background  : fond ('auto', 'opaque')                    default 'auto'

ALTER TABLE projects ADD COLUMN IF NOT EXISTS image_format      VARCHAR(10) DEFAULT 'jpeg';
ALTER TABLE projects ADD COLUMN IF NOT EXISTS image_compression INTEGER     DEFAULT 85;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS image_background  VARCHAR(15) DEFAULT 'auto';
