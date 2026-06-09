# ClinicApp

Système de gestion de clinique complet — modulaire, adaptable, déployable sur serveur local ou cloud.

## Démarrage rapide

```bash
cd backend && mvn spring-boot:run
# → http://localhost:8080/login
# admin/admin123 · dr.martin/medecin123 · secretaire/secretaire123
```

## Documentation

| Document | Description |
|---|---|
| [`docs/OVERVIEW.md`](docs/OVERVIEW.md) | Vision, architecture, stack, roadmap |
| [`docs/DATABASE.md`](docs/DATABASE.md) | Schéma complet de la base de données |
| [`docs/modules/01-AUTH.md`](docs/modules/01-AUTH.md) | Authentification & Rôles |
| [`docs/modules/02-PATIENTS.md`](docs/modules/02-PATIENTS.md) | Gestion des patients |
| [`docs/modules/03-APPOINTMENTS.md`](docs/modules/03-APPOINTMENTS.md) | Rendez-vous & Agenda |
| [`docs/modules/04-CONSULTATIONS.md`](docs/modules/04-CONSULTATIONS.md) | Consultations & Ordonnances |
| [`docs/modules/05-PHARMACY.md`](docs/modules/05-PHARMACY.md) | Pharmacie & Stock |
| [`docs/modules/06-MATERNITY.md`](docs/modules/06-MATERNITY.md) | Maternité & Obstétrique |
| [`docs/modules/07-BILLING.md`](docs/modules/07-BILLING.md) | Facturation & Paiements |
| [`docs/modules/08-HOSPITALIZATION.md`](docs/modules/08-HOSPITALIZATION.md) | Hospitalisation & Lits |
| [`docs/modules/09-LAB.md`](docs/modules/09-LAB.md) | Laboratoire |
| [`docs/modules/10-RADIOLOGY.md`](docs/modules/10-RADIOLOGY.md) | Imagerie médicale |
| [`docs/modules/11-DEPARTMENTS.md`](docs/modules/11-DEPARTMENTS.md) | Départements spécialisés |
| [`docs/modules/12-NOTIFICATIONS.md`](docs/modules/12-NOTIFICATIONS.md) | Notifications SMS/Email |
| [`docs/modules/13-REPORTS.md`](docs/modules/13-REPORTS.md) | Rapports & Statistiques |
| [`docs/modules/14-CONFIG.md`](docs/modules/14-CONFIG.md) | Configuration clinique |
| [`docs/modules/15-DEPLOYMENT.md`](docs/modules/15-DEPLOYMENT.md) | Déploiement & Maintenance |

## Structure du projet

```
medical-app/
├── docs/           ← Documentation complète
├── backend/        ← Spring Boot (API REST + Web Thymeleaf)
├── desktop/        ← Client JavaFX
├── docker-compose.yml
└── .env.example
```

## Licence
Ayat_dev — tous droits réservés.
