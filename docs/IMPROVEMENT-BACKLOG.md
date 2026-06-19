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
- [x] Statut : **fait** (2026-06-19)
- **Réalisé** : nouveau pkg `com.clinic.backend.audit` — `AuditLog` (entité append-only), `AuditLogRepository` (search filtré + `distinctEntityTypes/Actions`), `AuditService` (`record` en `@Transactional(REQUIRES_NEW)`, résout auteur via `SecurityContextHolder` + IP/User-Agent via `RequestContextHolder`, **n'échoue jamais vers l'appelant**), annotation `@Audited(action, entity)` + `AuditAspect` (`@AfterReturning`, entityId déduit du `getId()` du retour sinon 1er arg `Long`). `V14__audit_log_table.sql`. Vue admin lecture seule `/admin/audit` (`AdminAuditWebController`, `@PreAuthorize("hasRole('ADMIN')")`, filtres utilisateur/entité/action/dates, cap 300) + `admin/audit/list.html`. Nav : entrée `ADMIN_AUDIT` (📜) dans `Module` (auto-visible ADMIN via `EnumSet.allOf`). 14 méthodes annotées : Patient create/update, Lab create/validate/cancel, Invoice create/payment/cancel, Consultation create/complete, Dispensation dispense, Hospitalization admit/discharge/transfer.
- **Vérifié** : Flyway→v14, build OK, app démarre ; create patient + record payment + cancel lab → **3 lignes d'audit** avec auteur réel (`admin`), entityId (Patient #4, Invoice #1, LabRequest #2) et IP (`::1`) ; les actions métier réussissent (302) → la trace en tx séparée ne casse rien ; filtres entité/action OK ; non-admin (laborantin) → **403**.
- **Pourquoi** : manipulation de données médicales → exigence légale (HIPAA-like). **Aucun** audit aujourd'hui. Déjà listé « différé » au module 15.
- **Où** : nouveau pkg `com.clinic.backend.audit`.
- **Étapes** :
  1. Table `audit_log` (V14) : `id, user_id, username, action, entity_type, entity_id, ip_address, user_agent, details, created_at`.
  2. Capture via AOP `@Aspect` autour des `service` (create/update/delete/transitions de statut) **ou** `@EntityListener` JPA (`@PostPersist/@PostUpdate/@PostRemove`).
  3. Vue admin `/admin/audit` (lecture seule, filtres user/entité/date), `@PreAuthorize("hasRole('ADMIN')")`.
- **Critère d'acceptation** : créer/modifier un patient, valider un labo, encaisser une facture → 1 ligne `audit_log` chacune avec l'utilisateur réel.

### P1.3 — Anti-brute-force (verrouillage + rate-limit login)
- [x] Statut : **fait** (2026-06-19)
- **Réalisé** : `V15__user_login_lockout.sql` ajoute `failed_attempts INT NOT NULL DEFAULT 0` + `locked_until TIMESTAMP` sur `users` ; `User` porte les champs et `isAccountNonLocked()` = `lockedUntil == null || lockedUntil < now()` (auto-déverrouillage à expiration). `LoginAttemptService` (`@Transactional`) : `loginFailed` incrémente (un verrou expiré repart de 0), verrouille 15 min à `MAX_ATTEMPTS=5` ; `loginSucceeded` remet compteur+verrou à zéro. Branché **par événements** Spring Security (`AuthenticationEventListener` : `AuthenticationSuccessEvent`/`AuthenticationFailureBadCredentialsEvent`) → couvre web **et** API d'un coup ; `LockedException` ignorée (sinon le verrou se prolongerait). `DefaultAuthenticationEventPublisher` exposé en bean (sinon publisher no-op). `LoginFailureHandler` (web) route `LockedException`→`/login?locked=true`, reste→`/login?error=true` ; message « Compte verrouillé… 15 minutes » dans `login.html`. `AuthController.login` (API) catch → **423 Locked** / **401** JSON propre (au lieu d'un 500 brut). On n'écoute pas les `BadCredentials` du `JwtFilter` (il pose l'auth en direct, pas d'événement → pas de bruit par requête).
- **Vérifié** (H2, app bootée Flyway→v15) : **API** 5 mauvais mdp sur `admin` → 5×401 JSON, puis bon mdp → **423 Locked** (verrou actif) ; `dr.martin` intact → 200. **Reset prouvé** : 4 échecs + 1 succès ×2 (8 échecs au total) → jamais verrouillé, dernier succès 200. **Web** (avec token CSRF + session) : `admin` verrouillé → **302 → `/login?locked=true`**.
- **Pourquoi** : aucun lockout ni rate-limit ; `/login` et `/api/auth/login` exposés au bourrinage.
- **Où** : `User` (a déjà `active`), `SecurityConfig`, `service` auth.
- **Étapes** :
  1. Colonnes `failed_attempts INT`, `locked_until TIMESTAMPTZ` sur `users` (V15).
  2. `AuthenticationFailureHandler` : incrémente ; à N (ex. 5) → `locked_until = now()+15min`. Succès → reset.
  3. `isAccountNonLocked()` reflète `locked_until`.
  4. (Option) rate-limit IP via Bucket4j sur les endpoints de login. — **non fait** (lockout par compte suffit pour le critère ; rate-limit IP reportable si besoin DoS distribué).
- **Critère d'acceptation** : 5 mauvais mots de passe → compte verrouillé 15 min, message clair. — ✅

### P1.4 — `@RestControllerAdvice` global (fin des 500 bruts)
- [x] Statut : **fait** (déjà livré par le commit `e2db645` du 2026-06-14, *avant* la rédaction de ce backlog — la prémisse « il manque le pendant API » était fausse). Vérifié 2026-06-19.
- **Constat** : `config/GlobalExceptionHandler.java` est un `@RestControllerAdvice(basePackages = "com.clinic.backend.controller.api")` — il couvre donc **les 16 contrôleurs API** (tous dans `controller.api`). Mapping en place : `MethodArgumentNotValidException`→400, `IllegalArgumentException`→400, `IllegalStateException`→**409**, `AuthenticationException`→401, `AccessDeniedException`→403, `ResourceNotFoundException`→404, fallback `Exception`→500, au format standard `{timestamp,status,error,message,path}`. Le NB « no global @RestControllerAdvice » répété dans `CLAUDE.md` est **obsolète** (historique d'avant le 2026-06-14).
- **Scope volontairement étroit** : l'advice ne vise que `controller.api`, **pas** `com.clinic.backend.controller` au sens large — sinon il avalerait les exceptions des contrôleurs `controller.web` (vues Thymeleaf) et les rendrait en JSON. Le seul `@RestController` hors `controller.api` est `AuthController` (`controller`), dont les erreurs sont gérées **inline** (423/401, fait en P1.3).
- **Vérifié** (H2, JWT admin) : surpaiement facture → **400** JSON standard (`Le montant dépasse le reste à payer (6000.00)`) ; facture déjà soldée → **409** JSON (`Cette facture est déjà soldée`) ; **plus aucun 500 brut**.
- **Raffinement optionnel restant** : le not-found renvoie **400**, pas 404 — les services lèvent `IllegalArgumentException("… introuvable")` au lieu de `ResourceNotFoundException` (dont le handler 404 existe pourtant). Corriger demanderait de remplacer ces throws dans ~14 services ; reportable (hors acceptance, qui porte sur insuffisant/surpaiement).
- **Critère d'acceptation** : dispensation insuffisante / surpaiement via API → JSON propre, plus de 500. — ✅ (mapping réel : insuffisant/surpaiement = `IllegalArgumentException`→**400** ; conflits d'état type « déjà dispensée/soldée » →**409**).

### P1.5 — Tests (sécurité + invariants métier)
- [x] Statut : **fait** (2026-06-19) — **26 tests verts** (`mvnd test` BUILD SUCCESS).
- **Réalisé** : `spring-security-test` ajouté au pom ; `src/test/resources/application-test.properties` (profil `test` : H2 dédié + secret JWT de test) ; tests sous `@ActiveProfiles("test")` (DataInitializer seedé, profil `!prod`). 5 classes :
  - `ClinicApplicationTests` — smoke contexte (Flyway migre, beans se câblent, Hibernate valide).
  - `LoginAttemptServiceTest` (5, Mockito pur) — invariants P1.3 : +1 par échec, verrou à 5, reset au succès, verrou expiré repart de 0, utilisateur inconnu/null ne casse rien.
  - `SecurityMatrixTest` (9, MockMvc + `@WithMockUser`) — API sans token refusée, page web sans session → redirect `/login`, `/login` public ; `/admin/audit` (ADMIN 200 / MEDECIN 403), `/reports/dashboard` (MEDECIN 200 / CAISSIER 403), `/reports/financial` (CAISSIER 200 / MEDECIN 403).
  - `PageRenderSmokeTest` (6, MockMvc + `@WithUserDetails("admin")`) — `/dashboard`, `/patients`, `/appointments`, `/billing`, `/reports`, `/admin/audit` → 200 (exerce layout+sidebar = la classe du bug `mod`→`navMod`). Principal réel via `userDetailsServiceBeanName="userDetailsServiceImpl"`.
  - `BusinessInvariantTest` (5, `@SpringBootTest` + `@Transactional` rollback + `@WithUserDetails`) — couverture assurance (80% sur 1000 → ins 800/patient 200), surpaiement rejeté (IllegalArgument), paiement sur facture soldée rejeté (IllegalState), **FIFO** (lot J+30 vidé avant le lot +2 ans : reste 5 sur le lointain), stock insuffisant rejeté.
- **Différé** : **étape 4 (Testcontainers PostgreSQL) non faite** — Docker indisponible en local (même contrainte qu'en P1.1), `mvnd test` doit rester vert ici. À ajouter quand un runtime Docker (CI) sera dispo, pour valider Flyway sur le vrai moteur. Double-admission/transition d'hospitalisation non couvertes (FIFO + couverture + gardes de paiement = invariants les plus à risque, priorisés).
- **Pourquoi** : **0 test** dans `backend/src/test`. Pas de filet pour les régressions (ex. le bug `mod` aurait été attrapé par un test de rendu).
- **Critère d'acceptation** : `mvnd test` vert, matrice de rôles couverte. — ✅ (26 tests, matrice RBAC + rendu + invariants).

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
| 2026-06-19 | **P1.2** | Journal d'audit : pkg `audit` (`@Audited` + AOP `@AfterReturning`, écriture `REQUIRES_NEW`), V14, vue admin `/admin/audit` (ADMIN). 14 méthodes auditées. Vérifié : 3 actions → 3 traces (auteur/entité/id/IP), filtres OK, non-admin 403. |
| 2026-06-19 | **P1.3** | Anti-brute-force : V15 (`failed_attempts`/`locked_until`), `LoginAttemptService` (5 échecs → verrou 15 min, reset au succès) branché par événements Spring Security (web+API), `isAccountNonLocked()` câblé, `LoginFailureHandler`→`?locked=true`, API→423/401 JSON. Vérifié : API 5×401→423, autre compte 200, reset prouvé (8 échecs entrecoupés de succès → jamais verrouillé), web 302→`/login?locked=true`. Rate-limit IP (Bucket4j) non fait. |
| 2026-06-19 | **P1.4** | **Déjà fait** (commit `e2db645`, 2026-06-14, avant ce backlog). `GlobalExceptionHandler` = `@RestControllerAdvice` scopé `controller.api` (couvre les 16 contrôleurs API) : IllegalArgument→400, IllegalState→409, ResourceNotFound→404, Auth→401, AccessDenied→403, fallback→500, format standard. Vérifié : surpaiement→400 JSON, facture soldée→409 JSON, plus de 500 brut. NB CLAUDE.md « no @RestControllerAdvice » obsolète. Raffinement reporté : not-found→400 (services lèvent IllegalArgument au lieu de ResourceNotFound) — à faire en « P1.4b » après P1.5. |
| 2026-06-19 | **P1.5** | Tests : **0 → 26 verts** (`mvnd test` OK). `spring-security-test` + profil `test` (H2). 5 classes : smoke contexte, `LoginAttemptServiceTest` (P1.3), `SecurityMatrixTest` (matrice RBAC MockMvc), `PageRenderSmokeTest` (anti-régression templating), `BusinessInvariantTest` (couverture assurance, surpaiement, facture soldée, FIFO, stock insuffisant). Testcontainers PostgreSQL différé (Docker indispo local). |

---

## Sources de l'analyse (2026-06-19)

- Marché EHR : [Software Finder — Top EHR Vendors 2026](https://softwarefinder.com/resources/largest-ehr-vendors) · [Commure — Best EMR 2026](https://www.commure.com/blog-scribe/best-emr-software) · [CapMinds — OpenMRS](https://www.capminds.com/blog/openmrs-the-open-source-emr-solution-for-your-small-practice/) · [Best in KLAS 2026](https://www.healthcareitnews.com/news/best-klas-2026-sees-positive-disruption-tangible-tech-improvements)
- Sécurité : [IT's ASAP — HIPAA 2025](https://www.itsasap.com/blog/hipaa-security-best-practices) · [Accountable — App security PHI](https://www.accountablehq.com/post/healthcare-application-security-hipaa-compliant-best-practices-to-protect-phi) · [OWASP10 — HIPAA & cybersécurité](https://owasp10.com/hipaa-compliance-and-cybersecurity-best-practices-medical-systems-computer-security/)
- UI/UX : [Excellent WebWorld — Trends 2026](https://www.excellentwebworld.com/healthcare-ux-ui-design-trends/) · [UX Studio — 10 trends 2026](https://www.uxstudioteam.com/ux-blog/healthcare-ux)
- Interopérabilité : [Momentum — FHIR/HL7](https://www.themomentum.ai/blog/fhir-hl7-the-foundation-of-healthtech-interoperability) · [PureLogics — Standards 2025](https://purelogics.com/top-interoperability-standards/)
