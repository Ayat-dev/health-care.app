-- V25: Étend le discriminant multi-tenant (P4.2) aux tables opérationnelles PHI
-- pharmacie (stock/dispensations) et maternité (dossiers/CPN), qui « fuitaient »
-- entre cliniques (lectures non filtrées par @TenantId).
-- Compatible PostgreSQL (prod) ET H2 (dev/test).
--
-- NB catalogues volontairement NON scopés (partagés, comme lab_test_catalog) :
--   drugs, radiology_exam_catalog. radiology_requests / rooms restent à traiter
--   dans une tranche dédiée (numérotation RAD globale + UNIQUE room_number → contrainte
--   composite, cf. docs/IMPROVEMENT-BACKLOG.md P4.2).
--
-- Schéma 3 temps (robuste même si la table contient déjà des données : on rétro-remplit
-- vers la clinique CENTRALE avant de poser le NOT NULL — en dev/test les tables sont
-- vides à la migration, le rétro-remplissage est alors un no-op).

-- ── Pharmacie ───────────────────────────────────────────────────────────────────
ALTER TABLE stock_items        ADD COLUMN clinic_id BIGINT;
ALTER TABLE dispensations      ADD COLUMN clinic_id BIGINT;
ALTER TABLE dispensation_items ADD COLUMN clinic_id BIGINT;

-- ── Maternité ───────────────────────────────────────────────────────────────────
ALTER TABLE maternity_records  ADD COLUMN clinic_id BIGINT;
ALTER TABLE prenatal_visits    ADD COLUMN clinic_id BIGINT;

-- Rétro-remplissage des lignes existantes éventuelles → clinique CENTRALE.
UPDATE stock_items        SET clinic_id = (SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL;
UPDATE dispensations      SET clinic_id = (SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL;
UPDATE dispensation_items SET clinic_id = (SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL;
UPDATE maternity_records  SET clinic_id = (SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL;
UPDATE prenatal_visits    SET clinic_id = (SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL;

-- Verrou NOT NULL (portable H2 + PostgreSQL).
ALTER TABLE stock_items        ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE dispensations      ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE dispensation_items ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE maternity_records  ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE prenatal_visits    ALTER COLUMN clinic_id SET NOT NULL;

-- Clés étrangères vers le registre des tenants.
ALTER TABLE stock_items        ADD CONSTRAINT fk_stock_items_clinic        FOREIGN KEY (clinic_id) REFERENCES clinics (id);
ALTER TABLE dispensations      ADD CONSTRAINT fk_dispensations_clinic      FOREIGN KEY (clinic_id) REFERENCES clinics (id);
ALTER TABLE dispensation_items ADD CONSTRAINT fk_dispensation_items_clinic FOREIGN KEY (clinic_id) REFERENCES clinics (id);
ALTER TABLE maternity_records  ADD CONSTRAINT fk_maternity_records_clinic  FOREIGN KEY (clinic_id) REFERENCES clinics (id);
ALTER TABLE prenatal_visits    ADD CONSTRAINT fk_prenatal_visits_clinic    FOREIGN KEY (clinic_id) REFERENCES clinics (id);

-- Index de filtrage tenant.
CREATE INDEX idx_stock_items_clinic        ON stock_items (clinic_id);
CREATE INDEX idx_dispensations_clinic      ON dispensations (clinic_id);
CREATE INDEX idx_dispensation_items_clinic ON dispensation_items (clinic_id);
CREATE INDEX idx_maternity_records_clinic  ON maternity_records (clinic_id);
CREATE INDEX idx_prenatal_visits_clinic    ON prenatal_visits (clinic_id);
