-- V29: clinic_config devient PAR CLINIQUE (multi-tenant P4.2). Jusqu'ici un SINGLETON (1 ligne,
-- seedée en V4) → en multi-clinique, chaque clinique doit avoir SA config (identité, adresse,
-- QR de paiement, devise, drapeaux de modules, préfixes de numérotation).
-- Cloisonnement APPLICATIF (colonne clinic_id simple résolue via TenantContext, comme users.clinic_id),
-- PAS @TenantId : la config est créée à la volée et à l'installation (hors session avec tenant).
-- Compatible PostgreSQL (prod) ET H2 (dev/test).
--
-- La ligne singleton existante est rattachée à CENTRALE. Les autres cliniques obtiennent leur config
-- à la demande (ClinicConfigService.getConfig crée un défaut sous la clinique courante) ou à
-- l'installation (SetupService). UNIQUE(clinic_id) garantit « au plus une config par clinique ».

ALTER TABLE clinic_config ADD COLUMN clinic_id BIGINT;
UPDATE clinic_config SET clinic_id = (SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL;
ALTER TABLE clinic_config ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE clinic_config ADD CONSTRAINT fk_clinic_config_clinic FOREIGN KEY (clinic_id) REFERENCES clinics (id);
ALTER TABLE clinic_config ADD CONSTRAINT uq_clinic_config_clinic UNIQUE (clinic_id);
