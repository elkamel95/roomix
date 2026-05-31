-- V6 : Ajout du champ custom_note pour les instructions libres de l'utilisateur
ALTER TABLE projects ADD COLUMN IF NOT EXISTS custom_note TEXT;
