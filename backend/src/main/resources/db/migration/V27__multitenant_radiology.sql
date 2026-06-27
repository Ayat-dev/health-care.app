-- V27: Étend le discriminant multi-tenant (P4.2) à l'imagerie (radiology_requests + items +
-- reports + images), qui fuitaient entre cliniques (lectures non filtrées par @TenantId).
-- Compatible PostgreSQL (prod) ET H2 (dev/test).
--
-- NB numérotation : radiology_requests.request_number reste UNIQUE GLOBAL ; la requête
--   findMaxSequence a été passée en SQL natif (non filtré par @TenantId) → numéros RAD
--   globalement uniques entre cliniques (même choix que lab/factures en P4.2). Aucune
--   contrainte à dropper (pas de DROP non portable H2/PG).
-- NB radiology_exam_catalog reste PARTAGÉ (catalogue, comme lab_test_catalog) → non scopé.
--
-- Schéma 3 temps (robuste même si la table contient déjà des données → rétro-remplissage CENTRALE).

ALTER TABLE radiology_requests      ADD COLUMN clinic_id BIGINT;
ALTER TABLE radiology_request_items ADD COLUMN clinic_id BIGINT;
ALTER TABLE radiology_reports       ADD COLUMN clinic_id BIGINT;
ALTER TABLE radiology_images        ADD COLUMN clinic_id BIGINT;

UPDATE radiology_requests      SET clinic_id = (SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL;
UPDATE radiology_request_items SET clinic_id = (SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL;
UPDATE radiology_reports       SET clinic_id = (SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL;
UPDATE radiology_images        SET clinic_id = (SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL;

ALTER TABLE radiology_requests      ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE radiology_request_items ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE radiology_reports       ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE radiology_images        ALTER COLUMN clinic_id SET NOT NULL;

ALTER TABLE radiology_requests      ADD CONSTRAINT fk_radiology_requests_clinic      FOREIGN KEY (clinic_id) REFERENCES clinics (id);
ALTER TABLE radiology_request_items ADD CONSTRAINT fk_radiology_request_items_clinic FOREIGN KEY (clinic_id) REFERENCES clinics (id);
ALTER TABLE radiology_reports       ADD CONSTRAINT fk_radiology_reports_clinic       FOREIGN KEY (clinic_id) REFERENCES clinics (id);
ALTER TABLE radiology_images        ADD CONSTRAINT fk_radiology_images_clinic        FOREIGN KEY (clinic_id) REFERENCES clinics (id);

CREATE INDEX idx_radiology_requests_clinic      ON radiology_requests (clinic_id);
CREATE INDEX idx_radiology_request_items_clinic ON radiology_request_items (clinic_id);
CREATE INDEX idx_radiology_reports_clinic       ON radiology_reports (clinic_id);
CREATE INDEX idx_radiology_images_clinic        ON radiology_images (clinic_id);
