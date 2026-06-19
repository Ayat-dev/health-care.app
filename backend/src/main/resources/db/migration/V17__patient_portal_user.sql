-- V17: Portail patient (P2.4) — lie un compte utilisateur (rôle PATIENT) à son dossier patient.
--   * user_id — FK nullable + unique vers users(id) ; un dossier patient = au plus un compte portail.
-- Compatible PostgreSQL (prod) et H2 (dev).

ALTER TABLE patients ADD COLUMN user_id BIGINT;

ALTER TABLE patients
    ADD CONSTRAINT fk_patients_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE patients
    ADD CONSTRAINT uq_patients_user UNIQUE (user_id);
