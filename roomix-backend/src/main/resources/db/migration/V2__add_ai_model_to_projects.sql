-- Ajout de la colonne ai_model sur la table projects
ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS ai_model VARCHAR(50) NOT NULL DEFAULT 'QWEN';
