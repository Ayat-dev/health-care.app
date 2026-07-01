-- V33 (Z4b) : rapprochement manuel des paiements par QR marchand (AmanaTa/MyNITA).
-- Ces modes n'ont pas d'API/webhook (marché Niger) → le caissier confirme la réception
-- côté app marchande puis marque le paiement « rapproché ». Ces 2 colonnes tracent qui/quand.
--
-- Toutes NULLABLE : un paiement non (encore) rapproché reste à NULL ; les modes non-QR
-- ne sont jamais rapprochés. Compatible PostgreSQL (prod) ET H2 (dev/test).
ALTER TABLE payments ADD COLUMN reconciled_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN reconciled_by BIGINT REFERENCES users (id);

CREATE INDEX idx_payments_reconciled_at ON payments (reconciled_at);
