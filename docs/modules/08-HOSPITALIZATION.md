# Module 08 — Hospitalisation & Gestion des lits

## Objectif
Gérer les admissions, le séjour hospitalier, l'occupation des lits, et la sortie des patients hospitalisés.

## Fonctionnalités
- Plan des chambres et lits avec statut en temps réel (libre/occupé/réservé)
- Admission, transfert intra-clinique, sortie
- Feuille de soins quotidienne (constantes, médicaments administrés)
- Facturation automatique au tarif chambre × nombre de nuits
- Gestion des sorties contre avis médical (AMA)

## Endpoints REST

```
GET    /api/beds                           → plan des lits (statut temps réel)
GET    /api/hospitalizations               → liste (status: ADMIS, SORTI)
GET    /api/hospitalizations/{id}          → détail
POST   /api/hospitalizations               → admettre un patient
PATCH  /api/hospitalizations/{id}/transfer → transférer (autre chambre)
PATCH  /api/hospitalizations/{id}/discharge → sortie du patient
```

## Plan des lits — Vue tableau de bord
```
DÉPARTEMENT MÉDECINE GÉNÉRALE
┌──────────┬──────────┬──────────┬──────────┐
│ Ch.101   │ Ch.102   │ Ch.103   │ Ch.104   │
│ 🟢 LIBRE │ 🔴 ADMIS │ 🔴 ADMIS │ 🟢 LIBRE │
│          │ M. DIOP  │ Mme FALL │          │
│          │ J+3      │ J+1      │          │
└──────────┴──────────┴──────────┴──────────┘
```

## Feuille de soins (par jour d'hospitalisation)
- Constantes vitales (matin/soir)
- Médicaments administrés (avec heure et dose)
- Observations infirmières
- Visites médicales

## Règles métier
- Un lit ne peut avoir qu'un seul patient admis à la fois
- La sortie clôture automatiquement la feuille de soins
- La facturation est calculée : `nb_nuits × tarif_chambre + actes`
- Une sortie déclenche la génération de la facture finale

## Implémentation Java

**À créer :**
- `hospitalization/entity/Room.java`
- `hospitalization/entity/Hospitalization.java`
- `hospitalization/entity/DailyCareSheet.java`
- `hospitalization/service/HospitalizationService.java`
- `hospitalization/service/BedManagementService.java`
- Templates : `hospitalization/beds.html`, `hospitalization/detail.html`, `hospitalization/admit-form.html`
