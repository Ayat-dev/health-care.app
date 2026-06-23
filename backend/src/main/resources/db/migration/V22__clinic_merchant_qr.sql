-- Paiement mobile par QR marchand (Amanty / MyNITA) — confirmation manuelle.
-- La clinique téléverse le QR marchand de son compte ; il est affiché à
-- l'encaissement pour que le patient le scanne et paie. L'identifiant marchand
-- (optionnel) permet de payer en saisissant le code plutôt qu'en scannant.
ALTER TABLE clinic_config ADD COLUMN amanty_qr_url VARCHAR(255);
ALTER TABLE clinic_config ADD COLUMN amanty_merchant_id VARCHAR(60);
ALTER TABLE clinic_config ADD COLUMN mynita_qr_url VARCHAR(255);
ALTER TABLE clinic_config ADD COLUMN mynita_merchant_id VARCHAR(60);
