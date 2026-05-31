-- V8 : Paramètres de rendu gpt-image-2 (ChatGPT uniquement)
--
-- image_size        : résolution ('auto', '1024x1024', '1536x1024', '1024x1536',
--                                  '2048x2048', '2048x1152', '3840x2160', '2160x3840')
-- image_quality     : qualité du rendu ('auto', 'low', 'medium', 'high')
-- image_format      : format de sortie ('jpeg', 'png', 'webp')  default 'jpeg' (plus rapide)
-- image_compression : compression 0-100 pour jpeg/webp          default 85
-- image_background  : fond ('auto', 'opaque')  — 'transparent' non supporté sur gpt-image-2

ALTER TABLE projects ADD COLUMN IF NOT EXISTS image_size        VARCHAR(20)  DEFAULT 'auto';
ALTER TABLE projects ADD COLUMN IF NOT EXISTS image_quality     VARCHAR(10)  DEFAULT 'auto';
ALTER TABLE projects ADD COLUMN IF NOT EXISTS image_format      VARCHAR(10)  DEFAULT 'jpeg';
ALTER TABLE projects ADD COLUMN IF NOT EXISTS image_compression INTEGER      DEFAULT 85;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS image_background  VARCHAR(15)  DEFAULT 'auto';
