-- Configuration par défaut pour le marché cible : Niger.
-- Devise XOF (Franc CFA BCEAO, déjà la valeur seedée) + fuseau Africa/Niamey
-- (la ligne singleton avait été seedée avec Africa/Dakar en V4).
UPDATE clinic_config SET currency = 'XOF', timezone = 'Africa/Niamey';
