-- V35 (Tier E2-A) : classe/groupe allergène sur le catalogue de médicaments.
-- Sert au recoupement NON-BLOQUANT avec les allergies (texte libre) du patient à la
-- dispensation (« le pharmacien voit immédiatement les allergies »). Nullable — la donnée
-- est curée au fil de l'eau. Compatible PostgreSQL (prod) ET H2 (dev/test).
ALTER TABLE drugs ADD COLUMN allergen_class VARCHAR(100);
