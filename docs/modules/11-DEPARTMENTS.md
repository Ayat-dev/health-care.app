# Module 11 — Départements spécialisés

## Vue d'ensemble
Chaque département spécialisé étend le module Consultations avec des champs et workflows propres. Les données communes (patient, médecin, constantes, prescription) restent dans le module Consultations de base.

---

## 11A — Dentisterie

### Fonctionnalités spécifiques
- Schéma dentaire interactif (32 dents adultes / 20 dents enfants)
- Actes dentaires codifiés (extraction, obturation, détartrage, couronne, implant…)
- Devis dentaire imprimable
- Suivi des séances (un traitement peut nécessiter plusieurs séances)

### Entité `dental_records`
```sql
id, patient_id, consultation_id, tooth_schema JSONB,
-- tooth_schema: { "11": "carie", "16": "extraction", "21": "sain"... }
treatment_plan TEXT, next_session_date DATE
```

### Schéma dentaire JSON
Numérotation FDI (internationale) : quadrant 1-4, dent 1-8.
```json
{
  "11": { "status": "sain" },
  "16": { "status": "carie", "treatment": "obturation", "done": false },
  "26": { "status": "extraction", "done": true, "date": "2024-01-10" }
}
```

---

## 11B — Pédiatrie

### Fonctionnalités spécifiques
- Courbe de croissance OMS (poids/taille/périmètre crânien vs âge)
- Carnet de vaccination numérique (PEV — Programme Élargi de Vaccination)
- Score de Hollyday (évaluation nutritionnelle)
- Alerte malnutrition (poids-pour-taille < -2 DS)

### Vaccinations PEV (Afrique de l'Ouest)
| Âge | Vaccins |
|---|---|
| Naissance | BCG, VPO0, VHB1 |
| 6 semaines | Penta1, VPO1, Pneumo1, Rota1 |
| 10 semaines | Penta2, VPO2, Pneumo2, Rota2 |
| 14 semaines | Penta3, VPO3, Pneumo3, IPV |
| 9 mois | VAR, VPO4, VAA, Méningite A |
| 18 mois | Rappel rougeole |

### Entité `vaccination_records`
```sql
id, patient_id, vaccine_name VARCHAR(80), batch_number VARCHAR(50),
administered_date DATE, administered_by BIGINT, next_dose_date DATE,
notes TEXT
```

---

## 11C — Ophtalmologie

### Fonctionnalités spécifiques
- Mesure d'acuité visuelle (OD / OG, avec et sans correction)
- Prescription de lunettes / lentilles
- Fond d'œil (description textuelle)
- Tonométrie (pression intraoculaire)

### Ordonnance optique
```
OD : Sphère -2.00 / Cylindre -0.50 / Axe 90°
OG : Sphère -1.75 / Cylindre -0.25 / Axe 85°
Addition (presbytie) : +2.00
Écart pupillaire : 64 mm
```

---

## 11D — Gynécologie (hors maternité)

### Fonctionnalités spécifiques
- Frottis cervico-vaginal (résultat + suivi)
- Contraception (type, date début, renouvellement)
- Calendrier menstruel
- Ménopause / THS

---

## Implémentation Java

Chaque sous-module est un package séparé :
```
dental/entity/DentalRecord.java
pediatrics/entity/VaccinationRecord.java
ophthalmology/entity/OphthalmologyRecord.java
```
Chaque module est activé/désactivé via `clinic_config.module_*`.
