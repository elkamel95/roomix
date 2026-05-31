-- V10 : Corriger le type de image_compression : SMALLINT (int2) → INTEGER (int4)
-- Nécessaire pour correspondre au type Java Integer mappé par Hibernate (Types#INTEGER).
ALTER TABLE projects ALTER COLUMN image_compression TYPE INTEGER USING image_compression::INTEGER;
