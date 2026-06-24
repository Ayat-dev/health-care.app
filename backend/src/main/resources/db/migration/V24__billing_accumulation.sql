-- P5.1 Lot A — Auto-facturation : la facture « ouverte » accumule les actes du patient.
--
-- `open` = TRUE → facture accumulatrice (au plus une par patient/clinique) ; passe à FALSE
-- dès le 1er encaissement (ou à l'annulation). `source_type`/`source_id` tracent l'acte
-- d'origine d'une ligne pour l'idempotence (un acte ne se facture jamais deux fois).

ALTER TABLE invoices ADD COLUMN open BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE invoice_items ADD COLUMN source_type VARCHAR(20); -- CONSULTATION|LAB|RADIOLOGY|HOSPITALIZATION|DISPENSATION
ALTER TABLE invoice_items ADD COLUMN source_id   BIGINT;

CREATE INDEX idx_invoice_items_source ON invoice_items (source_type, source_id);

-- Les factures déjà existantes (manuelles / seed) ne doivent PAS ré-accumuler de lignes.
UPDATE invoices SET open = FALSE;

-- PROD (PostgreSQL) — décommenter au déploiement pour garantir au plus UNE facture ouverte
-- par patient et par clinique (anti-course). Index partiel NON supporté par H2 (dev) → en
-- dev on s'appuie sur le verrou pessimiste applicatif (BillingService.findOpenByPatient).
-- CREATE UNIQUE INDEX uq_invoice_open_per_patient ON invoices (clinic_id, patient_id) WHERE open;
