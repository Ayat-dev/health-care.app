# Module 05 — Pharmacie & Gestion de stock

## Objectif
Gérer l'inventaire complet des médicaments, les entrées/sorties de stock, les alertes de rupture et de péremption, et la dispensation sur ordonnance.

## Fonctionnalités
- Catalogue des médicaments (nom commercial, DCI, forme, dosage)
- Gestion des lots avec numéros de lot et dates de péremption
- Alertes automatiques : stock faible + médicaments périmés ou proches de la péremption
- Dispensation sur ordonnance ou vente libre
- Historique complet des mouvements de stock
- Inventaire périodique
- Gestion des fournisseurs

## Endpoints REST

```
GET    /api/pharmacy/drugs                  → catalogue médicaments (q, category)
POST   /api/pharmacy/drugs                  → ajouter un médicament
PUT    /api/pharmacy/drugs/{id}             → modifier

GET    /api/pharmacy/stock                  → état du stock (alertes incluses)
POST   /api/pharmacy/stock/receive          → entrée de stock (réception commande)
GET    /api/pharmacy/stock/expiring         → lots périmant dans les 30 jours
GET    /api/pharmacy/stock/low              → produits sous le seuil d'alerte

POST   /api/pharmacy/dispensations          → dispenser (sur ordonnance ou libre)
GET    /api/pharmacy/dispensations          → historique des dispensations
GET    /api/pharmacy/dispensations/{id}     → détail d'une dispensation

GET    /api/pharmacy/movements              → historique mouvements (entrées + sorties)
```

## Routes web

```
GET  /pharmacy                    → tableau de bord pharmacie (stock, alertes)
GET  /pharmacy/drugs              → catalogue
GET  /pharmacy/stock              → état du stock avec alertes visuelles
GET  /pharmacy/dispensations/new  → nouvelle dispensation
GET  /pharmacy/dispensations      → historique
GET  /pharmacy/reports/monthly    → rapport mensuel consommation
```

## Tableau de bord pharmacie
- Compteur produits en stock faible (rouge)
- Compteur lots expirant dans 30 jours (orange)
- Top 10 médicaments les plus dispensés (ce mois)
- Valeur totale du stock

## Règles métier
- Dispensation = FIFO (premier périmé = premier sorti)
- Impossible de dispenser un lot périmé
- Toute sortie de stock génère un mouvement traçable
- Une ordonnance ne peut être dispensée qu'une seule fois
- Alerte automatique si stock < `quantity_alert` du produit
- Les médicaments sous contrôle (stupéfiants) ont un registre séparé

## Rapport mensuel (PDF)
- Récapitulatif entrées/sorties par médicament
- Valeur des stocks en début et fin de mois
- Liste des périmés détectés et écartés

## Implémentation Java

**À créer :**
- `pharmacy/entity/Drug.java`
- `pharmacy/entity/StockItem.java`
- `pharmacy/entity/Dispensation.java` + `DispensationItem.java`
- `pharmacy/service/PharmacyService.java`
- `pharmacy/service/StockAlertService.java` (scheduled task)
- `pharmacy/controller/api/PharmacyApiController.java`
- `pharmacy/controller/web/PharmacyWebController.java`
- Templates : `pharmacy/dashboard.html`, `pharmacy/stock.html`, `pharmacy/dispensation-form.html`

**Scheduled jobs :**
- `@Scheduled(cron = "0 8 * * *")` → vérifier stock faible + péremptions → créer notifications
