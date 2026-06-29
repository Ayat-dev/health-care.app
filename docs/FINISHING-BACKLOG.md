# ClinicApp — Tracker des finitions (multi-tenant · PWA · A11y · Divers)

> **But** : finir proprement les chantiers **multi-tenant**, **PWA**, **accessibilité** et un
> bucket **Divers** (durcissement/polish), **sans perte de contexte entre sessions**.
>
> **⚠️ LIRE UNIQUEMENT CE FICHIER en session « finitions ».** Il est *self-contained* : ne PAS
> relire `IMPROVEMENT-BACKLOG.md` (52k tokens, périmé sur le multi-tenant) ni `CLAUDE.md` en
> entier — tout le contexte utile pour ces chantiers est ici. `IMPROVEMENT-BACKLOG.md` reste
> l'historique P1→P6 (référence ponctuelle), ce fichier est la **source de vérité des finitions**.
>
> **Créé** : 2026-06-29. **Base de référence vérifiée ce jour** : `mvnd test` → **189 verts**,
> BUILD SUCCESS ; **multi-tenant = ISOLATION COMPLÈTE** (V20→V30, validé PostgreSQL 16.14).

---

## 🔒 Conventions & protocole (verrouillés — ne pas redécouvrir chaque session)

- **Build/test** : `mvnd` (jamais `mvn`). Après chaque slice : `mvnd test` doit rester **vert**.
- **Workflow par slice** : 1 slice = 1 session. À la fin : `mvnd test` → cocher `[x]` la case +
  remplir **une ligne** au Journal (bas de ce fichier) + `git commit` sur **`main`** (pas de
  branche — décision utilisateur 2026-06-28) + `/clear`.
- **Format commit** : `feat(finish): <slice> — <résumé>` (ou `fix`/`chore`/`test`/`docs`).
- **DB** : nouvelle migration Flyway à chaque changement de schéma (jamais modifier une existante).
  **Dernière version = V30** (8 Java migrations possibles, cf. V28/V30). Migration multi-tenant =
  patron 3 temps portable (`ADD COLUMN nullable` → `UPDATE backfill CENTRALE` → `SET NOT NULL`).
  DROP de contrainte inline non nommée = **migration Java** (introspection `INFORMATION_SCHEMA`,
  cf. `V28__multitenant_hospitalization.java`).
- **Pas de nouveau CSS** : étendre `app.css`. **Pas de CDN** (CSP `default-src 'self'` — clients
  JS natifs, cf. `worklist-live.js`/`search.js`). **i18n** : 3 bundles `messages{,_en,_ar}.properties`,
  toute chaîne visible passe par `#{clé}` (RTL pour `ar`). **PHI jamais en cache offline.**
- **Tenant** : `@TenantId` sur les entités cliniques (filtre/estampille auto). Source de résolution =
  `users.clinic_id` (colonne **simple**, pas `@TenantId`). Tâche de fond → itérer
  `clinicRepository.findByActiveTrue()` + `TenantContext.runAs(id, () -> proxiedService.method())`
  (la session s'ouvre **après** la pose du tenant — piège de timing). Numérotation = **native globale**
  (Hibernate n'applique pas `@TenantId` au SQL natif).
- **Tests** : profil `test` (H2). Timing tenant dans les tests `@Transactional` → poser le tenant en
  `@BeforeTransaction`, et `@WithUserDetails` (pas `@WithMockUser`) pour les assertions de contenu.

---

## 📊 Tableau de bord (cocher au fil de l'eau)

| # | Chantier | Slices | Faits | Statut |
|---|---|---|---|---|
| A | Multi-tenant — finitions | 4 | 0 | 🔲 à démarrer |
| B | PWA — finitions | 4 | 0 | 🔲 à démarrer |
| C | Accessibilité (A11y) — finitions | 3 | 0 | 🔲 à démarrer |
| D | Divers (durcissement/polish) | 8 | 0 | 🔲 à démarrer |
| Z | (Tier 2) Grosses features parquées | — | — | 📦 listées, hors périmètre finitions |

**Ordre conseillé** : A (clôt le multi-tenant, petites slices à forte valeur) → D4a (sécu) →
C (a11y) → B (PWA) → reste de D. Mais chaque slice est indépendante : prendre la plus utile.

---

## A — MULTI-TENANT : finitions

> État : **isolation complète**. Ne restent que 4 finitions (aucune n'est une fuite active).

- [ ] **A1 — Test Testcontainers PostgreSQL permanent (CI-safe).**
  Aujourd'hui la validation PG (V20→V30 sur PG 16.14) a été faite avec un `@SpringBootTest`
  jetable supprimé après coup (non rejouable). Docker tournant maintenant → ajouter un **vrai
  test Testcontainers** : dép. `org.testcontainers:postgresql` + `junit-jupiter` (scope test) ;
  `@SpringBootTest` `@ActiveProfiles("test")` avec `@DynamicPropertySource` pointant sur un
  conteneur `postgres:16` → vérifie (1) les **30 migrations s'appliquent** sur PG, (2) **isolation
  tenant** (CENTRALE ≠ PLATEAU) sur PG, (3) UNIQUE composite `(clinic_id, code)` réutilisable.
  Clôt aussi **P1.5 étape 4** (Testcontainers différé). **Piège connu** : le démon `mvnd` ne
  propage pas l'env shell aux forks de test → passer la datasource via `@DynamicPropertySource`
  (pas de variables d'env). Tag le test `@Tag("testcontainers")` + skip si Docker absent
  (`@EnabledIfDockerAvailable` ou `DockerClientFactory.instance().isDockerAvailable()`) pour
  garder `mvnd test` vert sans Docker.
  *Acceptation* : `mvnd test` vert (test skippé si pas de Docker) ; avec Docker, migrations + isolation prouvées sur PG.

- [ ] **A2 — `enqueueInAppToRole` cloisonné par clinique.**
  `NotificationService.enqueueInAppToRole(role)` résout les destinataires sur **toutes** les
  cliniques → crée des lignes orphelines (la notif est taguée au tenant courant, donc invisible
  aux autres, mais on insère pour des users d'autres cliniques). Filtrer les destinataires sur
  `clinic_id = TenantContext.currentClinicId()`. Vérifier les appelants (`StockAlertService`,
  triggers labo/stock). +1 test d'isolation dans `MultiTenancyTest`.
  *Acceptation* : alerte stock d'une clinique → notifs uniquement pour les users de **cette** clinique.

- [ ] **A3 — Seed catalogues + config pour PLATEAU (démo utilisable).**
  La 2ᵉ clinique seedée (PLATEAU) n'a ni départements, ni actes, ni analyses, ni `clinic_config`
  → inutilisable en démo. Étendre `DataInitializer` (profil `!prod`) : sous
  `TenantContext.runAs(plateauId, …)`, créer un petit jeu de départements/actes/labs + une
  `clinic_config` PLATEAU. (Garder léger — c'est de la démo.)
  *Acceptation* : se connecter en contexte PLATEAU → catalogues + config présents et distincts de CENTRALE.

- [ ] **A4 — Provisionnement SUPER_ADMIN en prod + comptes inter-cliniques.**
  En prod, `ProdDataInitializer` crée une clinique « PRINCIPALE » + un admin **de clinique** mais
  **aucun SUPER_ADMIN** (transverse). Ajouter le bootstrap d'un SUPER_ADMIN via env
  (`CLINIC_SUPERADMIN_USERNAME/PASSWORD`, fail-safe si absent → pas créé) + permettre au
  SUPER_ADMIN de créer des cliniques et des comptes rattachés depuis `/admin/clinics`/`/admin/users`.
  *Acceptation* : prod avec env SUPER_ADMIN → compte transverse créé ; peut créer une 2ᵉ clinique + son admin.

> **Parqué (A — ne pas faire sauf demande)** : numérotation **par clinique** (uniques composites
> `(clinic_id, numéro)` — aujourd'hui volontairement globale/monotone, OK) ; quotas/branding par tenant.

---

## B — PWA : finitions

> État : fondation faite (manifest + SW réseau-d'abord + `offline.html`, **zéro cache PHI/auth**).

- [ ] **B1 — Icônes PNG (installabilité large).**
  Aujourd'hui seule une icône **SVG** (`images/icon.svg`). Certains OS/navigateurs exigent des
  PNG pour l'install/splash. Générer 192×192 + 512×512 (+ maskable) PNG, les déclarer dans
  `manifest.webmanifest` (garder le SVG en complément). Pas de migration, pas de PHI.
  *Acceptation* : `manifest` valide avec icônes PNG ; pas de régression test.

- [ ] **B2 — Invite d'installation personnalisée (`beforeinstallprompt`).**
  Capturer l'événement dans `js/pwa.js`, afficher un bouton/discret « Installer l'app » (i18n),
  déclencher `prompt()` au clic, masquer si déjà installé. Dégradation gracieuse (navigateurs
  sans support). 3 clés i18n.
  *Acceptation* : sur navigateur compatible, bouton d'install apparaît et fonctionne ; invisible sinon.

- [ ] **B3 — Précache des ressources-clés (app shell renforcé).**
  Étendre `sw.js` : précacher au `install` la liste explicite de l'app shell (css/js/images/manifest/
  offline.html) pour un premier rendu hors-ligne fiable. **Toujours zéro page HTML / PHI / auth**
  en cache. Versionner le cache (bump à chaque déploiement).
  *Acceptation* : couper le réseau après 1ère visite → shell + `offline.html` servis ; aucune PHI cachée.

- [ ] **B4 — File de synchro des écritures hors-ligne (LE gros morceau).**
  Permettre quelques **écritures** hors-ligne (ex. prise de constantes / création RDV) mises en
  file (**IndexedDB**) + **Background Sync API** + rejeu serveur **idempotent** (clé de requête
  / dédup). **Décisions à verrouiller au démarrage de la slice** : quelles écritures (périmètre
  minimal d'abord), gestion des conflits, et **interdiction de mettre de la PHI en clair durable**
  côté client (chiffrer la file ? ou limiter aux non-PHI ?). Slice **lourde** — la découper si besoin.
  *Acceptation* : créer un RDV hors-ligne → mis en file → rejoué et persisté au retour réseau, sans doublon.

---

## C — ACCESSIBILITÉ (WCAG 2.2) : finitions

> État : socle fait dans le chrome partagé + `app.css` (focus-visible, skip-link, ARIA repères,
> contrastes corrigés). Ne reste que l'**audit fin par template** (~75 vues) + l'outillage.

- [ ] **C1 — Audit a11y des templates à fort trafic (lot 1).**
  Passer en revue : `patients/{list,detail,form}`, `appointments/{list,week,form}`,
  `consultations/{list,form,detail}`, `billing/**`. Corriger : `<label for>` (champs orphelins),
  `aria-label` sur **boutons icône-seule**, `<th scope="col|row">` sur les tables, ordre de
  tabulation des formulaires complexes, contraste des **badges colorés**. i18n des nouveaux libellés.
  *Acceptation* : ces vues passent une revue manuelle clavier + labels ; pas de régression test.

- [ ] **C2 — Audit a11y des templates restants (lot 2).**
  Idem C1 sur le reste : pharmacie, labo, imagerie, hospitalisation, maternité, rapports, admin,
  portail patient, setup. Mêmes critères.
  *Acceptation* : tous les templates audités ; checklist a11y cochée par vue.

- [ ] **C3 — Tests a11y automatisés (axe-core).**
  Intégrer axe-core sur les vues-clés rendues (via un test qui charge le HTML rendu + assert
  l'absence de violations critiques). **Sans CDN** (vendoriser axe-core ou via dépendance test).
  Étend `A11yTest`.
  *Acceptation* : `mvnd test` vert + violations critiques = 0 sur les vues couvertes.

> **Parqué (C)** : audit lecteur d'écran réel (manuel, exploitation — non automatisable ici).

---

## D — DIVERS (durcissement / polish — finitions de prod)

### D1 — Sécurité / auth (priorité haute)
- [ ] **D1a — Refresh token en cookie HttpOnly (front web).** Aujourd'hui le refresh est en JSON
  (OK pour API/desktop). Pour le **web**, le poser en cookie `HttpOnly`/`Secure`/`SameSite` et
  adapter `/refresh`/`/logout`. Garder le JSON pour les clients API/desktop. *Acc.* : session web
  survit au refresh via cookie ; pas de token en JS web.
- [ ] **D1b — Nettoyage périodique des refresh tokens expirés (`@Scheduled`).** Job tenant-agnostique
  (les refresh ne sont pas `@TenantId`) qui purge `refresh_tokens` expirés/révoqués anciens. *Acc.* : cron + test unitaire de purge.
- [ ] **D1c — Vue admin « sessions actives » + révocation ciblée par appareil.** Lister les refresh
  actifs d'un user, révoquer un appareil précis. *Acc.* : page admin liste + bouton révoquer → 401 sur cet appareil.
- [ ] **D1d — Rate-limit IP sur le login (Bucket4j).** Complète le lockout par compte (P1.3) contre
  le DoS distribué. *Acc.* : N tentatives/IP/fenêtre → 429.

### D2 — Monitoring / ops
- [ ] **D2a — Dashboards Grafana provisionnés + métriques métier.** Provisionner datasource + JSON
  dashboards ; ajouter compteurs/`@Timed` métier (consultations, encaissements…) ; label `clinic_id`
  pour le multi-tenant. *Acc.* : dashboard chargé au boot Grafana + métriques métier visibles dans `/actuator/prometheus`.
- [ ] **D2b — build-info (git/version) + alerting.** Plugin Spring Boot `build-info` (expose version/git
  dans `/actuator/info`) + règles d'alerte Prometheus/Alertmanager de base. *Acc.* : `/actuator/info` montre version+git ; règle d'alerte définie.

### D3 — Crypto (extension)
- [ ] **D3a — Chiffrer constantes/diagnostics consultation + résultats labo.** Nécessite d'élargir
  des VARCHAR courts → TEXT (migration) puis poser `@Convert(PhiStringConverter)`. **Attention** :
  ne pas chiffrer de colonnes recherchées/uniques. *Acc.* : lecture JDBC brute = `gcm:…`, relecture entité = clair ; recherche intacte.
- [ ] **D3b — Chiffrer les fichiers uploadés au repos (niveau appli) + rotation de clé outillée.**
  Chiffrer le contenu stocké par `FileStorageService` (au-delà du volume) ; commande/route de
  re-chiffrement pour rotation de `app.encryption.key`. *Acc.* : fichier sur disque illisible ; rotation rejoue le déchiffrement/re-chiffrement.

### D4 — Fonctionnel / UX (polish)
- [ ] **D4a — Export `format=pdf|excel` sur `/api/reports/*` + images radio base64 en PDF.** Pour les
  clients API/desktop. Réutilise `PdfExportService`/`ExcelExportService`. + embarquer les images
  imagerie en base64 dans le bulletin PDF (aujourd'hui masquées). *Acc.* : `GET /api/reports/x?format=pdf` → `%PDF` ; bulletin radio PDF contient l'image.
- [ ] **D4b — Portail patient : annulation RDV + téléchargement PDF + profil/mot de passe.** Permettre
  au patient d'annuler son RDV, télécharger ses bulletins/ordonnances en PDF, changer son mot de
  passe. *Acc.* : ces 3 actions fonctionnent sous `hasRole('PATIENT')`, cloisonnées au propre dossier.
- [ ] **D4c — Recherche globale étendue + libellés CIM-10 + « top pathologies » sur codes.** Étendre
  la palette ⌘K (consultations/RDV/médicaments) ; afficher le **libellé** des codes CIM-10 sur la
  fiche consultation ; brancher le « top pathologies » des rapports sur `icd10_codes` (pas le texte
  libre). *Acc.* : palette trouve une consultation ; fiche montre « J06.9 — Infection… » ; rapport épidémio basé sur codes.
- [ ] **D4d — i18n des onglets/écrans encore FR en dur (balayage de complétude).** Vérifier les
  reliquats post-I18N-PLAN (ex. onglet **Aperçu** patient signalé FR en dur, écrans desktop hors
  périmètre web). Compléter les `#{}` manquants. *Acc.* : grep des chaînes FR en dur sur les vues web = vide (hors contenu dynamique).

---

## Z — TIER 2 : grosses features parquées (listées pour exhaustivité — HORS périmètre finitions)

> Ce ne sont **pas** des finitions mais des **features** complètes, déférées par décision. Listées
> ici pour que **rien ne soit perdu**. À **promouvoir** explicitement dans le tableau de bord si
> l'utilisateur le décide ; sinon, ne pas les traiter dans une session « finitions ».

- **Z1 — FHIR avancé** [ex-P2.1] : écritures (`POST`/transaction Bundle), serveur HAPI `RestfulServer`
  complet (`_format`/recherche avancée/pagination), ressources `Practitioner`/`DiagnosticReport`/
  `Condition`/`AllergyIntolerance`, **codage LOINC réel** (`lab_test_catalog.code`→LOINC), SMART-on-FHIR.
- **Z2 — Scribe IA étage 1 (audio→texte)** [ex-P4.1] : captation micro front (MediaRecorder +
  consentement), STT (Whisper auto-hébergé vs Deepgram/Azure — vrai poste de coût), streaming/UX
  d'attente, éval qualité FR + accents, mapping typé des constantes, vue de validation médecin,
  posture PHI définitive (BAA/ZDR vs LLM auto-hébergé).
- **Z3 — Télémédecine avancée** [ex-P3.7] : notif du lien au patient (SMS/email), **Jitsi
  auto-hébergé + JWT de salle**, fenêtre temporelle d'ouverture, salle d'attente/présence, enregistrement.
- **Z4 — Mobile Money actif** [ex-P3.3] : initiation de paiement (push USSD/STK), vue admin du
  journal des webhooks, SDK réels par agrégateur (signatures propres), réconciliation/relance,
  montants partiels multiples. (Le récepteur webhook P3.3 existe mais dort — marché Niger = QR manuel.)
- **Z5 — Patient overview avancé** [ex-P3.6] : filtres de timeline par type, pagination dossier
  volumineux, CPN maternité comme évènements, sparkline des constantes.
- **Z6 — Let's Encrypt automatisé** [ex-P2.5] : certbot/Caddy pour le TLS prod (aujourd'hui certif
  manuel / auto-signé LAN documenté).

---

## Journal de progression (1 ligne par slice — remplir à chaque session)

| Date | Slice | Résultat (tests, fichiers clés, NB) |
|---|---|---|
| 2026-06-29 | (création) | Tracker créé. Base vérifiée : `mvnd test` **189 verts**, multi-tenant = isolation complète (V20→V30, validé PG 16.14). Note P4.2 d'`IMPROVEMENT-BACKLOG.md` constatée périmée → ce fichier devient la source de vérité des finitions. |
