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
| A | Multi-tenant — finitions | 4 | 4 | ✅ terminé |
| B | PWA — finitions | 4 | 3 | 🚧 en cours |
| C | Accessibilité (A11y) — finitions | 3 | 3 | ✅ terminé |
| D | Divers (durcissement/polish) | 8 | 0 | 🔲 à démarrer |
| Z | (Tier 2) Grosses features parquées | — | — | 📦 listées, hors périmètre finitions |

**Ordre conseillé** : A (clôt le multi-tenant, petites slices à forte valeur) → D4a (sécu) →
C (a11y) → B (PWA) → reste de D. Mais chaque slice est indépendante : prendre la plus utile.

---

## A — MULTI-TENANT : finitions

> État : **isolation complète**. Ne restent que 4 finitions (aucune n'est une fuite active).

- [x] **A1 — Test Testcontainers PostgreSQL permanent (CI-safe). ✅ FAIT 2026-06-29.**
  `PostgresMigrationTenancyTest` (`@SpringBootTest @ActiveProfiles("test") @Testcontainers(disabledWithoutDocker=true) @Tag("testcontainers")`),
  conteneur `postgres:16`, datasource écrasée via `@DynamicPropertySource` (précédence > profil test H2).
  3 tests verts sur **vrai PostgreSQL** : (1) Flyway ≥ V30 appliqué + 0 pending, (2) isolation tenant
  CENTRALE≠PLATEAU + accès-par-id cloisonné, (3) UNIQUE composite `(clinic_id, code)` réutilisable
  (PLATEAU recrée un code de CENTRALE). Deps `org.testcontainers:{postgresql,junit-jupiter}` (BOM Spring Boot).
  Clôt aussi **P1.5 étape 4**. `disabledWithoutDocker=true` → **skippé** (pas en échec) sans Docker → `mvnd test` reste vert.
  ⚠️ **Voir « Notes d'environnement » (bas de page)** : sur ce poste Windows + Docker Desktop 29 il a fallu
  2 fichiers de config globaux (hôte du pipe + version d'API docker-java) sinon Testcontainers se skippe en silence.
  *Vérifié* : `mvnd clean test` → **190 verts, 0 skip** (le test a réellement tourné contre PG).

- [x] **A2 — `enqueueInAppToRole` cloisonné par clinique. ✅ FAIT 2026-06-29.**
  `NotificationService.enqueueInAppToRole` filtre désormais les destinataires sur
  `TenantContext.currentClinicId()` (nouvelle requête `UserRepository.findByRoleAndClinicIdAndDeletedAtIsNullOrderByFullNameAsc`) ;
  si aucune clinique résolue → skip + log (fail-closed). Plus de lignes orphelines pour les users
  d'autres cliniques. Seul appelant = `notifyStockAlert` (via `StockAlertService`, déjà sous `runAs`).
  +1 test `MultiTenancyTest` : sous PLATEAU, le delta de notifs == nb de médecins **de PLATEAU** (dr.kone),
  pas le total global (dr.martin/radiologue inclus). *Vérifié* : `mvnd clean test` → **191 verts, 0 skip**.
  *NB découvert* : PLATEAU n'est pas vide d'utilisateurs (seed `admin.plateau` + `dr.kone` MEDECIN) — il manque seulement catalogues+config (slice A3).

- [x] **A3 — Seed catalogues + config pour PLATEAU (démo utilisable). ✅ FAIT 2026-06-29.**
  `DataInitializer` (profil `!prod`), dans le bloc `runAs(clinic2Id)` : 2 départements (MED_GEN, PEDIATRIE),
  2 actes (CONS_GEN 5000, PANSEMENT 2000), 2 analyses (NFS, GLY) + une `ClinicConfig` PLATEAU
  (« Cabinet du Plateau », clinic_id explicite car appli-scoped, pas `@TenantId`). Pas d'imagerie
  (module off par défaut) → garde une preuve d'isolation (CENTRALE a des examens, PLATEAU non).
  Injections ajoutées : `DepartmentRepository`, `ActCatalogRepository`, `ClinicConfigRepository` + helpers `seedAct`/`seedLab`.
  Tests : `MultiTenancy.catalogues_cloisonnes_par_clinique` réécrit (les 2 cliniques ont départements+actes,
  isolation par id) ; `PostgresMigrationTenancyTest` (A1) rendu indépendant du seed (code neuf `A1_REUSE`
  créé dans les 2 cliniques au lieu de réutiliser un code seedé qui collisionnait avec MED_GEN).
  *Vérifié* : `mvnd clean test` → **191 verts, 0 skip**.

- [x] **A4 — Provisionnement SUPER_ADMIN en prod + comptes inter-cliniques. ✅ FAIT 2026-06-29.**
  `ProdDataInitializer` restructuré : bootstrap d'un **SUPER_ADMIN transverse** (clinic_id NULL) via
  `CLINIC_SUPERADMIN_USERNAME/PASSWORD` **et/ou** l'admin de clinique via `CLINIC_ADMIN_*` — au moins
  l'un des deux requis, sinon `/setup` prend le relais. `.env.example` documente les 2 blocs.
  **Comptes inter-cliniques** : le SUPER_ADMIN peut créer une clinique (déjà possible) **+ son premier
  admin** via `GET/POST /admin/clinics/{id}/admin` (`UserService.createForClinic(clinicId, dto)` — pose
  le `clinic_id` explicitement, rôle forcé ADMIN ; contrairement à `create()` qui lie au tenant courant,
  null pour le SUPER_ADMIN). Template `admin/clinics/admin-form.html` + lien « + Admin » dans la liste
  + 3 clés i18n ×3 langues + flash succès (clé dynamique `#{${success}}`).
  Tests : `MultiTenancy.superadmin_provisionne_l_admin_d_une_clinique_cible` (clinicId/role corrects) +
  2 `SecurityMatrixTest` (SUPER_ADMIN voit le form / ADMIN → 403). *Vérifié* : `mvnd clean test` → **194 verts, 0 skip**.
  *NB* : le bootstrap prod (profil `prod` + PG) n'est pas testable en H2 (même contrainte que P1.1) — vérifié par revue + le flux de provisionnement l'est sous le profil `test`.

> **✅ Chantier A (multi-tenant) TERMINÉ** — A1→A4 faits. Plus aucune fuite ; 2ᵉ clinique démo utilisable ; SUPER_ADMIN provisionnable en prod + flux de création clinique→admin.

> **Parqué (A — ne pas faire sauf demande)** : numérotation **par clinique** (uniques composites
> `(clinic_id, numéro)` — aujourd'hui volontairement globale/monotone, OK) ; quotas/branding par tenant.

---

## B — PWA : finitions

> État : fondation faite (manifest + SW réseau-d'abord + `offline.html`, **zéro cache PHI/auth**).

- [x] **B1 — Icônes PNG (installabilité large). ✅ FAIT 2026-06-30.**
  Générées `images/icon-{192,512}.png` (`purpose: any`) + `icon-maskable-512.png`
  (`purpose: maskable`, croix réduite à 78 % pour rester dans la zone de sécurité, fond plein
  perdu). Le `manifest.webmanifest` déclare désormais les 3 PNG **en plus** du SVG (SVG repassé en
  `purpose: any` seul, le maskable étant fourni par le PNG dédié). Rendu vérifié visuellement (croix
  médicale bleue centrée). Pas de CDN (servies depuis `/images/` static, CSP `default-src 'self'` OK),
  pas de migration, pas de PHI. **NB outillage** : pas d'ImageMagick ici (`convert` = `convert.exe`
  NTFS de Windows, pas IM ; `magick` absent) → généré via un script **Pillow** jetable
  (`pip install Pillow`) qui redessine la géométrie de l'`icon.svg` (rects arrondis) en supersampling ×4
  + downscale LANCZOS. *Vérifié* : `mvnd test` → **203 verts, 3 skip** (Testcontainers sans Docker, attendu).

- [x] **B2 — Invite d'installation personnalisée (`beforeinstallprompt`). ✅ FAIT 2026-06-30.**
  `js/pwa.js` capture `beforeinstallprompt` (`preventDefault` → on pilote notre UI), stocke
  l'événement différé, et **affiche** un bouton discret `#pwa-install-btn` (présent masqué dans le
  chrome de `base.html` **et** `portal/layout.html`, topbar/header). Clic → `deferredPrompt.prompt()`
  + `userChoice.finally` qui purge l'événement (rejouable une seule fois) et remasque. Garde
  `display-mode: standalone` / `navigator.standalone` → jamais proposé si déjà installé ; `appinstalled`
  → remasque + log la clé `pwa.installed`. Dégradation gracieuse : pas de bouton sur les pages sans
  chrome (login) → no-op ; navigateurs sans l'événement → bouton reste masqué. 3 clés i18n
  `pwa.{install,install_title,installed}` ×3 langues (label via `th:text`, message succès en
  `data-installed`). +1 test `PageRenderSmokeTest` (bouton présent + masqué + titre i18n).
  **NB** : Thymeleaf échappe l'apostrophe (`l'app` → `l&#39;app`) → le test assert sur le titre
  sans apostrophe. *Vérifié* : `mvnd test` → **204 verts, 3 skip**.

- [x] **B3 — Précache des ressources-clés (app shell renforcé). ✅ FAIT 2026-06-30.**
  `sw.js` : `SHELL_ASSETS` rendu **explicite et complet** — il ne précachait que 5 ressources
  (offline/app.css/pwa.js/manifest/icon.svg), ratant `js/{ui,search,worklist-live}.js` et les 3 PNG
  B1. Désormais 11 entrées (offline.html + app.css + les 4 JS + manifest + svg + 3 PNG). Cache
  **versionné** `clinicapp-shell-v2` (bump documenté « à chaque déploiement qui modifie l'app shell » ;
  l'`activate` purge déjà tout `clinicapp-shell-*` ≠ version courante). **Toujours zéro page HTML /
  PHI / auth** en cache (navigations = réseau-d'abord → repli `offline.html` ; `BYPASS_PREFIXES`
  inchangé). +2 tests `PwaShellTest` : extrait `SHELL_ASSETS` du `/sw.js` servi et (1) vérifie que
  **chaque** ressource est servie en 200 — garde-fou contre l'échec **atomique** de `cache.addAll`
  (1 entrée 404 = install SW cassée), (2) vérifie qu'aucune entrée n'est sensible (préfixes API/FHIR/
  uploads/auth interdits, extensions statiques seulement). *Vérifié* : `mvnd test` → **206 verts, 3 skip**.

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

- [x] **C1 — Audit a11y des templates à fort trafic (lot 1). ✅ FAIT 2026-06-29.**
  `<label for>`+`id` posés sur **tous** les champs des 5 formulaires (patients/form, appointments/form,
  consultations/form ~25 champs, billing/invoices/{form,pay}) — convention `for`/`id` alignée sur `login.html`.
  Lignes dynamiques de facturation (`items[i]`) : `aria-label` par colonne sur les rangées Thymeleaf **et**
  celles créées en JS (`addInvRow`, constantes i18n `LBL_*`). `<th scope="col">` sur **toutes** les tables
  (listes + dossier patient ×6 + détails + grilles) ; colonne « Actions » vide → `<th scope="col"><span class="sr-only">`;
  grille semaine : cellule heure passée en `<th scope="row">`. `aria-label` sur tous les filtres non étiquetés
  (recherche patients, date/médecin/statut des barres de filtre, recherche caisse). 2 clés i18n neuves
  `common.date_from`/`common.date_to` ×3 langues. Contraste badges vérifié OK (texte foncé sur fond clair, ≥4.5:1).
  +2 tests `A11yTest` (label/for sur `/patients/new`, `scope="col"` sur `/patients`). Pattern ARIA tablist du
  dossier patient laissé tel quel (déjà `role=tab`/`aria-selected`, JS-géré — pas de régression).
  *Vérifié* : `mvnd test` → **196 verts, 0 skip** (PostgreSQL Testcontainers inclus).

- [x] **C2 — Audit a11y des templates restants (lot 2). ✅ FAIT 2026-06-29.**
  Mêmes critères que C1, appliqués à **tout le reste** : pharmacie (drugs/stock-receive/dispense + dashboard/stock/worklist/list/detail),
  labo (form/result-entry + worklist/list/detail), imagerie (form/report-form + worklist/list/detail),
  hospitalisation (admit/room-form + list/rooms/detail ; beds = grille de cartes, RAS), maternité
  (form/visit-form/delivery-form + list/record), rapports (financial/activity/epidemiology/outstanding/dashboard),
  admin (8 formulaires + 8 listes + audit + config), portail patient (form + home/appointments/record + **layout**), setup.
  Posé `<label for>`+`id` sur **tous** les champs (`th:field` génère déjà l'`id` → seul `for` ajouté ;
  `name=` simple → `for`+`id`). `<th scope="col">` sur toutes les tables (+ `<th scope="row">` sur les
  rangées de saisie labo, `sr-only` pour colonnes Actions vides, `aria-label` sur cases à cocher labo/imagerie
  reprenant le nom de l'analyse/examen). `aria-label` sur tous les filtres non étiquetés + les contrôles d'action
  inline du détail hospitalisation. **Chrome portail** mis à parité C1 : skip-link → `#portal-main`, `main[tabindex=-1]`,
  `nav[aria-label]`. Print-only (bulletins labo/imagerie, pdf-report) exclus (hors chrome interactif). +1 clé i18n
  `common.selection` ×3 langues. setup/wizard déjà conforme (for/id présents). +2 tests `A11yTest` (labo form/for, worklist scope).
  *Vérifié* : `mvnd test` → **196 verts, 0 skip** (PageRenderSmoke + Portal + I18nBundle inclus).

- [ ] **C2-reliquat (optionnel) — pattern ARIA tablist complet.** Les onglets (dossier patient, dossier maternité)
  portent `role=tablist`/`role=tab`/`aria-selected` mais pas `role=tabpanel`+`aria-controls`/`aria-labelledby`
  (géré en JS). Laissé tel quel pour ne pas régresser le JS ; à compléter si un audit lecteur d'écran le réclame.

- [x] **C3 — Tests a11y automatisés (axe-core). ✅ FAIT 2026-06-30.**
  Nouveau `A11yAxeTest` (frère de `A11yTest`) : exécute le **vrai moteur axe-core** (WCAG 2.0/2.1
  A & AA) sur **15 vues-clés** rendues (login + médecin ×7 + caisse ×3 + admin ×3 + pilotage) et
  échoue sur toute violation d'impact **critique ou sérieux**. Résultat : **0 violation** partout
  (le travail C1/C2 tient). **Stack 100% Java, sans réseau ni binaire de navigateur** : on récupère
  le HTML **authentifié** via `MockMvc` (`@WithUserDetails`), puis on l'audite dans **HtmlUnit** nu.
  axe-core est lu depuis `/axe.min.js` **embarqué dans le JAR `com.deque.html.axe-core:selenium`**
  (aucun CDN — cohérent CSP `default-src 'self'`). Dépendances test : `org.htmlunit:htmlunit`
  (gérée BOM) + ce JAR deque (pour la ressource axe). +5 tests. *Vérifié* : `mvnd clean test` → **203 verts, 0 skip**.
  **NB / pièges tranchés (chacun a coûté un cycle)** :
  (1) **Ne PAS utiliser `AxeBuilder` (deque) ni l'`HtmlUnitDriver` Selenium** : `executeAsyncScript`
  renvoie la `Promise` non résolue d'axe → `NativePromise` non sérialisable par le pont
  HtmlUnit→Jackson. On injecte axe soi-même, forme **à callback**, et on récupère le résultat
  **déjà `JSON.stringify`é** (une `String`, insensible à la conversion d'objets HtmlUnit).
  (2) **`@WithUserDetails` n'est PAS propagé** aux requêtes émises par le pont HtmlUnit→MockMvc
  (toutes → 302 `/login`, donc on n'auditait que la page de login). Solution : charger le HTML via
  `mvc.perform` (qui, lui, respecte l'annotation) et l'injecter dans HtmlUnit.
  (3) **Le JS applicatif fait planter/traîner HtmlUnit** (client STOMP/websocket temps réel,
  graphiques → timeout 10 min). On coupe : `WebConnection` stub qui sert le HTML d'audit et renvoie
  un **corps vide pour toute sous-ressource** (app.js/css), CSS/images/websocket désactivés. L'audit
  ne dépend que du **DOM rendu serveur**, pas du JS de page.
  (4) **`loadHtmlCodeIntoCurrentWindow` laisse `document.readyState='loading'`** → axe (qui attend
  le « ready ») ne rappelle jamais son callback (skip silencieux). Solution : **vraie navigation**
  `web.getPage(AUDIT_URL)` (le stub renvoie le HTML) → `readyState=complete` + `DOMContentLoaded`.
  (5) **Contraste non audité ici** : sans CSS chargé, les règles de contraste ressortent en
  « incomplete » (non bloquant) — contraste déjà vérifié à la main en C1/C2. Audit ciblé sur les
  règles **structurelles** (labels, en-têtes de tableau, rôles ARIA, alt, attributs, langue…).
  (6) **CI-safe** : si le moteur JS d'HtmlUnit ne peut exécuter axe sur un poste donné, chaque test
  se **skippe** (assumeTrue) au lieu d'échouer — même posture que A1. Règles `aria-required-children/attr`
  exclues (C2-reliquat ARIA tablist, encore différé).

> **✅ Chantier C (accessibilité) TERMINÉ** — C1→C3 faits. Reste optionnel : C2-reliquat (pattern
> ARIA tablist complet) si un audit lecteur d'écran le réclame.

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

## 🖥️ Notes d'environnement (Testcontainers sur ce poste Windows + Docker Desktop)

> Nécessaire **uniquement** pour les tests `@Tag("testcontainers")` (A1, et futurs tests PG/conteneur)
> **sur cette machine de dev Windows**. En CI Linux (socket Unix standard), **rien de tout ça** n'est
> requis. Si Testcontainers se **skippe en silence** (« Skipped », « Could not find a valid Docker
> environment »), c'est l'un de ces deux points.

1. **Hôte Docker** — Docker Desktop expose plusieurs pipes nommés ; le contexte actif (`desktop-linux`)
   utilise `npipe:////./pipe/dockerDesktopLinuxEngine`, que Testcontainers ne sonde pas par défaut.
   Fichier `C:\Users\<user>\.testcontainers.properties` :
   `docker.host=npipe:////./pipe/dockerDesktopLinuxEngine`
2. **Version d'API docker-java** — Docker Engine 29 impose **MinAPI 1.40** (serveur API 1.54), mais le
   docker-java embarqué dans Testcontainers 1.21.0 négocie une version plus ancienne (≤ 1.32) → le daemon
   répond **HTTP 400** sur `/info` → Testcontainers conclut « pas de Docker » et **skippe**. Pinner une
   version dans [1.40–1.54] dans `C:\Users\<user>\.docker-java.properties` :
   `api.version=1.43`

   Ces deux fichiers sont **hors dépôt** (config machine). Diagnostic : relancer le test **sans** `-q`
   (`mvnd -Dtest=PostgresMigrationTenancyTest test`) et chercher `Status 400` / `Could not find a valid Docker`.

---

## Journal de progression (1 ligne par slice — remplir à chaque session)

| Date | Slice | Résultat (tests, fichiers clés, NB) |
|---|---|---|
| 2026-06-30 | **B3 — app shell précaché renforcé** | `sw.js` : `SHELL_ASSETS` explicite & complet (5→11 entrées : ajoute `js/{ui,search,worklist-live}.js` + 3 PNG B1) ; cache versionné `v1`→`v2` (purge `activate` déjà en place). Zéro page/PHI/auth en cache (navigations réseau-d'abord → `offline.html`). +2 tests `PwaShellTest` (extrait `SHELL_ASSETS` du `/sw.js` → chaque ressource 200 [garde l'échec atomique de `addAll`] + aucune entrée sensible). **206 verts, 3 skip.** |
| 2026-06-30 | **B2 — invite d'installation PWA** | `js/pwa.js` capture `beforeinstallprompt` (preventDefault) → affiche `#pwa-install-btn` (masqué dans le chrome `base.html` + `portal/layout.html`) ; clic → `prompt()` + `userChoice.finally` purge/remasque ; garde standalone (jamais si installé) + `appinstalled` → remasque. No-op sur login (pas de bouton) et navigateurs sans support. 3 clés `pwa.{install,install_title,installed}` ×3 langues. +1 test `PageRenderSmokeTest`. NB : Thymeleaf échappe l'apostrophe → assert sur le titre. **204 verts, 3 skip.** |
| 2026-06-30 | **B1 — icônes PNG PWA** | Généré `icon-{192,512}.png` (`any`) + `icon-maskable-512.png` (`maskable`, croix à 78 % pour la safe-zone) ; `manifest.webmanifest` déclare les 3 PNG + le SVG (repassé `any`). Pas d'ImageMagick ici (`convert`=NTFS Windows) → script **Pillow** jetable redessinant la géométrie de l'`icon.svg` (supersampling ×4 + LANCZOS). Rendu vérifié à l'œil. Pas de migration/PHI/CDN. **203 verts, 3 skip.** |
| 2026-06-30 | **C3 — tests a11y axe-core → chantier C TERMINÉ** | Nouveau `A11yAxeTest` : axe-core réel (WCAG A/AA) sur 15 vues authentifiées (login + médecin/caisse/admin/owner) → **0 violation critique/sérieuse**. Stack 100% Java sans réseau : HTML authentifié via `mvc.perform` (`@WithUserDetails`) → audité dans **HtmlUnit nu** ; `/axe.min.js` lu du JAR `com.deque.html.axe-core:selenium` (aucun CDN). Deps test : `org.htmlunit:htmlunit` + JAR deque. 4 pièges tranchés : Promise non sérialisable (→ callback + `JSON.stringify`) ; `@WithUserDetails` non propagé au pont HtmlUnit→MockMvc (→ `mvc.perform`) ; JS appli qui fait planter HtmlUnit (→ `WebConnection` stub, sous-ressources vides) ; `loadHtmlCodeIntoCurrentWindow` reste `readyState=loading` (→ vraie nav `getPage`). CI-safe (skip si moteur JS HS). **203 verts, 0 skip.** |
| 2026-06-29 | **C2 — audit a11y lot 2 (le reste)** | `for`/`id` sur tous les champs des formulaires pharmacie/labo/imagerie/hospitalisation/maternité/rapports/admin(×8 forms + config)/portail (`th:field`→`for` seul ; `name=`→`for`+`id`). `<th scope="col">` sur **toutes** les tables restantes + `<th scope="row">` (saisie labo, grille semaine déjà en C1), `sr-only` colonnes Actions, `aria-label` cases à cocher labo/imagerie (nom analyse) + filtres non étiquetés + actions inline détail hospi. Chrome **portail** mis à parité C1 (skip-link/`#portal-main`/`nav[aria-label]`). +1 clé `common.selection` ×3. setup déjà conforme. Print-only exclus. +2 tests `A11yTest`. **196 verts, 0 skip.** |
| 2026-06-29 | **C1 — audit a11y lot 1 (labels/scope/aria)** | `for`/`id` sur tous les champs des 5 formulaires (patients/appointments/consultations/billing form+pay) ; `aria-label` par colonne sur lignes de facturation dynamiques (Thymeleaf + JS) ; `<th scope="col">` sur toutes les tables (+ `sr-only` pour colonne Actions vide, `<th scope="row">` heure grille semaine) ; `aria-label` sur les filtres non étiquetés. 2 clés i18n `common.date_from/to` ×3. +2 tests `A11yTest`. Contraste badges OK (déjà ≥4.5:1). **196 verts, 0 skip.** |
| 2026-06-29 | **A4 — SUPER_ADMIN prod + comptes inter-cliniques → chantier A TERMINÉ** | `ProdDataInitializer` : bootstrap SUPER_ADMIN (clinic_id NULL) via `CLINIC_SUPERADMIN_*` et/ou admin clinique via `CLINIC_ADMIN_*` (au moins un, sinon /setup). `UserService.createForClinic(clinicId,dto)` (clinic_id explicite, rôle ADMIN forcé) + endpoints `GET/POST /admin/clinics/{id}/admin` + template `admin-form.html` + lien liste + 3 clés i18n ×3 + flash `#{${success}}`. `.env.example` MAJ. +3 tests (service createForClinic + 2 gating SUPER_ADMIN/ADMIN). **194 verts, 0 skip.** Bootstrap prod non testable en H2 (revue). |
| 2026-06-29 | **A3 — seed catalogues + config PLATEAU** | `DataInitializer` (runAs clinic2) : 2 départements/2 actes/2 analyses + `ClinicConfig` « Cabinet du Plateau ». Pas d'imagerie (preuve d'isolation gardée). Injections `Department/ActCatalog/ClinicConfigRepository` + helpers. `MultiTenancy.catalogues_*` réécrit (isolation par id) ; **A1 rendu indépendant du seed** (code neuf `A1_REUSE`, l'ancien réutilisait MED_GEN désormais présent côté PLATEAU → collision). **191 verts, 0 skip.** |
| 2026-06-29 | **A2 — enqueueInAppToRole par clinique** | `NotificationService.enqueueInAppToRole` filtre les destinataires sur `TenantContext.currentClinicId()` (+ requête `UserRepository.findByRoleAndClinicIdAndDeletedAtIsNullOrderByFullNameAsc`) ; null tenant → skip (fail-closed). Fin des lignes orphelines inter-cliniques. +1 test `MultiTenancyTest` (delta notifs PLATEAU == médecins de PLATEAU, pas le total). **191 verts, 0 skip.** NB : PLATEAU a déjà des users seedés (`admin.plateau`/`dr.kone`), reste catalogues+config (A3). |
| 2026-06-29 | **A1 — Testcontainers PostgreSQL** | `PostgresMigrationTenancyTest` (postgres:16, `@DynamicPropertySource`, skip-sans-Docker) → 3 verts sur **vrai PG** (Flyway ≥V30, isolation tenant, UNIQUE composite). Deps `testcontainers:{postgresql,junit-jupiter}`. **Compte fiable `mvnd clean test` = 190 verts, 0 skip** (le « 189 » de la création était gonflé par 2 rapports surefire périmés `ActuatorMonitoringTest`/`PgValidationManualTest`, purgés par `clean`). Clôt P1.5 étape 4. **Gotcha Docker/Windows** documenté en « Notes d'environnement » (2 fichiers `~/.testcontainers.properties` + `~/.docker-java.properties`). |
| 2026-06-29 | (création) | Tracker créé. Base : multi-tenant = isolation complète (V20→V30, validé PG 16.14). Note P4.2 d'`IMPROVEMENT-BACKLOG.md` constatée périmée → ce fichier = source de vérité des finitions. |
