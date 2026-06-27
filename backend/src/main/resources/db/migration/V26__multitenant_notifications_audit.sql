-- V26: Étend le discriminant multi-tenant (P4.2) aux files transverses notifications
-- et audit_log. Ces tables étaient écrites/relues sans filtre clinique → un admin
-- voyait l'audit de toutes les cliniques, la file de notifs n'était pas cloisonnée.
-- Compatible PostgreSQL (prod) ET H2 (dev/test).
--
-- NB notifications créées par des tâches de fond (rappels RDV, relances, alertes stock) :
--   le scheduler tourne désormais clinique par clinique (TenantContext.runAs) → le tenant
--   est posé avant l'écriture. Voir NotificationScheduler.
-- NB audit_log : écrit par AuditAspect dans le contexte de la requête (tenant = clinique de
--   l'utilisateur). Les actions transverses SUPER_ADMIN ne sont pas auditées (set des 14
--   méthodes @Audited = clinique uniquement).
--
-- Schéma 3 temps (robuste même si la table contient déjà des données → rétro-remplissage CENTRALE).

ALTER TABLE notifications ADD COLUMN clinic_id BIGINT;
ALTER TABLE audit_log     ADD COLUMN clinic_id BIGINT;

UPDATE notifications SET clinic_id = (SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL;
UPDATE audit_log     SET clinic_id = (SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL;

ALTER TABLE notifications ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE audit_log     ALTER COLUMN clinic_id SET NOT NULL;

ALTER TABLE notifications ADD CONSTRAINT fk_notifications_clinic FOREIGN KEY (clinic_id) REFERENCES clinics (id);
ALTER TABLE audit_log     ADD CONSTRAINT fk_audit_log_clinic     FOREIGN KEY (clinic_id) REFERENCES clinics (id);

CREATE INDEX idx_notifications_clinic ON notifications (clinic_id);
CREATE INDEX idx_audit_log_clinic     ON audit_log (clinic_id);
