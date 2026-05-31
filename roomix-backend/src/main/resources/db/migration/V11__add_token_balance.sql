-- V11 : Ajout du solde de tokens utilisateur
-- 1 token = $0.001 — basé sur la grille tarifaire gpt-image-2
-- Solde initial : 200 tokens (≈ 3 générations Medium 1024×1024)
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_balance INTEGER NOT NULL DEFAULT 200;
