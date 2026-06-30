-- V31 (B4) : clé d'idempotence pour le rejeu des RDV créés hors-ligne (PWA).
-- Le client génère un UUID par RDV mis en file (IndexedDB chiffrée) ; au retour réseau il
-- rejoue la création. request_key UNIQUE rend ce rejeu idempotent : un 2ᵉ POST avec la même
-- clé retombe sur le RDV déjà créé au lieu d'en dupliquer un.
--
-- Colonne NULLABLE : seuls les RDV nés hors-ligne portent une clé ; les créations en ligne
-- (formulaire web classique) restent à NULL. PostgreSQL ET H2 autorisent plusieurs NULL dans une
-- contrainte UNIQUE → une simple UNIQUE (sans index partiel, non portable) suffit et reste portable.
ALTER TABLE appointments ADD COLUMN request_key VARCHAR(36);
ALTER TABLE appointments ADD CONSTRAINT uq_appointments_request_key UNIQUE (request_key);
