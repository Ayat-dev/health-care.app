# Module 06 — Maternité & Obstétrique

## Objectif
Assurer le suivi complet de la grossesse (CPN), de l'accouchement, et du post-partum, en conformité avec les protocoles de santé maternelle en Afrique de l'Ouest.

## Fonctionnalités
- Ouverture du dossier maternité (carnet de grossesse numérique)
- Enregistrement des consultations prénatales (CPN1 à CPN8)
- Suivi : poids, tension, hauteur utérine, rythme cardiaque fœtal
- Gestion de l'accouchement (type, complications, état du nouveau-né)
- Score APGAR
- Vaccinations (VAT — vaccin antitétanique)
- Supplémentation (fer, acide folique)
- Alerte grossesses à risque (HTA, diabète gestationnel, VIH…)
- Post-partum et visite du nouveau-né

## Endpoints REST

```
GET    /api/maternity                        → liste des dossiers (status: EN_COURS, ACCOUCHEE)
POST   /api/maternity                        → ouvrir un dossier maternité
GET    /api/maternity/{id}                   → dossier complet
PUT    /api/maternity/{id}                   → modifier
PATCH  /api/maternity/{id}/deliver           → enregistrer l'accouchement

GET    /api/maternity/{id}/visits            → liste des CPN
POST   /api/maternity/{id}/visits            → ajouter une CPN
PUT    /api/maternity/{id}/visits/{visitId}  → modifier une CPN
```

## Routes web

```
GET      /maternity                     → liste des grossesses en cours
GET      /maternity/{id}                → dossier maternité (onglets)
GET/POST /maternity/new                 → ouvrir un dossier
GET/POST /maternity/{id}/visits/new     → saisir une CPN
GET      /maternity/{id}/delivery       → formulaire d'accouchement
```

## Calculs automatiques
- **Date probable d'accouchement (DPA)** = `last_period_date + 280 jours`
- **Âge gestationnel** = `(today - last_period_date) / 7` semaines
- **IMC** = `weight / (height/100)²`

## Vue dossier maternité (onglets)
1. **Grossesse** — données de base, DPA, parité, gravité
2. **Consultations CPN** — tableau chronologique des visites
3. **Courbe de poids** — graphique poids vs semaines
4. **Accouchement** — détails si déjà accouché
5. **Nouveau-né** — poids, APGAR, sexe

## Grossesse à risque — alertes automatiques
- Tension artérielle systolique > 140 → alerte rouge
- Prise de poids > 2kg en 2 semaines → alerte orange
- Protéinurie positive → alerte rouge
- Moins de 4 CPN complétées à 36 semaines → alerte

## Protocole CPN minimum OMS (Afrique subsaharienne)
| Visite | Semaine | Contenu obligatoire |
|---|---|---|
| CPN1 | < 12 SA | Bilan complet, groupage, VIH, VAT1 |
| CPN2 | 20–24 SA | Echo morfologique, glycémie |
| CPN3 | 28–32 SA | Albuminurie, NFS |
| CPN4 | 36–38 SA | Présentation, bassin, préparation accouchement |

## Implémentation Java

**À créer :**
- `maternity/entity/MaternityRecord.java`
- `maternity/entity/PrenatalVisit.java`
- `maternity/service/MaternityService.java`
- `maternity/service/MaternityRiskCalculator.java`
- Templates : `maternity/list.html`, `maternity/record.html`, `maternity/visit-form.html`
