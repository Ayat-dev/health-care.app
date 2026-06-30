-- V32 (D1c) : métadonnées d'appareil sur les refresh tokens.
-- Permet à la vue admin « sessions actives » d'identifier chaque session (navigateur /
-- client desktop) et de la révoquer ciblément. Un appareil = sa chaîne de rotation ; les
-- métadonnées sont reportées à chaque rotation, donc le jeton actif courant porte l'identité.
--
-- Toutes NULLABLE : les jetons existants (et ceux émis sans requête HTTP) restent à NULL.
-- Compatible PostgreSQL (prod) ET H2 (dev/test).
ALTER TABLE refresh_tokens ADD COLUMN user_agent  VARCHAR(256);
ALTER TABLE refresh_tokens ADD COLUMN ip_address  VARCHAR(45);
ALTER TABLE refresh_tokens ADD COLUMN last_used_at TIMESTAMP;
