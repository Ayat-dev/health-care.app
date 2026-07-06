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

## Phase 2 — Packaging jpackage

**But :** un installeur Windows signé, JRE embarqué, lancement d'un clic.

- [ ] `jlink` → JRE minimal (modules réellement utilisés).
- [ ] `jpackage` → `.msi`/`.exe` avec JRE bundlé + binaire PG + le JAR Spring Boot.
- [ ] **Slimmer les binaires PG** sur `embedded-postgres-binaries-windows-amd64` seul (le pom tire actuellement le bundle multi-plateforme).
- [ ] **Fournir `pg_dump.exe` officiel** (PostgreSQL 14 Windows) dans l'installeur + pointer `app.desktop.backup.pg-dump-path` dessus → active les sauvegardes automatiques (report de Phase 1 : les binaires embarqués « réduits » ne l'incluent pas).
- [ ] **Launcher** : au lancement, démarre le serveur (profil `desktop`), attend `/actuator/health` UP, puis ouvre l'UI.
  - v1 : ouvre le **navigateur système** sur `http://localhost:PORT` (le plus simple).
  - v2 optionnelle : **WebView JavaFX** pour une fenêtre "app" sans barre navigateur (réutilise le skill JavaFX gelé du dossier `desktop/`).
- [ ] **Signature de code : différée en v1.** Installs sur site assistées → l'avertissement SmartScreen est cliqué par le technicien, pas le client final. Documenter l'étape « Exécuter quand même ». Le jour venu : **Azure Trusted Signing** (~10$/mois, cloud, sans token matériel, réputation SmartScreen progressive ; vérifier éligibilité géo) ; **EV** (~300-600 €/an + token) si réputation instantanée requise.
- [ ] Icône, nom, entrée menu démarrer, désinstalleur.
- [ ] **Auto-update** : v1 = installeurs versionnés manuels. v2 = Tauri sidecar / `update4j` / Velopack si le besoin se confirme.

**Vérif :** install sur une VM Windows vierge (sans Java installé) → l'app démarre et `/setup` s'affiche.

---

## Phase 3 — Module `license` + mode d'essai

**But :** l'app refuse de servir les écritures cliniques sans licence valide OU essai actif ; activation hors-ligne.

Architecture (miroir des patterns existants `setup` / `mfa`) :

- [ ] Paire **Ed25519** générée hors-ligne. Clé **publique** embarquée dans l'app ; clé **privée** jamais distribuée (coffre perso).
- [ ] Format de licence = payload signé :
  ```
  { licenceId, clinique, edition, features[], maxUsers, dateEmission, dateExpiration }
  + signature Ed25519
  → encodé base64 en segments type XXXX-XXXX-XXXX
  ```
- [ ] `LicenseService` : vérifie la signature avec la clé publique (**hors-ligne, zéro réseau**), expose l'état courant (ESSAI / ACTIVE / EXPIRÉE / INVALIDE), jours restants.
- [ ] Stockage de la clé activée dans `clinic_config` (étendu) ou table `license`.
- [ ] **`LicenseGuardInterceptor`** (clone de `SetupGuardInterceptor`/`MfaGuardInterceptor`, enregistré dans `WebConfig`) : pas de licence valide ni essai actif → redirige vers `/license`. Laisse passer `/license`, assets, `/actuator/health`.
- [ ] Page `/license` : coller la clé → vérif → activation + flash.
- [ ] Bandeau "Essai : J-N" / "Licence expirée" via `GlobalModelAdvice` (déjà injecteur d'attributs par page).

**Mode d'essai :**
- [ ] Premier `/setup` sans licence → démarre un essai **30 j**, `trial_started_at` écrit.
- [ ] **Anti-triche raisonnable** (un desktop est toujours contournable — viser l'honnête, pas le blindé) : écrire la date de début en **3 endroits** (DB `clinic_config` + fichier hors dossier app + registre Windows), prendre le **max** ; horloge qui recule / incohérence → essai considéré expiré. Ne pas sur-investir.
- [ ] À expiration : **blocage des écritures cliniques + facturation** (read-only). **Carve-out dur** : lecture de tout + export/backup + page `/license` restent toujours ouverts (jamais couper l'accès aux dossiers médicaux — non-négociable juridique/médical). Bandeau d'avertissement dès **J-7** avant bascule.
- [ ] Renouvellement/abonnement = `dateExpiration` dans la clé → réémission annuelle d'une nouvelle clé.

**Vérif :** clé valide → ACTIVE ; clé trafiquée → INVALIDE (signature KO) ; horloge avancée au-delà de 30 j → essai expiré → `/license`.

---

## Phase 4 — Génération de clés + paiement

**But :** transformer un paiement en clé signée, d'abord manuellement.

- [ ] **Outil CLI interne** (offline, utilise la clé privée) : `generate-license --clinique "X" --edition PRO --expire 2027-07-06` → imprime la clé. Ne JAMAIS embarquer la clé privée dans l'app distribuée.
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
| 2026-07-06 | 1 | **Phase 1 implémentée + vérifiée** (zonky embedded-postgres 2.2.2, profil desktop, EPP secrets, DataInitializer re-gated). Boot desktop OK : 37 migrations Flyway sur PG 14.22 réel, /setup 200, health UP. Report Phase 2 : bundler `pg_dump` officiel (binaires zonky réduits sans pg_dump) → active les sauvegardes auto. Option future : wrapper Hikari sur le DataSource. |
