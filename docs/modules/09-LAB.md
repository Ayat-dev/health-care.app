# Module 09 — Laboratoire

## Objectif
Gérer les demandes d'analyses, la saisie des résultats par les laborantins, la validation médicale, et la communication des résultats aux médecins prescripteurs.

## Fonctionnalités
- Catalogue d'analyses avec prix et délais
- Demande d'analyses depuis une consultation
- Interface laborantin : liste des demandes en attente, saisie des résultats
- Validation des résultats par un médecin ou biologiste senior
- Alertes résultats anormaux (valeurs hors normes)
- Impression du bulletin de résultats (PDF)
- Historique par patient

## Catégories d'analyses
| Catégorie | Exemples |
|---|---|
| Hématologie | NFS, groupe sanguin, VS |
| Biochimie | Glycémie, créatinine, transaminases, cholestérol |
| Sérologie | VIH, hépatite B/C, syphilis (RPR), paludisme (TDR) |
| Bactériologie | ECBU, hémoculture, coproculture |
| Parasitologie | Goutte épaisse, examen parasitologique des selles |
| Immunologie | CRP, facteur rhumatoïde |
| Hormonologie | TSH, hCG (grossesse), FSH |

## Endpoints REST

```
GET    /api/lab/catalog                    → catalogue des analyses
GET    /api/lab/requests                   → demandes (status, patientId, priority)
POST   /api/lab/requests                   → créer une demande
GET    /api/lab/requests/{id}              → détail + résultats
POST   /api/lab/requests/{id}/results      → saisir les résultats (LABORANTIN)
PATCH  /api/lab/requests/{id}/validate     → valider (MEDECIN/BIOLOGISTE)
GET    /api/lab/requests/{id}/pdf          → bulletin résultats PDF
```

## Interface laborantin
- Vue "Travail du jour" : toutes les demandes EN_ATTENTE ou EN_COURS
- Saisie rapide : clic sur l'analyse → saisir valeur + unité
- Badge ANORMAL si la valeur dépasse les normes (comparaison automatique)
- Bouton "Valider tout" pour envoyer les résultats au médecin

## Bulletin de résultats PDF
```
┌─────────────────────────────────────────┐
│ LABORATOIRE DE [CLINIQUE]               │
│ Date: 15/01/2024  N°: LAB-2024-00123    │
├─────────────────────────────────────────┤
│ Patient: DIOP Mamadou   Age: 35 ans     │
│ Prescripteur: Dr. Martin                │
├─────────────────────────────────────────┤
│ HÉMATOLOGIE                             │
│ Hémoglobine    12.5 g/dL  [13-17] ⚠️   │
│ Leucocytes     7.2 G/L    [4-10]  ✅   │
├─────────────────────────────────────────┤
│ Validé par: Dr. Diallo, Biologiste      │
└─────────────────────────────────────────┘
```

## Implémentation Java

**À créer :**
- `lab/entity/LabTestCatalog.java`
- `lab/entity/LabRequest.java`, `LabRequestItem.java`, `LabResult.java`
- `lab/service/LabService.java`
- `lab/service/ResultAbnormalityChecker.java`
- Templates : `lab/worklist.html`, `lab/result-entry.html`, `lab/request-detail.html`
