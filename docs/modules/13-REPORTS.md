# Module 13 — Rapports & Statistiques

## Objectif
Fournir aux décideurs (direction, médecins, administrateurs) des indicateurs clés et des rapports exportables pour piloter la clinique.

## Tableaux de bord

### Dashboard Admin
- Revenus du jour / mois (vs mois précédent)
- Nombre de consultations (jour / semaine / mois)
- Taux d'occupation des lits
- Top 5 pathologies (codes CIM-10 les plus fréquents)
- Stock pharmacie critique (alertes)
- Répartition des modes de paiement (espèces vs mobile money vs assurance)

### Dashboard Médecin
- Mes patients du jour
- Résultats labo en attente de validation
- Rendez-vous de la semaine

### Dashboard Pharmacie
- Valeur totale du stock
- Médicaments sous seuil d'alerte
- Médicaments expirant dans 30 jours
- Top 10 médicaments consommés ce mois

## Rapports exportables (PDF + Excel)

| Rapport | Fréquence | Format |
|---|---|---|
| Rapport de caisse journalier | Quotidien | PDF |
| Bilan financier mensuel | Mensuel | PDF + Excel |
| Rapport d'activité médicale | Mensuel | PDF |
| État du stock pharmacie | À la demande | Excel |
| Liste des impayés | À la demande | PDF + Excel |
| Statistiques épidémiologiques | Mensuel | PDF |
| Rapport de vaccination (pédiatrie) | Mensuel | PDF |

## Statistiques épidémiologiques
- Top 10 des pathologies les plus fréquentes (par mois, par département)
- Prévalence par tranche d'âge et sexe
- Cartographie des consultations par ville/quartier (si donnée adresse)
- Évolution des consultations paludisme / VIH / diabète sur 12 mois

## Endpoints REST

```
GET /api/reports/dashboard/admin       → KPIs admin
GET /api/reports/dashboard/doctor      → KPIs médecin
GET /api/reports/daily-cash            → rapport caisse (date)
GET /api/reports/monthly-financial     → bilan mensuel (month, year)
GET /api/reports/activity              → rapport d'activité
GET /api/reports/stock                 → état stock
GET /api/reports/outstanding           → impayés
GET /api/reports/epidemiology          → stats épidémio
```

Chaque endpoint accepte `format=pdf` ou `format=excel` pour l'export.

## Implémentation Java

**À créer :**
- `reports/service/DashboardService.java`
- `reports/service/ReportExportService.java` (JasperReports + Apache POI)
- `reports/controller/api/ReportApiController.java`
- `reports/controller/web/ReportWebController.java`
- Templates : `reports/dashboard.html`, `reports/financial.html`

**Librairies :**
```xml
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports</artifactId>
    <version>6.21.0</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```
