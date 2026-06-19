-- V15: Anti-brute-force — verrouillage de compte après N échecs de connexion (P1.3).
--   * failed_attempts — compteur d'échecs consécutifs (remis à 0 au succès)
--   * locked_until    — verrou temporaire ; tant que NOW() < locked_until, isAccountNonLocked() = false
-- Compatible PostgreSQL (prod) et H2 (dev).

ALTER TABLE users ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until    TIMESTAMP;
