# Desktop packaging + Licence/Essai — Plan

> Statut : **plan validé, pas encore implémenté** (2026-07-06).
> Décisions cadrées : **topologie tout-en-un par clinique**, **PostgreSQL embarqué**, **commencer par un plan écrit**.
> Source de vérité "quoi ensuite" une fois lancé : cocher les cases ci-dessous + journal en bas.

---

## 0. Décisions actées

| Sujet | Décision | Conséquence |
|---|---|---|
| Modèle de distribution | **Tout-en-un** : un poste "serveur" par clinique embarque app + base ; les autres postes tapent le LAN au navigateur | Installeur autonome, s'appuie sur le wizard `/setup` existant |
| Base en mode desktop | **PostgreSQL embarqué** (fidélité prod) | Bundling plus lourd, gestion de cycle de vie du process PG à écrire ; migrations Flyway **inchangées** (même dialecte que la prod) |
| Packaging | **jpackage** (JDK natif) en v1, Tauri/auto-update en v2 optionnel | Reste 100 % Java, pas de toolchain Rust/Node |
| Licence | **Clé signée Ed25519, vérifiée hors-ligne** | Pas de dépendance réseau ; incontournable pour cliniques à connectivité faible |
| Paiement | **Manuel + outil de génération de clé** en v1 ; Lemon Squeezy/Paddle + webhook en v2 | Colle au marché Niger (pas d'API mobile money grand public) |
| Blocage à l'expiration | **Écritures cliniques + facturation bloquées** ; lecture + export/backup + `/license` toujours ouverts ; bandeau dès J-7 | Non-négociable médical : jamais couper la lecture des dossiers ni séquestrer les données |
| Signature de code | **Différée en v1** (installs sur site assistées) ; puis **Azure Trusted Signing** (~10$/mois, cloud, sans token) quand le volume le justifie | EV reste l'option "réputation instantanée" si besoin ; vérifier l'éligibilité géographique d'Azure |

**Point de vigilance PostgreSQL embarqué** (choix assumé) : c'est le poste le plus risqué du packaging. Bibliothèque cible = `io.zonky.test:embedded-postgres` (binaire PG réel, lancé sur un port local avec un data-dir persistant). Risques à traiter explicitement en phase 2 : (a) premier démarrage lent (extraction du binaire), (b) conflit de port si PG déjà installé sur le poste, (c) data-dir à placer hors dossier programme (droits Windows), (d) arrêt propre de PG à la fermeture de l'app (sinon corruption). Un fallback H2-fichier derrière un flag reste une soupape de secours si le terrain se révèle hostile.

---

## Phase 1 — Profil Spring `desktop` + PostgreSQL embarqué ✅ (2026-07-06)

**But :** l'app démarre en double-clic, base persistante locale, zéro config manuelle.

- [x] Profil `desktop` (`application-desktop.properties`) : hérite `ddl-auto=validate` + Flyway ON de `application.properties` ; cookies non-Secure (localhost HTTP) ; réglages `app.desktop.*`. DataSource fourni par bean (pas de `spring.datasource.*`).
- [x] `desktop/DesktopDatabaseConfig` (`@Profile("desktop")`) : bean `EmbeddedPostgres` (zonky 2.2.2) — data-dir persistant `%LOCALAPPDATA%\ClinicApp\db`, port 15432, `cleanDataDirectory=false`, working-dir d'extraction fixé, `registerShutdownHook=false` (Spring ordonne l'arrêt), `destroyMethod=close`. Bean `@Primary DataSource` = `pg.getPostgresDatabase()`. Ordre PG-avant-datasource garanti par dépendance de bean.
- [x] Secrets sans `.env` : `desktop/DesktopSecretsEnvironmentPostProcessor` (enregistré `META-INF/spring.factories`) génère au 1er lancement + persiste `<home>\secrets.properties` (`app.jwt.secret`, `app.encryption.key`, `app.webhook.secret`, `app.monitoring.password`) et fixe `app.storage.upload-dir` sous le home. Ne remplit que l'absent (override env possible). Chemins centralisés dans `desktop/DesktopPaths` (surcharge `CLINICAPP_HOME`).
- [x] Data-dir persistant + détection "première install vs existante" (log `initdb` vs "base réutilisée").
- [x] Pas de seed démo en desktop : `DataInitializer` re-gated `@Profile("!prod & !desktop")`. `/setup` reste la porte d'entrée.
- [~] Sauvegarde locale automatique (`desktop/DesktopBackupService`, `@Scheduled` nuit + au démarrage, rétention N jours, dégradation propre). **Limite constatée : les binaires zonky « réduits » n'incluent PAS `pg_dump`** (bin/ = initdb/pg_ctl/postgres seulement). → chemin `app.desktop.backup.pg-dump-path` ajouté ; **fourniture du binaire pg_dump officiel reportée au packaging (Phase 2)**, où l'on bundle les binaires de toute façon. Sans lui : backups désactivés + warning clair (l'app tourne normalement).

**Vérifié end-to-end (profil desktop, home isolé) :** PostgreSQL embarqué démarre (v14.22, processus `postgres.exe`), **les 37 migrations Flyway passent inchangées** sur le vrai PG, JPA validate OK, app `Started` en ~31 s, `/setup` → 200, `/actuator/health` → UP, secrets générés+persistés, aucun compte démo seedé. Arrêt propre.

**NB pooling :** `getPostgresDatabase()` renvoie un `PGSimpleDataSource` **non poolé**. OK à l'échelle mono-clinique ; à envelopper dans Hikari si la latence de connexion gêne (amélioration, non bloquant).

**Fallback documenté :** flag `app.desktop.db=h2` (repli fichier) — non implémenté (PG embarqué validé), à ajouter seulement si le terrain se révèle hostile.

---

## Phase 2 — Packaging jpackage ✅ app-image (2026-07-07)

**But :** paquet Windows autonome, JRE embarqué, lancement d'un clic.

- [x] `jpackage` (JDK 24) → **app-image** portable `dist\ClinicApp\ClinicApp.exe` (launcher natif + runtime JRE bundlé + jar Spring Boot). Profil `desktop` figé via `--java-options -Dspring.profiles.active=desktop` + `-Djava.awt.headless=false`. Script `packaging\build-desktop.ps1` (+ `packaging\README.md`).
- [x] **Slimmé** sur `embedded-postgres-binaries-windows-amd64` seul (exclusions pom des binaires darwin/linux/alpine) — vérifié : le jar packagé ne contient QUE le binaire Windows.
- [x] **Launcher** : l'app **est** le serveur ; `DesktopBrowserOpener` (@Profile desktop, sur `ApplicationReadyEvent`) ouvre le navigateur système sur `http://localhost:8080/` (désactivable `app.desktop.open-browser=false`). *(v2 optionnelle : WebView JavaFX pour une fenêtre sans barre navigateur.)*
- [x] Nom/vendor/version dans jpackage.
- [~] **Installeur `.msi`/`.exe`** : le script le produit avec `-Installer` **si WiX est sur le PATH** ; WiX absent sur ce poste → app-image livrée (zippable). Installer WiX Toolset pour générer le `.msi` (menu démarrer, désinstalleur, dir-chooser déjà câblés).
- [ ] **`pg_dump.exe` officiel** (PostgreSQL 14) : à fournir via `-PgDump` (le script le copie dans `app\`) + pointer `app.desktop.backup.pg-dump-path` → active les sauvegardes (report Phase 1 toujours ouvert ; pas de PG14 client sous la main ici).
- [ ] **jlink runtime minimal** : jpackage bundle actuellement le runtime JDK complet (image ~293 Mo). Générer un runtime `jlink` ciblé (via `jdeps`) en `--runtime-image` pour réduire — optimisation, non bloquante.
- [ ] **Signature de code** : différée (installs sur site assistées). Le jour venu → **Azure Trusted Signing** (cloud, sans token) sur le `.exe`/`.msi`, sinon EV. Étape « Exécuter quand même » à documenter côté client.
- [ ] **Icône** de marque (`-Icon xxx.ico`) + **auto-update** (v2 : Velopack/update4j).

**Vérifié :** app-image construite (293 Mo, JRE bundlé), **`ClinicApp.exe` lancé** (home isolé) → PostgreSQL embarqué démarre, secrets auto-générés, Tomcat 8080, `Started` ~49 s, `/actuator/health` UP, `/setup` 200, `/` → 302 /login. Suite complète : **309 verts, 0 régression**. Reste à valider sur une **VM Windows vierge** (sans Java installé) pour prouver l'autonomie du runtime bundlé.

---

## Phase 3 — Module `license` + mode d'essai ✅ (2026-07-06)

**But :** l'app passe en lecture seule sans licence valide ni essai actif ; activation hors-ligne.

Architecture (miroir des patterns `setup` / `mfa`), pkg `com.clinic.backend.license` :

- [x] **Ed25519** natif JDK (pas de BouncyCastle). Clé **publique** éditeur embarquée (`app.license.public-key`, défaut = clé réelle générée) ; clé **privée** hors dépôt.
- [x] Format = payload JSON signé → jeton `base64url(payload).base64url(sig)` (`License` + `LicenseCodec`). Champs : id, clinic, edition, features[], maxUsers, issued, expires.
- [x] `LicenseService` (cache 60 s + flag enforce) + `LicenseCalculator` (`@Transactional`, séparé pour éviter l'auto-invocation) : état `DISABLED/ACTIVE/TRIAL/EXPIRED`, jours restants. Vérif **hors-ligne**.
- [x] Stockage : table dédiée **`license_activation`** (installation-globale, pas de `@TenantId`) — `V38__license_activation.sql`.
- [x] **`LicenseGuardInterceptor`** (enregistré dans `WebConfig`) : en état bloqué, **seules les écritures** (POST/PUT/PATCH/DELETE) sont refusées → 302 `/license?blocked` ; GET/HEAD (lecture + export) toujours OK. Exclut `/license`, auth, MFA, setup, statiques, endpoints machine.
- [x] Page `/license` (`LicenseWebController` + `license/activate.html`) : coller la clé → vérif → activation ; activation réservée `OWNER/ADMIN/SUPER_ADMIN`.
- [x] Bandeau essai/expiration via `GlobalModelAdvice` (`licenseState`) rendu dans `layouts/base.html`.
- [x] **Enforcement OPT-IN** (`app.license.enforce`, défaut false) : dev/test/prod-web jamais bloqués et n'écrivent aucun marqueur → **309 tests verts, 0 régression**. Activé (`true`) dans `application-desktop.properties`.

**Mode d'essai :**
- [x] Premier démarrage sous licence → essai **30 j** (`app.license.trial-days`), `trial_started_at` écrit.
- [x] **Anti-triche raisonnable** (`TrialStore`) : date de début en **3 sources** (DB + fichier `<home>/license.trial` + registre `HKCU\Software\ClinicApp`), on retient la **plus ancienne** (min) et on réaligne les autres (un reset d'une source ne rallonge pas l'essai) ; recul d'horloge > 1 j → état bloqué. Best-effort, jamais bloquant.
- [x] À expiration : **écritures bloquées (read-only)**, lecture + export toujours ouverts, page `/license` accessible. Bandeau « lecture seule ».
- [x] Renouvellement/abonnement = `expires` dans la clé → réémission d'une nouvelle clé (`LicenseKeyTool`).
- [x] **Outil d'émission** `LicenseKeyTool` (`keygen` + `issue`, hors-ligne, base de la CLI Phase 4).

**Vérifié end-to-end (profil desktop, home isolé) :** (1) `LicenseCodecTest` 5/5 — sign/verify Ed25519, rejet altération + mauvaise clé ; (2) install fraîche → bandeau **essai J-30** + accès complet ; (3) activation d'une clé signée via l'UI web (CSRF) → **ACTIVE (STANDARD, valide 2027-08-10)**, bandeau essai disparu ; (4) essai expiré (`trial-days=-1`) → GET 200 + bandeau « lecture seule », **POST /profile/password → 302 /license?blocked**, `/setup` + `/login` OK pendant le blocage.

**NB :** l'enforcement prod-web reste à `false` (focus commercial = desktop) ; l'activer serait un simple flag pour un SaaS licencié. Un pré-avertissement dédié « J-7 » n'a pas de bandeau distinct : le bandeau essai est permanent et passe en niveau « warn » à ≤ 7 j.

---

## Phase 4 — Génération de clés + paiement

**But :** transformer un paiement en clé signée, d'abord manuellement.

- [~] **Outil CLI interne** (offline, clé privée) : socle livré en Phase 3 = `LicenseKeyTool` (`keygen`, `issue <cléPrivée> <clinique> <edition> <jours> [maxUsers]`). Reste à emballer en CLI conviviale (args nommés) + doc d'usage éditeur. Ne JAMAIS embarquer la clé privée dans l'app distribuée.
- [ ] **Flux v1 (manuel, marché Niger)** : client paie (virement / mobile money / espèces via revendeur) → tu génères la clé avec le CLI → tu l'emailes. Suffisant pour lancer.
- [ ] **Flux v2 (automatisé, international)** : Lemon Squeezy ou Paddle (Merchant of Record, gèrent la TVA) → webhook d'achat → service qui génère + envoie la clé automatiquement.

---

## Ordre recommandé & rationale

Si l'objectif premier est **vendre**, faire **Phase 3 (licence) avant Phase 2 (packaging)** : c'est du pur Java, dans tes patterns, testable en `mvnd`, et ça débloque la monétisation indépendamment de la mécanique d'installeur. Si l'objectif premier est **livrer un exécutable**, faire 1 → 2 d'abord.

Séquence par défaut proposée : **1 (base) → 3 (licence) → 2 (packaging) → 4 (paiement)**.

---

## Journal de progression

| Date | Phase | Note |
|---|---|---|
| 2026-07-06 | — | Plan créé et validé (topologie tout-en-un, PG embarqué, plan-first). Rien implémenté. |
| 2026-07-06 | — | 2 arbitrages tranchés : expiration = écritures bloquées / lecture+export toujours ouverts / bandeau J-7 ; signature = différée puis Azure Trusted Signing. |
| 2026-07-07 | 2 | **Phase 2 (app-image) implémentée + vérifiée** : `packaging/build-desktop.ps1` (jpackage), `DesktopBrowserOpener`, binaires PG slimmés windows-amd64, `dist/` gitignoré. ClinicApp.exe packagé boote (PG embarqué, /setup 200, health UP). Reste : `.msi` (WiX absent ici), pg_dump officiel, jlink minimal, signature, VM vierge. 309 tests verts. |
| 2026-07-06 | 3 | **Phase 3 implémentée + vérifiée** (pkg `license` : Ed25519 offline, LicenseService/Calculator, TrialStore 3-sources, LicenseGuardInterceptor read-only, page /license, bandeau, LicenseKeyTool, V38). Enforcement opt-in (desktop true, ailleurs false). E2E desktop : essai J-30 → activation ACTIVE → expiré=lecture-seule (POST bloqué, GET OK). Suite : 309 verts / 0 régression. Clé publique éditeur embarquée ; clé privée hors dépôt. |
| 2026-07-06 | 1 | **Phase 1 implémentée + vérifiée** (zonky embedded-postgres 2.2.2, profil desktop, EPP secrets, DataInitializer re-gated). Boot desktop OK : 37 migrations Flyway sur PG 14.22 réel, /setup 200, health UP. Report Phase 2 : bundler `pg_dump` officiel (binaires zonky réduits sans pg_dump) → active les sauvegardes auto. Option future : wrapper Hikari sur le DataSource. |
