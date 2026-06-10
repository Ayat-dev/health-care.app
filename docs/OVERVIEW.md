# ClinicApp — Master Overview

> **Version** 1.0.0 · **Langue** Bilingue (FR/EN) · **Statut** En développement actif

---

## 1. Vision produit

ClinicApp est un système de gestion de clinique **complet, modulaire et adaptable**, conçu pour les établissements de soins africains modernes et tout type de clinique privée ou publique.

**Objectifs clés :**
- Couvrir 100 % des besoins opérationnels d'une clinique (dossiers, RDV, pharmacie, facturation, labo, maternité, etc.)
- Déployable sur un **serveur local sans Internet** (réseau LAN) ou en **cloud**
- Configurable pour chaque clinique sans modifier le code (multi-tenant prêt)
- Accessible depuis un **navigateur** (web) et une **application desktop** (JavaFX)
- Compatible matériel modeste (Raspberry Pi 4, vieux PC recyclé)

---

## 2. Stack technique

| Couche | Technologie | Rôle |
|---|---|---|
| Backend | Spring Boot 3.2 (Java 17) | API REST + rendu web Thymeleaf |
| Base de données | PostgreSQL 16 / H2 (dev) | Persistance |
| Migrations DB | Flyway | Versionning du schéma |
| Sécurité | Spring Security + JWT | Auth API (desktop) + sessions (web) |
| Interface web | Thymeleaf + HTML/CSS/JS vanilla | Zéro framework JS, fonctionne sur tout navigateur |
| Client desktop | JavaFX 20 (Java 17) | Application native multi-OS |
| Conteneurisation | Docker + Docker Compose | Déploiement reproductible |
| Notifications | Twilio SMS / SMTP email | Rappels, alertes |
| Rapports | JasperReports / iText PDF | Ordonnances, factures, rapports |
| Tests | JUnit 5 + Testcontainers | Tests unitaires et d'intégration |

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     CLIENTS                             │
│  ┌──────────────────┐        ┌─────────────────────┐   │
│  │  Navigateur web  │        │  Desktop JavaFX     │   │
│  │  (secrétariat,   │        │  (médecins,         │   │
│  │   admin, web)    │        │   infirmiers)       │   │
│  └────────┬─────────┘        └──────────┬──────────┘   │
│           │ HTTP + Session              │ HTTP + JWT    │
└───────────┼─────────────────────────────┼──────────────┘
            │                             │
┌───────────▼─────────────────────────────▼──────────────┐
│                  BACKEND (Spring Boot)                   │
│  ┌─────────────────┐   ┌──────────────────────────────┐ │
│  │  Web Controllers│   │  REST API Controllers        │ │
│  │  (Thymeleaf)    │   │  /api/**                     │ │
│  └────────┬────────┘   └─────────────┬────────────────┘ │
│           └──────────┬───────────────┘                  │
│                ┌─────▼──────┐                           │
│                │  Services  │  (logique métier)         │
│                └─────┬──────┘                           │
│                ┌─────▼──────┐                           │
│                │    JPA /   │  (repositories)           │
│                │  Hibernate │                           │
│                └─────┬──────┘                           │
└──────────────────────┼──────────────────────────────────┘
                       │
              ┌────────▼────────┐
              │   PostgreSQL    │
              │   (ou H2 dev)   │
              └─────────────────┘
```

---

## 4. Modules fonctionnels

| # | Module | Fichier spec | Statut |
|---|---|---|---|
| 01 | Authentification & Rôles | `modules/01-AUTH.md` | 🟡 En cours |
| 02 | Gestion des patients | `modules/02-PATIENTS.md` | 🟡 En cours |
| 03 | Rendez-vous & Agenda | `modules/03-APPOINTMENTS.md` | ✅ Terminé |
| 04 | Consultations & Ordonnances | `modules/04-CONSULTATIONS.md` | ✅ Terminé — `consultations` + `prescriptions` + `prescription_items` (V6), constantes/diagnostic/CIM-10, ordonnances ORD-YYYY-NNNNN, clôture diagnostic-obligatoire, impression HTML (PDF binaire différé), onglet dossier patient câblé |
| 05 | Pharmacie & Stock | `modules/05-PHARMACY.md` | ✅ Terminé — `drugs` + `stock_items` + `dispensations` + `dispensation_items` (V7), catalogue + réception de lots, alertes stock faible/péremption, dispensation FIFO (lot périmant en premier, périmés exclus) sur ordonnance ou vente libre, ordonnance dispensée une seule fois, tableau de bord (compteurs + top dispensés + valeur stock), job quotidien `StockAlertService` |
| 06 | Maternité & Obstétrique | `modules/06-MATERNITY.md` | ⚪ Planifié |
| 07 | Facturation & Paiements | `modules/07-BILLING.md` | ⚪ Planifié |
| 08 | Hospitalisation & Lits | `modules/08-HOSPITALIZATION.md` | ⚪ Planifié |
| 09 | Laboratoire | `modules/09-LAB.md` | ✅ Terminé — `lab_requests` + `lab_request_items` + `lab_results` (V8), demande d'analyses depuis une consultation (catalogue `lab_test_catalog` V4), travail du jour laborantin (urgent en premier), saisie des résultats avec détection automatique des valeurs anormales (intervalles numériques + qualitatif « Négatif »), validation médecin/biologiste (EN_ATTENTE→EN_COURS→VALIDE→LIVRE), bulletin de résultats imprimable, historique par patient |
| 10 | Imagerie médicale | `modules/10-RADIOLOGY.md` | ⚪ Planifié |
| 11 | Départements spécialisés | `modules/11-DEPARTMENTS.md` | 🟡 En cours — table de référence `departments` faite (socle) ; sous-modules cliniques (dentaire, pédiatrie…) à venir |
| 12 | Notifications (SMS/Email) | `modules/12-NOTIFICATIONS.md` | ⚪ Planifié |
| 13 | Rapports & Statistiques | `modules/13-REPORTS.md` | ⚪ Planifié |
| 14 | Configuration clinique | `modules/14-CONFIG.md` | 🟢 Socle fait — `clinic_config` (identité, feature flags, numérotation) + catalogues `insurance_providers` / `act_catalog` / `lab_test_catalog`, interfaces admin `/admin/config\|insurance\|acts\|lab-tests` (V4) ; onglets sauvegardes/notifications à venir |
| 15 | Déploiement & Maintenance | `modules/15-DEPLOYMENT.md` | ⚪ Planifié |

---

## 5. Rôles utilisateurs

| Rôle | Code | Accès |
|---|---|---|
| Administrateur | `ADMIN` | Tout — configuration, utilisateurs, rapports financiers |
| Médecin | `MEDECIN` | Ses patients, consultations, ordonnances, labo |
| Infirmier/ère | `INFIRMIER` | Constantes, soins, hospitalisations |
| Secrétaire | `SECRETAIRE` | RDV, dossiers (lecture), facturation, accueil |
| Pharmacien | `PHARMACIEN` | Stock pharmacie, dispensation |
| Laborantin | `LABORANTIN` | Saisie et validation des résultats labo |
| Caissier | `CAISSIER` | Encaissements, reçus |
| Patient | `PATIENT` | Son dossier, ses RDV (portail patient — optionnel) |

---

## 6. Roadmap de développement

### Phase 1 — Socle (Mois 1–2) ✅ Partiellement fait
- [x] Structure du projet backend Spring Boot
- [x] Entités JPA : User, Patient, Appointment, Invoice
- [x] Sécurité JWT + Spring Security
- [x] API REST : Auth, Patients, RDV
- [x] Interface web Thymeleaf : Login, Dashboard, Patients, RDV
- [x] Client desktop JavaFX : Login, Dashboard, Patients
- [x] Docker Compose (PostgreSQL + backend)
- [ ] Flyway migrations
- [ ] Tests unitaires de base

### Phase 2 — Cœur clinique (Mois 2–3)
- [ ] Module Consultations (actes, diagnostics CIM-10, ordonnances)
- [ ] Module Pharmacie (stock, dispensation, alertes péremption)
- [ ] Module Facturation complet (Mobile Money, assurances, reçus PDF)
- [ ] Module Hospitalisation (lits, admissions, transferts)
- [ ] Impression : ordonnances, reçus, certificats (PDF)
- [ ] Notifications SMS (Twilio / Africa's Talking)

### Phase 3 — Spécialités (Mois 3–4)
- [ ] Module Maternité (suivi grossesse, CPN, accouchements)
- [x] Module Laboratoire (demandes d'analyses, saisie résultats, validation, bulletin)
- [ ] Module Dentisterie (schéma dentaire, devis, actes)
- [ ] Module Pédiatrie (courbe de croissance, vaccinations)
- [ ] Module Ophtalmologie (acuité visuelle, prescriptions lunettes)

### Phase 4 — Intelligence & Rapports (Mois 4–5)
- [ ] Tableau de bord statistiques avancées
- [ ] Rapports exportables (PDF, Excel)
- [ ] Gestion des assurances / mutuelles
- [ ] Portail patient (web, consultation dossier + prise de RDV en ligne)
- [ ] Mode hors-ligne (PWA ou cache local)
- [ ] Multi-langues (français, anglais, arabe)

### Phase 5 — Production & Scale (Mois 5–6)
- [ ] Multi-tenant (une instance = plusieurs cliniques)
- [ ] Audit trail complet (qui a fait quoi, quand)
- [ ] Sauvegarde automatique chiffrée
- [ ] Documentation utilisateur (guide par rôle)
- [ ] Installeur Windows/Linux pour le client desktop

---

## 7. Conventions de code

### Nommage
- **Packages Java** : `com.clinic.backend.{module}` (ex: `com.clinic.backend.pharmacy`)
- **Entités** : PascalCase, singulier (ex: `StockItem`, `Prescription`)
- **DTOs** : suffixe `Dto` (ex: `PatientDto`, `StockItemDto`)
- **Contrôleurs REST** : suffixe `ApiController` dans `controller/api/`
- **Contrôleurs web** : suffixe `WebController` dans `controller/web/`
- **Services** : suffixe `Service` (ex: `PatientService`)
- **Repositories** : suffixe `Repository`
- **Templates Thymeleaf** : `resources/templates/{module}/{view}.html`
- **Migrations Flyway** : `V{N}__{description_snake_case}.sql`

### Structure d'un module (exemple : pharmacie)
```
com.clinic.backend.pharmacy/
  ├── entity/
  │   ├── Drug.java
  │   ├── StockItem.java
  │   └── Dispensation.java
  ├── repository/
  │   ├── DrugRepository.java
  │   └── StockItemRepository.java
  ├── service/
  │   └── PharmacyService.java
  ├── dto/
  │   ├── DrugDto.java
  │   └── StockItemDto.java
  └── controller/
      ├── api/PharmacyApiController.java
      └── web/PharmacyWebController.java
```

### API REST — conventions
- `GET    /api/{module}`           → liste
- `GET    /api/{module}/{id}`      → détail
- `POST   /api/{module}`           → création
- `PUT    /api/{module}/{id}`      → mise à jour complète
- `PATCH  /api/{module}/{id}/{action}` → action partielle (ex: `/api/appointments/5/confirm`)
- `DELETE /api/{module}/{id}`      → suppression (ADMIN uniquement)

Toutes les réponses d'erreur suivent le format :
```json
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Patient introuvable : 42",
  "path": "/api/patients/42"
}
```

### Sécurité
- Aucun mot de passe en clair dans le code ou les fichiers de config
- Toutes les variables sensibles via `application.properties` ou variables d'environnement
- Audit log pour toute modification de données médicales

---

## 8. Structure du dépôt

```
medical-app/
├── docs/                    ← 📚 Toute la documentation (CE DOSSIER)
│   ├── OVERVIEW.md          ← Ce fichier
│   ├── DATABASE.md          ← Schéma complet de la base de données
│   └── modules/             ← Spec détaillée par module
├── backend/                 ← 🖥️  Spring Boot
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
├── desktop/                 ← 🖥️  Client JavaFX
│   ├── src/
│   └── pom.xml
├── docker-compose.yml       ← Démarrage en une commande
├── .env.example             ← Template de configuration
└── README.md                ← Guide de démarrage rapide
```

---

## 9. Démarrage rapide (développeur)

```bash
# 1. Cloner le projet
git clone https://github.com/votre-org/clinicapp.git
cd clinicapp

# 2. Lancer le backend en mode dev (H2, données de test incluses)
cd backend
mvn spring-boot:run

# 3. Ouvrir dans le navigateur
# → http://localhost:8080/login
# → admin / admin123

# 4. (Optionnel) Lancer le client desktop
cd ../desktop
mvn javafx:run
```

Pour la production avec Docker :
```bash
cp .env.example .env    # remplir DB_PASSWORD et JWT_SECRET
docker-compose up -d
# → http://IP_SERVEUR:8080/login
```

---

*Ce document est la source de vérité du projet. Toute décision architecturale doit être reflétée ici avant d'être implémentée.*
