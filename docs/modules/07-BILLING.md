# Module 07 — Facturation & Paiements

## Objectif
Gérer l'intégralité du cycle de facturation : génération des factures, gestion des assurances/mutuelles, encaissement multi-méthodes (dont Mobile Money), et rapports financiers.

## Fonctionnalités
- Génération automatique de facture depuis une consultation ou hospitalisation
- Catalogue d'actes avec tarifs configurables par clinique
- Gestion des assurances (tiers payant partiel ou total)
- Paiements multi-méthodes : espèces, Mobile Money (Orange Money, Wave, MTN MoMo), carte, virement
- Reçu de paiement PDF
- Suivi des impayés
- Rapports financiers quotidiens/mensuels

## Endpoints REST

```
GET    /api/billing/invoices              → liste (status, patientId, dateFrom, dateTo)
GET    /api/billing/invoices/{id}         → détail
POST   /api/billing/invoices              → créer
PUT    /api/billing/invoices/{id}         → modifier (si EN_ATTENTE)
POST   /api/billing/invoices/{id}/pay     → enregistrer un paiement
PATCH  /api/billing/invoices/{id}/cancel  → annuler
GET    /api/billing/invoices/{id}/pdf     → facture PDF
GET    /api/billing/invoices/{id}/receipt → reçu PDF

GET    /api/billing/acts                  → catalogue des actes
POST   /api/billing/acts                  → ajouter un acte
PUT    /api/billing/acts/{id}             → modifier tarif

GET    /api/billing/insurance             → liste assureurs
GET    /api/billing/reports/daily         → rapport journalier
GET    /api/billing/reports/monthly       → rapport mensuel
```

## Routes web

```
GET      /billing/invoices              → liste des factures avec filtres
GET      /billing/invoices/{id}         → détail facture
GET/POST /billing/invoices/new          → créer une facture
GET      /billing/invoices/{id}/pay     → formulaire d'encaissement
GET      /billing/dashboard             → tableau de bord financier
GET      /billing/acts                  → catalogue des actes et tarifs
```

## Facture PDF — Structure
```
┌─────────────────────────────────────────┐
│  [LOGO]        CLINIQUE XYZ             │
│  Adresse · RC · NIF                     │
├─────────────────────────────────────────┤
│  FACTURE N° FAC-2024-00042              │
│  Date : 15/01/2024                      │
├─────────────────────────────────────────┤
│  Patient : NOM Prénom                   │
│  N° Dossier : PAT-2024-00010            │
│  Assurance : CNAM (60%)                 │
├─────────────────────────────────────────┤
│  DÉSIGNATION          QTÉ    PU    TOTAL│
│  Consultation          1   5000   5000  │
│  Radiographie          1   8000   8000  │
│  ─────────────────────────────────────  │
│  TOTAL HT                       13 000  │
│  Part assurance (60%)            7 800  │
│  RESTE À PAYER                   5 200  │
├─────────────────────────────────────────┤
│  Payé le : 15/01/2024                   │
│  Mode : Orange Money - Ref: OM24012345  │
│  SOLDE : 0 FCFA   ✅ SOLDÉE            │
└─────────────────────────────────────────┘
```

## Mobile Money — intégration
- **Africa's Talking** ou **CinetPay** (API unifiée FCFA : Orange/MTN/Wave/Moov)
- Flux : générer facture → payer → callback webhook → mise à jour statut → reçu auto
- En attendant l'intégration API : saisie manuelle du numéro de transaction + validation caissier

## Règles métier
- Une facture soldée ne peut pas être modifiée
- Un paiement partiel met le statut à PARTIEL
- La suppression de facture est interdite — seule l'annulation est permise (avec motif)
- Les remboursements génèrent un avoir (facture de crédit)
- Rapport de caisse généré automatiquement chaque soir à 22h

## Implémentation Java

**Existant :** `entity/Invoice.java`

**À créer :**
- `billing/entity/ActCatalog.java`
- `billing/entity/InvoiceItem.java`
- `billing/entity/Payment.java`
- `billing/entity/InsuranceProvider.java`
- `billing/service/BillingService.java`
- `billing/service/ReportService.java`
- `billing/controller/api/BillingApiController.java`
- `billing/controller/web/BillingWebController.java`
- Templates : `billing/invoices/list.html`, `billing/invoices/detail.html`, `billing/invoices/pay.html`, `billing/dashboard.html`
