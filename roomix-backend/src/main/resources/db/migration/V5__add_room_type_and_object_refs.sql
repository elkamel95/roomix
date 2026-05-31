-- Type de pièce sélectionné par l'utilisateur (override de la détection automatique)
ALTER TABLE projects ADD COLUMN IF NOT EXISTS room_type    VARCHAR(40);

-- Objets de référence : JSON array [{title, imageKey, imageUrl}]
ALTER TABLE projects ADD COLUMN IF NOT EXISTS object_refs  JSONB;
