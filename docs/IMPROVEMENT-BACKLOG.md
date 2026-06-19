# ClinicApp — Backlog d'amélioration & durcissement (le tracker transverse)

> **But de ce fichier** : suivre, pas à pas et **entre sessions**, tout ce qui sépare le projet
> d'un EHR déployable en production réelle. Issu de l'analyse comparative du 2026-06-19
> (meilleurs systèmes du marché, sécurité HIPAA/OWASP, tendances UI/UX 2025-2026) **croisée
> avec un audit du code réel**.
>
> **Comment l'utiliser** :
> - `[ ]` todo · `[~]` en cours · `[x]` fait. Coche au fur et à mesure, comme le ledger de `CLAUDE.md`.
> - Un item par session de préférence ; `mvnd` pour vérifier ; mets à jour la case + une note de résultat.
> - Ordre conseillé : **P1 d'abord** (bloquants prod), puis P2, P3, P4.
> - Ce fichier est référencé depuis `CLAUDE.md` (« NEXT UP »). Le ledger de `CLAUDE.md` reste
>   la source de vérité pour les **modules fonctionnels** ; **ce fichier** est la source de
>   vérité pour le **non-fonctionnel + différenciation**.
>
> **État de référence au 2026-06-19** : 14 modules cliniques faits, RBAC propre (corrigé ce
> jour : variable `th:each` réservée `mod`→`navMod` qui 500-ait toutes les pages ; gating
> lecture pharmacie). Socle fonctionnel solide ; les manques sont surtout **non-fonctionnels**.

---

## 🔴 P1 — Bloquants production (sécurité & fiabilité)

### P1.1 — Séparation des profils Spring dev / prod
- [x] Statut : **fait** (2026-06-19)
- **Réalisé** : `application.properties` réduit à une base neutre (`spring.profiles.active=dev`, Flyway, OSIV off, multipart, upload-dir, expiration JWT) ; `application-dev.properties` (H2, console H2 on, secret de dev) ; `application-prod.properties` (PostgreSQL + secrets via env, **sans défaut → fail-fast**, console H2 off). `DataInitializer` gated `@Profile("!prod")` (plus de seed démo ni de `admin/admin123` en prod) ; nouveau `ProdDataInitializer` (`@Profile("prod")`) crée l'admin depuis `CLINIC_ADMIN_USERNAME/PASSWORD` si base vide. `docker-compose.yml` : `SPRING_PROFILES_ACTIVE=prod`, healthcheck Postgres + `depends_on: condition: service_healthy`, volume `backend_uploads`. `.env.example` (racine) créé ; `.env` déjà gitignoré.
- **Vérifié** : dev (défaut) démarre, Flyway v13, login admin/admin123 + dashboard 200 ; prod **sans secrets → refuse de démarrer** (`Could not resolve placeholder 'JWT_SECRET'`, rien sur 8080) ; prod avec secrets (testé sur H2, même chemin de code, Docker indispo pour Postgres) → admin bootstrappé depuis l'env (login OK, dashboard 200), `admin/admin123` rejeté (`/login?error=true`), `/patients` 200 sans donnée démo, **h2-console 404** (vs 302 en dev).
- **Reste à confirmer au déploiement réel** : boot complet sur PostgreSQL (non testable ici, Docker absent) — la config + le driver au pom sont en place.
- **Pourquoi** : aujourd'hui un **seul** `application.properties` (H2 en mémoire, console H2 activée,
  `show-sql`, secret JWT par défaut en dur). Un déploiement « prod » tournerait sur la config de dev.
- **Où** : `backend/src/main/resources/application.properties` (existant, dev).
- **Étapes** :
  1. Renommer la config H2 actuelle en `application-dev.properties` ; garder un `application.properties` minimal neutre (`spring.profiles.active=dev` par défaut).
  2. Créer `application-prod.properties` : datasource PostgreSQL, `spring.h2.console.enabled=false`, `spring.jpa.show-sql=false`, `ddl-auto=validate`.
  3. Externaliser **tous** les secrets en variables d'env : `app.jwt.secret`, `spring.datasource.password`, etc. (`${DB_PASSWORD}`, `${JWT_SECRET}`).
  4. `docker-compose.yml` + `.env.example` : injecter ces variables ; `SPRING_PROFILES_ACTIVE=prod`.
- **Critère d'acceptation** : `SPRING_PROFILES_ACTIVE=prod` démarre sur PostgreSQL, console H2 inaccessible, app refuse de démarrer si `JWT_SECRET` absent (fail-fast).

### P1.2 — Journal d'audit (qui / quoi / quand)
- [ ] Statut : todo
- **Pourquoi** : manipulation de données médicales → exigence légale (HIPAA-like). **Aucun** audit aujourd'hui. Déjà listé « différé » au module 15.
- **Où** : nouveau pkg `com.clinic.backend.audit`.
- **Étapes** :
  1. Table `audit_log` (V14) : `id, user_id, username, action, entity_type, entity_id, ip_address, user_agent, details, created_at`.
  2. Capture via AOP `@Aspect` autour des `service` (create/update/delete/transitions de statut) **ou** `@EntityListener` JPA (`@PostPersist/@PostUpdate/@PostRemove`).
  3. Vue admin `/admin/audit` (lecture seule, filtres user/entité/date), `@PreAuthorize("hasRole('ADMIN')")`.
- **Critère d'acceptation** : créer/modifier un patient, valider un labo, encaisser une facture → 1 ligne `audit_log` chacune avec l'utilisateur réel.

### P1.3 — Anti-brute-force (verrouillage + rate-limit login)
- [ ] Statut : todo
- **Pourquoi** : aucun lockout ni rate-limit ; `/login` et `/api/auth/login` exposés au bourrinage.
- **Où** : `User` (a déjà `active`), `SecurityConfig`, `service` auth.
- **Étapes** :
  1. Colonnes `failed_attempts INT`, `locked_until TIMESTAMPTZ` sur `users` (V15).
  2. `AuthenticationFailureHandler` : incrémente ; à N (ex. 5) → `locked_until = now()+15min`. Succès → reset.
  3. `isAccountNonLocked()` reflète `locked_until`.
  4. (Option) rate-limit IP via Bucket4j sur les endpoints de login.
- **Critère d'acceptation** : 5 mauvais mots de passe → compte verrouillé 15 min, message clair.

### P1.4 — `@RestControllerAdvice` global (fin des 500 bruts)
- [ ] Statut : todo
- **Pourquoi** : NB récurrent dans `CLAUDE.md` (« bare 500 on IllegalState ») sur pharmacy/lab/radio/hospit/billing/maternity. Le client JavaFX reçoit du 500 au lieu d'un JSON exploitable. Un `GlobalExceptionHandler` web existe déjà ; il manque le pendant **API**.
- **Où** : `controller/api` ; s'inspirer de `config/GlobalExceptionHandler.java`.
- **Étapes** :
  1. `@RestControllerAdvice` ciblant `/api/**` : `IllegalArgumentException`→400, `IllegalStateException`→409, `ResourceNotFoundException`→404, `AccessDeniedException`→403, fallback→500, au format d'erreur standard du projet (`{timestamp,status,error,message,path}`).
- **Critère d'acceptation** : dispensation insuffisante / surpaiement via API → 409 JSON propre, plus de 500.

### P1.5 — Tests (sécurité + invariants métier)
- [ ] Statut : todo
- **Pourquoi** : **0 test** dans `backend/src/test`. Pas de filet pour les régressions (ex. le bug `mod` aurait été attrapé par un test de rendu).
- **Étapes** :
  1. Tests `@WebMvcTest`/`@SpringBootTest` de sécurité : pour chaque rôle, matrice 200/403 attendus (automatiser ce que j'ai vérifié à la main).
  2. Test de rendu : `GET /dashboard` (et un échantillon de pages) → 200 (anti-régression templating).
  3. Tests d'invariants métier : dispensation FIFO, couverture assurance, transitions de statut interdites, double-admission, double-dispensation.
  4. Ajouter Testcontainers (PostgreSQL) pour valider Flyway sur le vrai moteur.
- **Critère d'acceptation** : `mvnd test` vert, matrice de rôles couverte.

---

## 🟠 P2 — Attentes d'un EHR sérieux

### P2.1 — Interopérabilité FHIR R4
- [ ] Statut : todo
- **Pourquoi** : standard 2025 (ONC Cures Act, Patient Access API). Sépare un « logiciel interne » d'un EHR.
- **Étapes** : exposer `Patient`, `Encounter`, `Observation` (constantes/labo), `MedicationRequest` (ordonnances) en FHIR via HAPI FHIR ; endpoints `/fhir/**`.
- **Critère** : `GET /fhir/Patient/{id}` renvoie une ressource FHIR valide.

### P2.2 — Diagnostics CIM-10 codés (fin du texte libre)
- [ ] Statut : todo
- **Pourquoi** : diagnostic actuellement en texte libre → épidémiologie et facturation par acte fragiles.
- **Étapes** : table de référence `icd10_catalog` + auto-complétion dans la consultation ; garder le texte libre en complément.

### P2.3 — Export PDF / Excel binaire
- [ ] Statut : todo (déjà différé faute de lib au pom)
- **Pourquoi** : ordonnances, bulletins labo/imagerie, reçus, rapports. Aujourd'hui HTML print-only.
- **Étapes** : iText/JasperReports + Apache POI au pom ; activer `format=pdf|excel` sur `/api/reports/*` et boutons « PDF » sur les bulletins/reçus.

### P2.4 — Portail patient
- [ ] Statut : todo (rôle `PATIENT` existe déjà, non câblé)
- **Pourquoi** : accès patient à ses résultats/RDV — attendu réglementaire.
- **Étapes** : espace `/portal/**` gated `PATIENT` : voir son dossier (lecture), ses RDV, demander un RDV.

### P2.5 — Chiffrement (transit + repos)
- [ ] Statut : todo
- **Étapes** : HTTPS aussi en LAN (reverse-proxy + certif interne) ; volume DB chiffré ou colonnes PHI sensibles chiffrées ; uploads sur volume chiffré.

---

## 🟡 P3 — Différenciation & contexte africain

- [ ] **P3.1 — Mode hors-ligne / PWA** : critique pour LAN + coupures (la force de Bahmni). Service worker + cache, file de synchro.
- [ ] **P3.2 — i18n FR/EN/AR** : `messages.properties` + résolveur de locale (planifié, non fait — templates en FR en dur).
- [ ] **P3.3 — Webhook Mobile Money réel** (Orange/Wave/MTN) : aujourd'hui saisie manuelle ; automatiser la confirmation de paiement.
- [ ] **P3.4 — Accessibilité WCAG 2.2** : contraste, navigation clavier, ARIA, tailles de cible sur les templates.
- [ ] **P3.5 — Recherche globale + raccourcis clavier** : réduction des clics (tendance UX forte).
- [ ] **P3.6 — Vue patient « coup d'œil » + timeline** : résumé (allergies, constantes récentes, alertes) + timeline chronologique unifiée par-dessus les onglets existants.
- [ ] **P3.7 — Télémédecine légère** : lien de téléconsultation (sans IA), tendance forte et faisable.

---

## ⚪ P4 — Haut de gamme / plus tard

- [ ] **P4.1 — Scribe IA ambiant** : transcription consultation → note structurée (avec un modèle Claude). *La* tendance phare 2026 — après les bloquants.
- [ ] **P4.2 — Multi-tenant** : une instance = plusieurs cliniques (déjà roadmap Phase 5).
- [ ] **P4.3 — Spring Actuator + monitoring** (Prometheus/Grafana, UptimeRobot) — esquissé au module 15.
- [ ] **P4.4 — Refresh tokens + révocation JWT** : aujourd'hui token 24h non révocable.

---

## Journal de progression (à remplir à chaque session)

| Date | Item | Résultat |
|---|---|---|
| 2026-06-19 | (création) | Backlog créé suite à l'analyse comparative + audit code. Bug RBAC `mod`→`navMod` corrigé (commit 4ed3535). |
| 2026-06-19 | **P1.1** | Profils dev/prod + secrets externalisés (fail-fast), seed démo gated `!prod`, bootstrap admin prod via env, compose+`.env.example`. Vérifié dev/prod/fail-fast. Boot Postgres réel à confirmer au déploiement (Docker indispo en local). |

---

## Sources de l'analyse (2026-06-19)

- Marché EHR : [Software Finder — Top EHR Vendors 2026](https://softwarefinder.com/resources/largest-ehr-vendors) · [Commure — Best EMR 2026](https://www.commure.com/blog-scribe/best-emr-software) · [CapMinds — OpenMRS](https://www.capminds.com/blog/openmrs-the-open-source-emr-solution-for-your-small-practice/) · [Best in KLAS 2026](https://www.healthcareitnews.com/news/best-klas-2026-sees-positive-disruption-tangible-tech-improvements)
- Sécurité : [IT's ASAP — HIPAA 2025](https://www.itsasap.com/blog/hipaa-security-best-practices) · [Accountable — App security PHI](https://www.accountablehq.com/post/healthcare-application-security-hipaa-compliant-best-practices-to-protect-phi) · [OWASP10 — HIPAA & cybersécurité](https://owasp10.com/hipaa-compliance-and-cybersecurity-best-practices-medical-systems-computer-security/)
- UI/UX : [Excellent WebWorld — Trends 2026](https://www.excellentwebworld.com/healthcare-ux-ui-design-trends/) · [UX Studio — 10 trends 2026](https://www.uxstudioteam.com/ux-blog/healthcare-ux)
- Interopérabilité : [Momentum — FHIR/HL7](https://www.themomentum.ai/blog/fhir-hl7-the-foundation-of-healthtech-interoperability) · [PureLogics — Standards 2025](https://purelogics.com/top-interoperability-standards/)
