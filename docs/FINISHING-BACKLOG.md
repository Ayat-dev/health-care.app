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
  **Dernière version = V32** (D1c ; 8 Java migrations possibles, cf. V28/V30). Migration multi-tenant =
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
| B | PWA — finitions | 4 | 4 | ✅ terminé |
| C | Accessibilité (A11y) — finitions | 3 | 3 | ✅ terminé |
| D | Divers (durcissement/polish) | 12 | 12 | ✅ terminé |
| Z | (Tier 2) Grosses features parquées | 6 | 3 | ✅ Z4(a+b)·Z5·Z6 faits ; 🛑 Z1-Z3 ABANDONNÉS (déc. util. 2026-07-01, ne pas reproposer) |
| E | Tier E — extensions cliniques | 3 | 2 | ✅ E1 certificats (+bis) ; ✅ E2 (allergies+interactions) ; ⏳ E3 MFA |

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

- [x] **B4 — File de synchro des écritures hors-ligne. ✅ FAIT 2026-06-30.**
  **Décisions verrouillées** (utilisateur) : périmètre = **création de RDV uniquement** ; PHI = **file
  IndexedDB chiffrée** (AES-GCM) ; conflits = **marquer en échec + notifier, garder en file**.
  **Client** `js/offline-sync.js` (chargé globalement par `base.html`, no-op sans IndexedDB/WebCrypto) :
  intercepte la soumission du formulaire RDV *uniquement hors-ligne* (`navigator.onLine === false`) →
  chiffre la charge utile (clé AES-GCM 256 **non-extractible**, persistée comme `CryptoKey` dans
  IndexedDB, jamais exportable) et l'enfile ; seuls l'UUID de requête (`crypto.randomUUID`, sans PHI) et
  le statut restent en clair. Rejeu **dans le contexte page** (pas le SW : besoin du jeton CSRF `<meta>`
  + cookie de session + la clé) au `DOMContentLoaded` et sur l'événement `online`. **Serveur** :
  endpoint `POST /appointments/offline` (chaîne web session+CSRF, `@ResponseBody` JSON) →
  `AppointmentService.createIdempotent(dto, key)` ; dédoublonnage via **`Idempotency-Key`** : un rejeu
  de la même clé retombe sur le RDV existant (`findByRequestKey`) → **aucun doublon**. Colonne
  `appointments.request_key VARCHAR(36) UNIQUE` (migration **V31**, NULLABLE — NULL pour les créations
  en ligne ; UNIQUE portable H2+PG car plusieurs NULL autorisés) = ultime garde-fou anti-course.
  Conflit métier (créneau pris, RDV passé…) → **409** → le client marque l'item **ÉCHEC** et le conserve
  (pas de rejeu en boucle). Bannière `#offline-queue-status` (`role=status aria-live`, style
  `.offline-banner` dans app.css) affiche les compteurs en attente/échec **sans déchiffrer** (donc sans
  exposer de PHI). 6 clés i18n `offline.*` ×3 langues. **Background Sync API** (rejeu SW fenêtre fermée)
  laissée en évolution future (incompatible avec clé/CSRF côté page + support navigateur). +2 tests
  `OfflineSyncTest` (rejeu même clé → 1 seul RDV ; conflit → 409). **NB** : la couche chiffrement/IndexedDB
  est JS pur (non testée par MockMvc, comme worklist-live/search) — le **contrat serveur** d'idempotence
  est couvert de bout en bout. *Vérifié* : `mvnd test` → **208 verts, 3 skip**.

> **✅ Chantier B (PWA) TERMINÉ** — B1→B4 faits. Reste optionnel/parqué : Background Sync API (rejeu
> hors fenêtre), périmètre d'écritures hors-ligne élargi (constantes, etc.).

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

- [x] **C2-reliquat — pattern ARIA tablist complet. ✅ FAIT 2026-07-01.**
  La sémantique « Tabs » du motif WAI-ARIA est désormais portée **en statique** par les 2 templates à
  onglets (`patients/detail.html` 9 onglets, `maternity/record.html` 4 onglets), pas seulement injectée
  en JS : chaque onglet `role=tab` gagne `id`/`aria-controls`→panneau + `tabindex` mobile (roving :
  actif `0`, autres `-1`) ; chaque panneau `role=tabpanel` + `aria-labelledby`→onglet + `tabindex=0`
  (focalisable). `js/ui.js` (`initTabs`, patron mutualisé) **gère l'état dynamique** : bascule
  `aria-selected`/`tabindex`/affichage, **navigation clavier** ←/→/↑/↓ + Origine/Fin (activation
  automatique + focus), et garde la liaison ARIA en **filet « si absent »** (templates = source de
  vérité, JS = secours pour une future page). **Point clé** : `A11yAxeTest` (C3) auditant le HTML
  **rendu serveur avant le JS de page**, mettre l'ARIA en statique permet de **lever l'exclusion** des
  règles `aria-required-children`/`aria-required-attr` (`DEFERRED_RULES` désormais vide) — et d'ajouter
  `/maternity/1` aux vues auditées. **Vérifié réellement exécuté** (pas skippé) : `A11yAxeTest` 5 tests,
  0 violation critique/sérieuse sur `/patients/1` + `/maternity/1` avec les règles tablist actives.
  *Vérifié* : `mvnd test` → **250 verts, 0 skip**.

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

> **✅ Chantier C (accessibilité) TERMINÉ** — C1→C3 **+ C2-reliquat** (pattern ARIA tablist complet,
> 2026-07-01) faits. Plus rien d'optionnel en suspens sur l'a11y.

> **Parqué (C)** : audit lecteur d'écran réel (manuel, exploitation — non automatisable ici).

---

## D — DIVERS (durcissement / polish — finitions de prod)

### D1 — Sécurité / auth (priorité haute)
- [x] **D1a — Refresh token en cookie HttpOnly (front web). ✅ FAIT 2026-06-30.**
  Mode **opt-in** : `POST /api/auth/login` avec `"cookie":"true"` pose le refresh token en cookie
  `HttpOnly`/`Secure`/`SameSite=Strict`, **path `/api/auth`** (envoyé seulement à refresh+logout),
  et **ne le renvoie jamais en JSON** (seul l'access token court reste en JSON, en mémoire JS).
  Sans le flag → JSON inchangé (API/desktop). Helper `security/RefreshCookieManager` (build/clear/read,
  `ResponseCookie` → en-tête `Set-Cookie`). `/refresh` et `/logout` : **cookie prioritaire** sur le
  corps JSON ; en mode cookie la rotation repose le nouveau jeton en cookie (hors JSON) et tout échec
  (jeton mort/volé/logout) **efface** le cookie (`Max-Age 0`). Flag `app.jwt.refresh-cookie-secure`
  (défaut `true`, surchargé `false` en dev/test HTTP ; `JWT_REFRESH_COOKIE_SECURE` dans `.env.example`).
  +3 tests `RefreshCookieTest` (login → cookie HttpOnly + 0 refresh JSON ; refresh via cookie rotate+repose
  + rejeu ancien cookie → 401+effacé ; logout via cookie révoque+efface). *Vérifié* : `mvnd test` →
  **211 verts, 3 skip**. *NB* : `/refresh` et `/logout` passent `@RequestBody(required=false)` +
  `HttpServletRequest` (le client cookie n'envoie pas forcément de corps JSON).
- [x] **D1b — Nettoyage périodique des refresh tokens expirés (`@Scheduled`). ✅ FAIT 2026-06-30.**
  `RefreshTokenCleanupScheduler` (`@Component`, cron `0 30 3 * * *`, 03:30 heure creuse) appelle
  `RefreshTokenService.purgeStaleTokens()` → repo `deleteExpiredOrRevokedBefore(cutoff)`
  (`@Modifying`, JPQL : `expiresAt < cutoff OR (revokedAt IS NOT NULL AND revokedAt < cutoff)`).
  **Tenant-agnostique** (refresh tokens pas `@TenantId`) → **aucun `runAs`** (à la différence des
  schedulers métier type `StockAlertService`). Fenêtre de **rétention** `app.jwt.refresh-cleanup-retention-days`
  (défaut 7 j ; `JWT_REFRESH_CLEANUP_RETENTION_DAYS`) — on garde un jeton révoqué cette durée pour
  préserver la détection de réutilisation/vol. +1 test `RefreshTokenCleanupTest` (purge expiré/révoqué
  ancien, garde actif + expiré/révoqué récent). *NB* : FK `refresh_tokens.user_id`→`users` → le test
  amorce les jetons sur l'id de `admin` (pas un id fictif). *Vérifié* : `mvnd test` → **212 verts, 3 skip**.
- [x] **D1c — Vue admin « sessions actives » + révocation ciblée par appareil. ✅ FAIT 2026-06-30.**
  Page `/admin/users/{id}/sessions` (ADMIN, dans `AdminUserWebController`) liste les sessions actives
  (refresh tokens non révoqués/non expirés) d'un user + bouton **Révoquer** par session ;
  `POST /{id}/sessions/{tokenId}/revoke` → `RefreshTokenService.revokeSession(tokenId, expectedUserId)`
  (garde-fou : la session doit appartenir au user ciblé ; idempotent). Une session = sa **chaîne de
  rotation** (un seul refresh actif à la fois) ; l'identité d'appareil (`user_agent`, `ip_address`,
  `last_used_at`, `created_at` reporté) est estampillée au login et **reportée à chaque rotation** —
  migration **V32** (3 colonnes nullables) + `RefreshToken` enrichi ; `AuthController` capture
  user-agent + IP (X-Forwarded-For-aware) sur login/refresh. DTO `RefreshSessionDto` (n'expose jamais
  le jeton, seulement l'id + métadonnées). Lien « Sessions » ajouté dans `admin/users/list.html` +
  template `admin/users/sessions.html` + 15 clés i18n `admin.sessions.*` ×3 langues.
  **Affinage sécu** : rejouer un refresh **révoqué par rotation** (`replacedById != null`) = vol → on
  coupe toute la lignée (inchangé) ; mais révoqué **sans remplacement** (révocation admin / logout) →
  simple 401 **sans escalade**, sinon révoquer un appareil tuerait les autres sessions légitimes du user.
  +3 tests `AdminSessionsTest` (page ADMIN 200 / MEDECIN 403 ; révoquer → `/refresh` de cet appareil 401).
  *NB* : pas de blocklist par jeton → l'access token courant de l'appareil révoqué expire dans son TTL
  court (15 min) ; révocation **immédiate** de TOUT le user = logout-all/bump de version (existant).
  *Vérifié* : `mvnd test` → **215 verts, 3 skip**.
- [x] **D1d — Rate-limit IP sur le login (Bucket4j). ✅ FAIT 2026-06-30.**
  Dépendance `com.bucket4j:bucket4j-core:8.7.0`. `LoginRateLimiter` (`@Component`, token-bucket en
  mémoire par IP via `ConcurrentHashMap`, `Bandwidth.classic` + `Refill.greedy`) + `LoginRateLimitFilter`
  (`OncePerRequestFilter`, **classe simple** instanciée dans `SecurityConfig` — PAS un bean, sinon
  Boot l'auto-enregistrerait et il compterait double) inséré `addFilterBefore(UsernamePasswordAuthenticationFilter)`
  sur **les 2 chaînes** (web `/login` + API `/api/auth/login`). Au-delà de la limite → **429** +
  `Retry-After`, **avant l'auth** (aucune charge DB). IP `X-Forwarded-For`-aware. Plafond/fenêtre
  configurables `app.security.login-rate-limit.{max-attempts,window-minutes}` (défaut **20/15 min** ;
  `LOGIN_RATE_LIMIT_MAX`/`LOGIN_RATE_LIMIT_WINDOW_MINUTES`). Plus permissif que le lockout compte (P1.3)
  car une clinique derrière un NAT partage une IP. +2 tests `LoginRateLimitTest` (limite=3 via
  `@TestPropertySource` : 4ᵉ tentative même IP → 429 ; autre IP garde son quota). **NB test** : limite
  relâchée (1e6) dans `application-test.properties` car la suite enchaîne >20 logins depuis 127.0.0.1
  dans le contexte partagé ; le test dédié la rabaisse via `@TestPropertySource` (contexte séparé).
  *Vérifié* : `mvnd test` → **217 verts, 3 skip**.

> **✅ Bloc D1 (sécurité/auth) TERMINÉ** — D1a→D1d faits. Reste optionnel/parqué : backend de bucket
> partagé (Redis/Hazelcast) pour un déploiement multi-instances ; blocklist par access token pour un
> kill immédiat par appareil (aujourd'hui : TTL court 15 min ou logout-all pour le kill total).

### D2 — Monitoring / ops
- [x] **D2a — Dashboards Grafana provisionnés + métriques métier. ✅ FAIT 2026-06-30.**
  Compteurs métier Micrometer (pkg `metrics`, `BusinessMetrics`) tagués **`clinic_id`** (résolu via
  `TenantContext`, multi-tenant) : `clinicapp.consultations.completed` (incr. dans
  `ConsultationService.complete`) + `clinicapp.payments.recorded` & `clinicapp.payments.amount`
  (tag additionnel **`method`**, incr. dans `BillingService.recordPayment`). Enregistrement **à la
  volée** (`Counter.builder(...).register`, Micrometer déduplique par nom+tags → cardinalité bornée
  cliniques×modes). Côté Grafana : **auto-provisionnement au boot** — `monitoring/grafana/provisioning/`
  (datasource Prometheus `http://prometheus:9090` + provider de dashboards) + dashboard
  `monitoring/grafana/dashboards/clinicapp-business.json` (variable `$clinic`, 4 stats + débits
  consultations/encaissements + camembert montant/mode + HTTP système). `docker-compose.yml` monte
  `provisioning/` (→ `/etc/grafana/provisioning`) et `dashboards/` (→ `/var/lib/grafana/dashboards`)
  dans le service `grafana`. **NB** : `baseUnit("XOF")` retiré du compteur montant (Micrometer
  l'injecte dans le nom → `..._XOF_total`, peu lisible) — nom propre `clinicapp_payments_amount_total`,
  XOF dans la description. **NB test** (gotcha P4.3) : ni `PrometheusMeterRegistry` ni l'endpoint
  `/actuator/prometheus` ne sont câblés en profil test → impossible d'asserter le rendu Prometheus ;
  la couverture porte sur le registre Micrometer (source de cette sortie), vérif format en dev.
  +3 tests `BusinessMetricsTest` (label `clinic_id` sur consultations ; `clinic_id`+`method` sur
  encaissements nombre+montant ; compteurs distincts par clinique). *Vérifié* : `mvnd test` →
  **220 verts, 0 skip** (Testcontainers a tourné, Docker présent).
- [x] **D2b — build-info (git/version) + alerting. ✅ FAIT 2026-06-30.**
  `/actuator/info` expose désormais une section **`build`** (version/artefact/heure — goal
  `spring-boot:build-info`, génère `META-INF/build-info.properties` → `BuildProperties`) **et**
  **`git`** (branche/commit/heure — plugin `io.github.git-commit-id:9.0.1`, génère `git.properties`
  → `GitProperties`, mode `full`). Le `.git` est à la **racine du dépôt** (parent du module
  `backend`) → `dotGitDirectory=${project.basedir}/../.git` ; `failOnNoGitDirectory=false` (build OK
  hors dépôt git : CI/tarball). Config : `management.info.{build,git}.enabled=true` +
  `management.info.git.mode=full`. **Alerting Prometheus** : `monitoring/alert.rules.yml` (3 règles —
  `ClinicAppBackendDown` `up==0` 2m critical · `ClinicAppHighHeapUsage` heap>90% 5m ·
  `ClinicAppHighServerErrorRate` 5xx>5% 5m), référencé via `rule_files` dans `prometheus.yml` +
  monté dans le conteneur prometheus (`docker-compose`). Routage Alertmanager laissé optionnel
  (stanza commentée — sans lui, Prometheus affiche PENDING/FIRING dans son UI). +2 tests
  `ActuatorInfoTest` (`BuildProperties`/`GitProperties` injectés + `/actuator/info` contient
  `build`/version/`git`). **NB** : les fichiers générés (`build-info.properties`/`git.properties`)
  vont dans `target/classes` au build → présents au moment des tests (≠ gotcha prometheus de D2a,
  car `/actuator/info` est un endpoint core mappé sous MockMvc). *Vérifié* : `mvnd test` →
  **222 verts, 0 skip**.

### D3 — Crypto (extension)
- [x] **D3a — Chiffrer diagnostics/narratifs consultation + résultats labo. ✅ FAIT 2026-06-30.**
  `@Convert(PhiStringConverter.class)` (AES-GCM existant, P2.5) posé sur les narratifs cliniques :
  **Consultation** `chief_complaint`/`history`/`physical_exam`/`diagnosis`/`treatment_plan` ;
  **LabResult** `result_value`/`reference_range`/`notes`. **PAS chiffré** : `icd10_codes` (code
  structuré, recherché/agrégé — réservé D4c), `unit` labo (court, non-PHI), les **constantes
  vitales** (numériques `BigDecimal`/`Integer` — le converter est `String`→`String`, hors périmètre ;
  le titre « constantes » du backlog visait en réalité les narratifs). **Aucune migration** :
  toutes ces colonnes étaient **déjà `TEXT`** (le NB du backlog « élargir VARCHAR→TEXT » ne
  s'appliquait pas — vérifié). **Point dur (recherche intacte)** : `diagnosis` était agrégé par
  `findTopDiagnoses` (`GROUP BY c.diagnosis`) ; chiffré à IV aléatoire, chaque ligne devient son
  propre groupe → cassé. Refactor : repo `findCompletedDiagnoses` (projette la valeur **déchiffrée**
  par le converter) + agrégation « top pathologies » **en Java** dans `ReportService.topDiagnoses`
  (tx readOnly, group/count/sort/limit) → épidémiologie intacte. Compat ascendante assurée par
  `AesGcmCipher.decrypt` qui tolère les valeurs legacy sans préfixe `gcm:` (seeds SQL). +2 tests
  `ClinicalPhiEncryptionTest` (diagnosis JDBC brut = `gcm:…` / clair via JPA ; top pathologies
  comptées 2 vs 1 malgré chiffrement). *Vérifié* : `mvnd test` → **224 verts, 0 skip**.
- [x] **D3b — Chiffrer les fichiers uploadés au repos + rotation de clé outillée. ✅ FAIT 2026-06-30.**
  `AesGcmCipher` gagne une variante **binaire** (`encryptBytes`/`decryptBytes`/`isEncryptedBytes`,
  format `MAGIC "GCM1"(4) ‖ IV(12) ‖ ciphertext+tag` ; le marqueur ne collisionne pas avec
  JPEG/PNG/WebP → distingue clair vs chiffré). Nouveau `FileEncryptionService` : clé courante +
  **clé précédente optionnelle** (repli), `encrypt`/`decrypt` (tolère les fichiers clairs legacy) et
  `rotateAll(root)` (relit courante/ancienne/clair → ré-écrit chiffré courant ; chiffre aussi au
  passage les fichiers encore en clair). `FileStorageService.storeImage` écrit désormais le contenu
  **chiffré** (`Files.write` au lieu de `transferTo`) et `load` **déchiffre**. **Servir** : le handler
  de ressources statiques `/uploads/**` est **remplacé** par `UploadedFileController`
  (`GET /uploads/**` → `load()` déchiffre, `Cache-Control: no-store` car PHI) — sinon les images
  seraient servies chiffrées. **Rotation outillée** : `POST /api/admin/maintenance/rotate-file-encryption`
  (`MaintenanceApiController`, `hasAnyRole('ADMIN','SUPER_ADMIN')`) → `{"rotated": n}`. **Clé dédiée**
  `app.storage.encryption.key` (défaut = clé maître `app.encryption.key`) + `…previous-key` →
  **découple la rotation des fichiers de celle des colonnes PHI** (D3a) : tourner la clé des fichiers
  ne rend jamais la base illisible. `.env.example` + `application.properties` documentés. +6 tests
  `FileEncryptionTest` (chiffré sur disque/clair via load ; legacy passthrough ; rotation
  ancienne→nouvelle clé + l'ancienne ne lit plus ; rotation chiffre le legacy ; comptage ; répertoire
  absent → 0). **NB** : full master-key rotation des colonnes PHI = hors périmètre (re-save d'entités,
  futur). *Vérifié* : `mvnd test` → **230 verts, 0 skip**.

> **✅ Bloc D3 (crypto) TERMINÉ** — D3a (colonnes PHI consultation/labo) + D3b (fichiers au repos +
> rotation outillée) faits. Parqué : rotation de la **clé maître** des colonnes PHI (batch re-save).

### D4 — Fonctionnel / UX (polish)
- [x] **D4a — Export `format=pdf|excel` sur `/api/reports/*` + images radio base64 en PDF. ✅ FAIT 2026-06-30.**
  Nouveau `export/ReportExportService` : **centralise** le mapping rapport→document (KPI/sections)
  pour les 6 rapports tabulaires (financier, activité, épidémio, impayés, caisse, stock) — PDF via le
  template print générique `reports/pdf-report`, Excel via `ExcelExportService`. `ReportApiController`
  gagne `?format=pdf|excel` (défaut **JSON** inchangé ; binaire en **pièce jointe** pour API/desktop) sur
  `daily-cash`/`monthly-financial`/`activity`/`epidemiology`/`outstanding`/`stock` ; les 3 dashboards
  composites restent JSON-only. `ReportWebController` **refactoré** pour déléguer au service (suppression
  des helpers dupliqués `reportPdf`/`kpi`/`row`/`section`/`rowsOf`/`period`/`money` → controller bien plus
  court, aucune régression sur `/reports/*/pdf` + `/outstanding/excel`). **Images radio en PDF** :
  `RadiologyImageDto.dataUri` + `RadiologyService.getBulletinDto` relit chaque image via `FileStorageService`
  (déchiffrée, D3b), l'encode en `data:image/...;base64,…` ; `bulletin.html` affiche les images en mode PDF
  (avant : masquées car openhtmltopdf ne suit pas les URL `/uploads/**` chiffrées+auth) ; `bulletinPdf` les
  embarque. +6 tests `ReportApiExportTest` (API pdf/excel/JSON-défaut + `getBulletinDto` produit le data-URI
  + bulletin radio PDF valide après upload). **NB** : `ResponseEntity<?>` sur les endpoints API (DTO→JSON par
  défaut, byte[] sinon) ; nom de fichier en pièce jointe (`bilan-financier-YYYY-MM.pdf`, `impayes.xlsx`, …).
  *Vérifié* : `mvnd test` → **236 verts, 0 skip**.
- [x] **D4b — Portail patient : annulation RDV + téléchargement PDF + profil/mot de passe. ✅ FAIT 2026-06-30.**
  Les 3 actions sous `hasRole('PATIENT')`, **cloisonnées au dossier du patient connecté** (résolu via
  `PortalService.currentPatient()`). **(1) Annulation RDV** : `POST /portal/appointments/{id}/cancel` —
  vérifie l'ownership (sinon `AccessDeniedException`→403), n'autorise que PLANIFIE/CONFIRME (sinon flash
  « trop tard »), puis `appointmentService.cancel`. Bouton dans `appointments.html` (formulaire POST+CSRF,
  `confirm()` via `data-confirm`). **(2) Téléchargements PDF** : nouveau `portal/PortalDocumentService`
  centralise bulletins labo/imagerie + ordonnances + reçus — **ownership obligatoire** + labo/imagerie
  seulement si **validés** (VALIDE/LIVRE) ; réutilise `PdfExportService` + `radiologyService.getBulletinDto`
  (images base64, D4a). Endpoints `/portal/{lab,radiology,prescriptions,invoices}/…/pdf` (servis via
  `BillingWebController.pdfInline`, passé **public**). Liens ⬇ PDF + nouvelle section « Ordonnances »
  (résolues par consultation) dans `record.html`. **(3) Profil/mot de passe** : `GET /portal/profile` +
  `POST /portal/profile/password` → `UserService.changeOwnPassword(userId, current, new)` (vérifie l'actuel,
  applique la politique ≥8+chiffre, **bump token-version + revokeAll** pour couper les autres sessions).
  Template `portal/profile.html` + lien nav « Mon profil ». 22 clés i18n `portal.*` ×3 langues (FR/EN/AR).
  +9 tests `PortalTest` (profil/changement+restauration/confirmation-différente, téléchargements labo/ordo/
  reçu, **doc d'autrui→403**, annulation RDV, **RDV d'autrui→403**). **NB** : `AccessDeniedException` levée
  en contrôleur/service → 403 par `ExceptionTranslationFilter` (le `@ExceptionHandler` portail ne gère que
  `ResourceNotFoundException` → page no-record). *Vérifié* : `mvnd test` → **245 verts, 0 skip**.
- [x] **D4c — Recherche globale étendue + libellés CIM-10 + « top pathologies » sur codes. ✅ FAIT 2026-06-30.**
  **(1) Palette ⌘K étendue** : `GlobalSearchService` gagne 3 catégories gatées par module (mêmes
  règles que la sidebar) — Consultations (`CONSULTATIONS`, recherche par nom patient OU code CIM-10
  via `ConsultationRepository.searchForPalette` — `icd10_codes` est non chiffré), Rendez-vous
  (`APPOINTMENTS`, par nom patient, `AppointmentRepository.searchForPalette` → `/appointments/{id}/edit`),
  Médicaments (`PHARMACY`, réutilise `DrugRepository.search` → `/pharmacy/drugs/{id}/edit`). 3 clés
  i18n `search.section.{consultations,appointments,drugs}` ×3 langues. `search.js`/`results.html`
  inchangés (rendu générique label/sublabel/icône emoji). **(2) Libellés CIM-10 sur la fiche** :
  `Icd10Service` gagne `splitCodes`/`titlesByCode`/`resolveCodes`/`displayLabel` (+ repo
  `findByCodeInUpper` résolution en lot) ; `ConsultationDto.icd10Resolved` rempli **seulement** dans
  `getDtoById` (fiche détail, pas dans `toDto` réutilisé partout) ; `consultations/detail.html` liste
  « CODE — Titre » (code seul si hors catalogue). **(3) Top pathologies sur codes** :
  `ReportService.topDiagnoses` agrège désormais `ConsultationRepository.findCompletedIcd10Codes`
  (TERMINE, codes non vides) — découpe chaque chaîne multi-codes, compte par code, top N, puis
  résout les libellés en un lot (« B54 — Paludisme… »). Remplace l'ancien `findCompletedDiagnoses`
  (texte libre déchiffré) — **supprimé** (plus de couplage au chiffrement D3a pour l'épidémio).
  Test D3a `top_pathologies_*` réécrit sur les codes (preuve du découpage multi-codes B54=2/J45=2) ;
  +2 `GlobalSearchTest` (consultation par code K29 ; médicament Paracétamol) +1 `Icd10CatalogTest`
  (résolution ordre/uppercase/inconnu→null). **NB** : `findByCodeInUpper` compare en `UPPER()` ↔
  `splitCodes` normalise déjà uppercase. *Vérifié* : `mvnd test` → **248 verts, 0 skip** (Docker présent).
- [x] **D4d — i18n des reliquats FR en dur (balayage de complétude). ✅ FAIT 2026-06-30.**
  Balayage outillé : (a) liste des templates **sans aucun `#{`** + (b) sweep des caractères accentués
  français **non liés** à un `th:*`/`#{` (hors commentaires/CSS/JS). L'onglet **Aperçu** patient
  signalé dans le backlog était **déjà traduit** (note périmée). Reliquats réels = **6 vues**
  totalement/majoritairement FR en dur, désormais traduites (FR/EN/AR) : `notifications/list.html`
  (boîte de réception entière), `dashboard-doctor.html` (titres KPI/panneaux + en-têtes + états vides
  + badges `status.*`/`priority.*` qui affichaient l'enum brut), `error.html` (titre + retour accueil),
  `fragments/ui.html` (bouton **← Retour** partagé → `common.back`), `setup/wizard.html` (assistant
  d'installation entier, + page rendue `th:lang`/`th:dir` pour RTL), `teleconsultation/room.html`
  (salle vidéo autonome, idem RTL). **Tous les autres « hits » du sweep étaient des faux positifs** :
  valeurs par défaut de `th:text` multi-lignes (non rendues), commentaires, emojis, et libellés de
  langue intentionnels (Français/English/العربية dans les sélecteurs, montrés dans leur propre langue).
  **~60 clés** neuves ×3 langues (namespaces `error.*`, `notifications.*`, `dashboard.doctor.*`,
  `setup.*`, `teleconsultation.*` + `common.{patient,doctor,reason,diagnosis,number,priority}`
  réutilisables) ; **bundles ré-alignés à 1401 clés** sur FR/EN/AR (diff vide). +2 tests
  `PageRenderSmokeTest` (`notifications` + dashboard médecin EN). **NB** : badges enum désormais via
  `#{${'status.'+x}}`/`#{${'priority.'+x}}` (cf. [[thymeleaf-sec-authorize-attribute-form]] dyn-key) ;
  back partagé via `th:text="'← ' + #{common.back}"`. *Vérifié* : `mvnd test` → **250 verts, 0 skip**.

> **✅ Bloc D4 (fonctionnel/UX) TERMINÉ** — D4a→D4d faits. **➡️ Chantier D entier terminé (12/12)** —
> et avec lui **tous les chantiers A→D du tracker de finitions**. Reste hors périmètre : C2-reliquat
> (ARIA tablist), parqués A/B/C, et le **Tier 2** (Z1→Z6, grosses features) — à promouvoir explicitement.

---

## Z — TIER 2 : grosses features parquées (listées pour exhaustivité — HORS périmètre finitions)

> Ce ne sont **pas** des finitions mais des **features** complètes, déférées par décision. Listées
> ici pour que **rien ne soit perdu**. À **promouvoir** explicitement dans le tableau de bord si
> l'utilisateur le décide ; sinon, ne pas les traiter dans une session « finitions ».

> **🛑 DÉCISION UTILISATEUR 2026-07-01 — Z1, Z2, Z3 ABANDONNÉS (ne pas reproposer).** Aucun n'est
> bloquant ; chacun exige soit du matériel/budget récurrent (GPU pour le scribe, VPS Jitsi), soit de
> la technicité lourde, pour une valeur pas assez immédiate au contexte Niger. L'utilisateur a
> explicitement choisi de les **laisser tomber** — **y compris la notif SMS du lien de téléconsult**
> (le seul « quick win » de Z3), jugée non-primordiale. **Ne PAS les resurfacer** dans une session
> future sauf demande explicite ré-ouvrant le sujet. Ils restent listés ci-dessous pour mémoire seule.

- **Z1 — FHIR avancé** [ex-P2.1] — *abandonné (voir décision ci-dessus).* Interop machine-à-machine
  sans écosystème d'échange au Niger : écritures (`POST`/transaction Bundle), serveur HAPI `RestfulServer`
  complet (`_format`/recherche avancée/pagination), ressources `Practitioner`/`DiagnosticReport`/
  `Condition`/`AllergyIntolerance`, **codage LOINC réel** (`lab_test_catalog.code`→LOINC), SMART-on-FHIR.
  (La couche FHIR **lecture seule** P2.1 reste en place et suffit comme badge d'interopérabilité.)
- **Z2 — Scribe IA étage 1 (audio→texte)** [ex-P4.1] — *abandonné.* Poste de coût réel (GPU
  auto-hébergé **ou** ~0,10 $/consult cloud) + upload audio en connexion faible + mur PHI (rompt la
  posture « tout local chiffré ») : captation micro front, STT FR/accents, streaming, éval qualité,
  mapping typé des constantes, posture PHI définitive. (L'**étage 2** texte→note P4.1 reste dispo,
  désactivé par défaut.)
- **Z3 — Télémédecine avancée** [ex-P3.7] — *abandonné, notif SMS incluse.* Notif du lien au patient
  (SMS/email), **Jitsi auto-hébergé + JWT de salle**, fenêtre temporelle, salle d'attente/présence,
  enregistrement. (La téléconsult **légère** P3.7 — lien Jitsi public par RDV — reste fonctionnelle.)
- **Z4 — Mobile Money : RE-SCOPÉ Niger (2026-07-01).** L'ancien Z4 (« Mobile Money actif » :
  initiation USSD/STK, SDK réels Orange/Wave/MTN, signatures par agrégateur, import CSV) a été
  **jugé obsolète** : il datait d'avant la correction marché → **Niger**, où les modes réels
  **AmanaTa/MyNITA sont des paiements par QR marchand SANS API/webhook public** (flux manuel par
  conception) et où Orange/Wave/MTN ont été **retirés du menu d'encaissement**. Le récepteur webhook
  P3.3 reste **dormant** (hook futur si Orange Money — qui a une API au Niger — est un jour
  ré-introduit, décision produit séparée). Re-scopé vers **ce qui a du sens au Niger**, en 2 slices :
  - [x] **Z4a — Journal admin des webhooks. ✅ FAIT 2026-07-01.** Vue **SUPER_ADMIN**
    `/admin/payment-webhooks` (read-only) sur `payment_webhook_events` : filtres fournisseur/statut/
    plage de dates, badges d'issue (Encaissé/Rejeté/Doublon/Reçu), montant, facture, détail d'erreur ;
    cap 200 lignes (patron `AdminAuditWebController`). **Gate SUPER_ADMIN** délibéré : la table est
    **globale** (le webhook arrive sans contexte de tenant, pas de `@TenantId`/`clinic_id`) → un ADMIN
    de clinique y verrait les factures d'autres cliniques ; le rôle transverse est le bon propriétaire
    d'un journal d'intégration plateforme. Nav auto-câblée via le registre : module `ADMIN_WEBHOOKS`
    (`Module` enum, Section.ADMIN) ajouté à `RoleProfile.SUPER_ADMIN` (pas de `sec:authorize` manuel).
    Repo `PaymentWebhookEventRepository.search(...)` (filtres optionnels null-safe, `Pageable`) +
    `distinctProviders()`. Template `admin/payment-webhooks/list.html`. 21 clés i18n
    (`nav.admin_webhooks` + `admin.webhooks.*`, dont `st_{RECEIVED,PROCESSED,REJECTED,DUPLICATE}`) ×3.
    +3 tests `AdminPaymentWebhooksTest` (SUPER_ADMIN 200 + évènement affiché ; ADMIN → 403 ; filtre
    statut exclut les autres ; `@Transactional` → seed visible même-tx puis rollback). *Vérifié* :
    `mvnd test` → **254 verts, 0 skip**.
  - [x] **Z4b — Rapprochement des paiements QR manuels (AmanaTa/MyNITA). ✅ FAIT 2026-07-01.**
    Décision utilisateur : **marquage manuel** (pas d'import CSV — format non documenté). **Migration
    V33** : `payments.reconciled_at`/`reconciled_by` (2 colonnes nullables + index, portable H2+PG) ;
    `Payment` enrichi (`reconciledAt` + `reconciledBy` @ManyToOne User). Vue **`/billing/reconciliation`**
    (`@PreAuthorize hasAnyRole('OWNER','CAISSIER')` — ADMIN→OWNER post-P6) : paiements méthode
    `AMANATA/MYNITA` **du jour** (filtre date + « non-rapprochés seulement »), synthèse (total/en-attente/
    montant en attente/**sans référence**), action **Rapprocher/Annuler** (POST toggle) qui estampille
    `reconciled_at`/`by = currentUser`. **Tenant-scopé** (contrairement à Z4a : `Payment` est `@TenantId`
    → `findById` cloisonné, un caissier ne rapproche que les paiements de SA clinique). `BillingService`
    (`reconciliationReport(day, pendingOnly)` mappé en-tx, compteurs sur **tous** les QR du jour même
    quand la liste est filtrée ; `toggleReconciled`), repo dérivé `findByMethodInAnd…`, DTO
    `ReconciliationReportDto` + `PaymentDto` (`reconciledAt`/`reconciledByName` + dérivés `isReconciled`/
    `isMissingReference`). Lien depuis le dashboard caisse (gated `sec:authorize`). 24 clés i18n
    `billing.reconciliation.*` ×3. +4 tests `BillingReconciliationTest` (QR-only, toggle+compteurs+
    pendingOnly, gating CAISSIER 200 / SECRETAIRE 403 ; patron tenant `@BeforeTransaction`+`@WithUserDetails`).
    *Vérifié* : `mvnd test` → **258 verts, 0 skip**.

> **✅ Chantier Z4 (re-scopé Niger) TERMINÉ** — Z4a (journal admin) + Z4b (rapprochement QR manuel) faits.
> Parqué : Mobile Money **actif** réel (initiation + confirmation auto) — n'a de sens qu'avec Orange Money
> Niger (API existante), décision produit distincte.
- ~~**Z5 — Patient overview avancé** [ex-P3.6]~~ — **✅ PROMU + FAIT 2026-07-01.** Les 4 volets sur
  l'onglet **Aperçu** du dossier (`patients/detail.html`), tous dérivés en mémoire par
  `PatientOverviewService` (toujours **zéro requête**, pur sur les listes déjà chargées) :
  **(1) CPN maternité comme évènements** — chaque `PrenatalVisitDto` (CPN, date→`atStartOfDay`) + un
  évènement **accouchement** (si `deliveryDate`) rejoignent la timeline (catégorie `maternity`, icônes
  🤰/👶, lien `/maternity/{id}`, sous-titres âge gestationnel+tension / type+poids nouveau-né).
  **(2) Sparkline des constantes** — `VitalsSparklineDto` par mesure (poids/tension syst./pouls/temp)
  ayant **≥ 2 points** ; coordonnées `<polyline>` SVG **normalisées côté service** (viewBox 120×32) au
  format **`Locale.US`** (point décimal — la virgule FR casserait l'attribut `points`) ; rendu en
  `<svg role=img aria-label>` inline. **(3) Filtres de timeline par type** — `TimelineEventDto` gagne
  une **`categoryKey`** stable (consultation/lab/imaging/hospitalization/billing/maternity) ; puces
  `.tl-filter[data-cat]` construites depuis `timelineFilters` (catégories présentes, ordre canonique,
  compteurs), affichées si > 1 catégorie. **(4) Pagination dossier volumineux** — `ul.timeline[data-paged=15]`
  + bouton `#tl-more` ; `js/ui.js initTimeline()` combine **filtre + révélation progressive** par paquets
  (rendu 100 % serveur, JS ne fait que masquer/révéler → offline-safe, **aucune PHI en JS**). 15 clés
  i18n `patients.overview.*` ×3 langues (bundles ré-alignés). CSS `.spark*`/`.tl-filters`/`.tl-filter`
  ajoutés à `app.css`. +1 test `PatientOverviewServiceTest` (CPN dans la timeline + filtres comptés +
  4 sparklines, coords Locale.US assertées par regex) ; `A11yAxeTest`/`PageRenderSmokeTest` sur
  `/patients/1` re-verts (SVG + puces, 0 violation). *Vérifié* : `mvnd test` → **251 verts, 0 skip**.
- ~~**Z6 — Let's Encrypt automatisé** [ex-P2.5]~~ — **✅ PROMU + FAIT 2026-07-01.** TLS prod automatisé
  (certbot), émission + renouvellement sans intervention. **Calque opt-in** `docker-compose.letsencrypt.yml`
  (superposé au compose de base) : bascule nginx sur `nginx/nginx.letsencrypt.conf` (challenge ACME http-01
  servi depuis un webroot partagé + certificats certbot au **chemin fixe `live/clinic/`** via `--cert-name
  clinic` → conf indépendante du domaine, **0 templating**) et ajoute un compagnon `certbot` qui renouvelle
  en boucle (12 h) ; nginx recharge en boucle (6 h) → récupère les certifs renouvelés en place, **sans
  reload inter-conteneurs**. Bootstrap one-shot `init-letsencrypt.sh` (certif factice → nginx up → certbot
  certonly webroot → reload ; gère le chicken-and-egg + `LETSENCRYPT_STAGING`). `.env.example` (DOMAIN /
  LETSENCRYPT_EMAIL / LETSENCRYPT_STAGING), `nginx/README.md` + `docs/DEPLOYMENT.md` documentés ;
  `.gitattributes` force LF sur `*.sh` (shebang). **Le mode auto-signé LAN reste le défaut intact** (ne pas
  inclure le calque). *Vérifié* : `docker compose -f … -f docker-compose.letsencrypt.yml config` OK + merge
  inspecté (service certbot, conf LE, volumes, boucles reload/renew présents) ; base seule toujours valide.
  **Non exerçable ici** : l'émission ACME réelle exige un domaine public + 80/443 ouverts (limite inhérente
  à Let's Encrypt) — couverte par revue + validation de la tuyauterie compose.

---

## E — TIER E : extensions cliniques (nouveau chantier, 2026-07-01)

> Issu d'une comparaison du produit à deux descriptions IA (ChatGPT/Gemini) d'un « système
> moderne complet ». Le noyau ClinicApp est au niveau (souvent au-delà sur le non-fonctionnel) ;
> ces 3 extensions sont les **seuls candidats à vraie valeur** hors modules « ERP hospitalier »
> (bloc/RH/compta/biomed — jugés hors-scope clinique ou à externaliser). Ordre recommandé E1→E3.

- [x] **E1 — Certificats médicaux. ✅ FAIT 2026-07-01.** Pkg `certificate` : entité `MedicalCertificate`
  (`@TenantId` ; FK patient/médecin/consultation-opt ; `type` ; numérotation `CERT-YYYY-NNNNN` **préfixe
  constant + native GLOBALE** — comme les ordonnances, uniques inter-cliniques ; dates de repos +
  `rest_days` calculé bornes inclusives ; corps texte). **V34** (`medical_certificates`). Web MEDECIN
  (`/certificates` : liste/new prefill(consultation|patient)/edit/**print=détail**/**pdf** via
  `PdfExportService`, patron impression ordonnance). Raccourci « 📄 Certificat » sur la consultation.
  **Confidentialité** : le médecin = émetteur (user courant) ; le corps est saisi à la main, **diagnostic
  jamais injecté** ; dates de repos effacées si type ≠ arrêt/repos. 7 types (`certificates.type.*`).
  26 clés i18n ×3. +5 tests `CertificateTest` (numéro+repos calculé+nettoyage non-repos ; prefill
  consultation ; PDF binaire ; gating MEDECIN 200/SECRETAIRE 403 ; patron tenant). *Vérifié* :
  `mvnd test` → **263 verts, 0 skip**.
- [x] **E1-bis — Téléchargement des certificats au portail patient. ✅ FAIT 2026-07-01.**
  `PortalDocumentService.certificatePdf` (cloisonné `requireOwnership` → 403 si pas le dossier du
  patient connecté) + endpoint `/portal/certificates/{id}/pdf` + section « Certificats » sur
  `portal/record.html` (⬇ PDF par ligne). 2 certificats seedés dans `DataInitializer` (p1=CERT-…-00001
  arrêt de travail, p2=CERT-…-00002 bonne santé). 3 clés i18n ×3 (`portal.record.certificates`/
  `no_certificates`/`portal.col.type`). +2 tests `PortalTest` (télécharge le sien 200 / celui d'autrui
  403). *Vérifié* : `mvnd test` → **265 verts, 0 skip**.
- **E2 — Allergies + interactions médicamenteuses (advisory, jamais bloquant).** Deux sous-slices :
  - [x] **E2-A — Allergies. ✅ FAIT 2026-07-01.** Colonne `drugs.allergen_class` (**V35**, nullable,
    curée au catalogue) + champ au formulaire médicament. `AllergyChecker` (`@Component` **pur, ne jette
    jamais**) : le texte d'allergie du patient (déchiffré) **contient-il** la classe allergène curée / la
    DCI / le nom commercial (normalisé sans accents, ≥3 car.) ? → `List<AllergyWarningDto>`. Intégré au
    **formulaire de dispensation** (`PharmacyWebController.prepareDispenseForm` : recoupe les allergies
    du patient avec les médicaments **présents** dans la dispensation — bannière `alert-error` role=alert,
    non-bloquante). Comble le manque documenté « le pharmacien voit immédiatement les allergies ». Seed :
    Amoxicilline taguée « Pénicilline » (p2 y est allergique). 5 clés i18n ×3. +5 tests
    `AllergyCheckerTest` (classe/DCI/nom, accents, aucun recoupement, null/vide, multi-médicaments).
    **Limite v1** : seules les lignes **déjà présentes** au rendu serveur sont couvertes (les lignes
    ajoutées en JS non — évolution possible). Surface **prescripteur** (consultation) = évolution. *Vérifié*
    `mvnd test` → **270 verts, 0 skip**.
  - [x] **E2-B — Interactions médicamenteuses. ✅ FAIT 2026-07-01.** Table **globale** `drug_interactions`
    (**V36**, référence universelle **non-`@TenantId`** comme `icd10_catalog` ; seedée par migration :
    8 paires connues Warfarine/Aspirine, Tramadol/Fluoxétine, Simvastatine/Clarithromycine…) + `Severity`
    MINEURE/MODEREE/MAJEURE. `InteractionChecker` (`@Component` pur, ne jette jamais, frère de
    `AllergyChecker`) : une règle déclenche si **deux médicaments distincts** de la dispensation
    correspondent à ses deux DCI (DCI ou nom, normalisé sans accents). Intégré au **formulaire de
    dispensation** (bannière `role=alert` non-bloquante, badge de sévérité coloré + description). Le
    contrôleur charge les règles actives via `DrugInteractionRepository.findByActiveTrue()` → DTO → check
    sur les médicaments présents (mutualisé avec E2-A via `prescribedDrugs`). 4 clés i18n ×3. +6 tests
    (`InteractionCheckerTest` pur ×5 : paire/1-seul/accents/auto-interaction/entrées insuffisantes ;
    `DrugInteractionSeedTest` : seed chargé + déclenche). *Vérifié* `mvnd test` → **276 verts, 0 skip**.

> **✅ Chantier E2 (sécurité pharmaceutique) TERMINÉ** — E2-A allergies + E2-B interactions. Advisory,
> jamais bloquant, à la dispensation. Parqué : CRUD admin du catalogue d'interactions (seed-only pour
> l'instant) ; surface prescripteur (consultation) ; couverture des lignes ajoutées en JS.
- [ ] **E3 — MFA (2ᵉ facteur).** TOTP (recommandé, offline/gratuit) vs SMS OTP (réutilise Africa's
  Talking mais plus faible + coût/login). Sur **les 2 chaînes** (session web + JWT) + enrôlement +
  **codes de secours** + reset admin + interaction rate-limit/lockout existant. Décisions à trancher :
  facteur, obligatoire (ADMIN/OWNER/SUPER_ADMIN) vs opt-in. *Touche le cœur auth → tests soignés ;
  charge de support réelle en clinique peu tech. À faire en dernier.*

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
| 2026-07-01 | **E2-B — interactions médicamenteuses → chantier E2 TERMINÉ** | Table **globale** `drug_interactions` (**V36**, non-`@TenantId` comme `icd10_catalog`, seed 8 paires par migration) + `Severity` MINEURE/MODEREE/MAJEURE. `InteractionChecker` (`@Component` pur, frère d'`AllergyChecker`) : règle déclenche si 2 médicaments **distincts** matchent ses 2 DCI (DCI/nom, sans accents). Bannière non-bloquante `role=alert` + badge sévérité au formulaire de dispensation ; règles chargées via `DrugInteractionRepository.findByActiveTrue()`, mutualisé E2-A/B via `prescribedDrugs`. 4 clés i18n ×3. +6 tests (`InteractionCheckerTest` ×5 pur + `DrugInteractionSeedTest` seed+déclenche). NB : global (aucun tenant requis), CRUD admin du catalogue = évolution (seed-only). **276 verts, 0 skip.** |
| 2026-07-01 | **E2-A — vérification des allergies à la dispensation** | `drugs.allergen_class` (**V35**, curée) + champ formulaire médicament. `AllergyChecker` (`@Component` pur, ne jette jamais) : allergies patient (déchiffrées) contiennent-elles la classe allergène / DCI / nom (normalisé sans accents, ≥3 car.) → `AllergyWarningDto`. Bannière **non-bloquante** `role=alert` au formulaire de dispensation (`PharmacyWebController.prepareDispenseForm`, recoupe patient × médicaments présents). Comble le manque « pharmacien voit les allergies ». Seed : Amoxicilline→« Pénicilline » (p2 allergique). 5 clés i18n ×3. +5 tests `AllergyCheckerTest`. NB : seules les lignes présentes au rendu serveur couvertes (JS non). **270 verts, 0 skip.** |
| 2026-07-01 | **E1-bis — certificats téléchargeables au portail patient** | `PortalDocumentService.certificatePdf` (ownership → 403 sinon) + `/portal/certificates/{id}/pdf` + section « Certificats » sur `portal/record.html`. 2 certificats seedés (`DataInitializer` : p1 arrêt de travail, p2 bonne santé). 3 clés i18n ×3. +2 tests `PortalTest` (le sien 200 / autrui 403). Patron D4b réutilisé tel quel. **265 verts, 0 skip.** |
| 2026-07-01 | **E1 — certificats médicaux (nouveau Tier E, issu de la comparaison IA)** | Pkg `certificate` : `MedicalCertificate` (@TenantId, FK patient/médecin/consultation-opt, `CERT-YYYY-NNNNN` **native GLOBALE** comme ordonnances, repos + `rest_days` bornes inclusives, corps texte) + **V34**. Web MEDECIN `/certificates` (liste/new prefill/edit/print=détail/**pdf** via `PdfExportService`, patron ordonnance). Raccourci « 📄 Certificat » sur la consultation. Confidentialité : médecin=user courant, **diagnostic jamais injecté**, dates de repos effacées si type≠arrêt/repos. 7 types. 26 clés i18n ×3. +5 tests `CertificateTest` (numéro/repos/nettoyage ; prefill ; PDF ; gating MEDECIN 200/SECRETAIRE 403 ; patron tenant `@BeforeTransaction`+`@WithUserDetails`). NB : numérotation **native non-tenant** obligatoire (colonne `unique` globale, sinon collision inter-cliniques). **263 verts, 0 skip.** |
| 2026-07-01 | **Décision — Z1/Z2/Z3 abandonnés (aucun code)** | Après revue détaillée (valeur/acteurs/coût matériel/technicité/PHI), l'utilisateur choisit de **laisser tomber** Z1 (FHIR avancé — pas d'écosystème d'échange Niger), Z2 (scribe audio — GPU/coût-par-consult + mur PHI), Z3 (téléméd. avancée — **notif SMS incluse**, jugée non-primordiale). Non-bloquants. **Ne pas reproposer.** Les fondations livrées (FHIR lecture P2.1, scribe étage 2 P4.1, téléconsult légère P3.7) restent en place. **➡️ Tout le tracker (A→D + Tier Z retenu) est clos** — plus de travail planifié ; toute suite = nouvelle demande utilisateur. Suite inchangée (258 verts). |
| 2026-07-01 | **Z4b — rapprochement des paiements QR manuels (AmanaTa/MyNITA) → chantier Z4 TERMINÉ** | Marquage manuel (pas de CSV). **V33** : `payments.reconciled_at`/`reconciled_by` (nullables+index) + `Payment` enrichi. Vue `/billing/reconciliation` (`hasAnyRole('OWNER','CAISSIER')`) : QR du jour, filtre date + non-rapprochés, synthèse (total/attente/montant/**sans réf**), toggle Rapprocher/Annuler (estampille `reconciled_at`/`by=currentUser`). **Tenant-scopé** (`Payment` @TenantId → `findById` cloisonné). `BillingService.reconciliationReport(day,pendingOnly)` (mappé en-tx, compteurs sur tous les QR même si liste filtrée) + `toggleReconciled` ; repo dérivé `findByMethodInAnd…` ; DTO `ReconciliationReportDto`+`PaymentDto` (`reconciledAt`/`reconciledByName`/`isReconciled`/`isMissingReference`). Lien dashboard caisse (`sec:authorize`). 24 clés i18n ×3. +4 tests `BillingReconciliationTest` (QR-only ; toggle+compteurs+pendingOnly ; CAISSIER 200/SECRETAIRE 403 ; patron `@BeforeTransaction`+`@WithUserDetails`). **258 verts, 0 skip.** |
| 2026-07-01 | **Z4 re-scopé Niger + Z4a — journal admin des webhooks** | **Décision** : ancien Z4 (SDK Orange/Wave/MTN, USSD/STK, CSV) jugé **obsolète** (marché Niger = AmanaTa/MyNITA QR **sans API**, aggrégateurs retirés du menu ; webhook P3.3 dormant). Re-scopé en Z4a (journal admin) + Z4b (rapprochement QR manuel, à venir). **Z4a fait** : vue **SUPER_ADMIN** `/admin/payment-webhooks` (read-only) sur `payment_webhook_events` — filtres fournisseur/statut/dates, badges d'issue, cap 200. Gate SUPER_ADMIN car table **globale** (webhook sans tenant → un ADMIN clinique verrait d'autres cliniques). Nav auto : module `ADMIN_WEBHOOKS` (`Module`/Section.ADMIN) ajouté à `RoleProfile.SUPER_ADMIN`. Repo `search(...)`+`distinctProviders()`. Template `admin/payment-webhooks/list.html`. 21 clés i18n ×3. +3 tests `AdminPaymentWebhooksTest` (SUPER_ADMIN 200/ADMIN 403/filtre statut, `@Transactional`). **254 verts, 0 skip.** |
| 2026-07-01 | **Z5 (promu) — Patient overview avancé (Aperçu dossier)** | 4 volets sur `patients/detail.html`, tous dérivés **en mémoire** par `PatientOverviewService` (zéro requête). **(1) CPN + accouchement** dans la timeline (catégorie `maternity`, `PrenatalVisitDto`→`atStartOfDay`, 🤰/👶, lien `/maternity/{id}`). **(2) Sparklines** `VitalsSparklineDto` (poids/tension/pouls/temp, ≥2 pts) — `<polyline>` SVG normalisé service (viewBox 120×32) format **`Locale.US`** (virgule FR casserait `points`), rendu `<svg role=img aria-label>`. **(3) Filtres** : `TimelineEventDto.categoryKey` stable + puces `.tl-filter[data-cat]` depuis `timelineFilters` (compteurs, si >1 cat.). **(4) Pagination** `ul.timeline[data-paged=15]` + `#tl-more` ; `js/ui.js initTimeline()` combine filtre+révélation progressive (100 % serveur, offline-safe, 0 PHI en JS). 15 clés `patients.overview.*` ×3. CSS `.spark*`/`.tl-filter*`. +1 test `PatientOverviewServiceTest` (CPN+filtres+4 sparklines, coords Locale.US par regex) ; `A11yAxeTest`/smoke `/patients/1` re-verts. **251 verts, 0 skip.** |
| 2026-07-01 | **Z6 (promu) — Let's Encrypt automatisé (TLS prod)** | Calque opt-in `docker-compose.letsencrypt.yml` : nginx → `nginx.letsencrypt.conf` (challenge ACME http-01 webroot + certifs certbot au chemin fixe `live/clinic/` via `--cert-name clinic`, sans templating) + compagnon `certbot` (renew loop 12 h) ; nginx reload loop 6 h → pas de reload inter-conteneurs. Bootstrap `init-letsencrypt.sh` (factice→up→certonly webroot→reload, gère chicken-and-egg + STAGING). `.env.example` (DOMAIN/EMAIL/STAGING), `nginx/README.md`+`DEPLOYMENT.md`, `.gitattributes` LF sur `*.sh`. Auto-signé LAN reste le défaut. Vérif : `docker compose -f … -f docker-compose.letsencrypt.yml config` OK + merge inspecté ; émission ACME réelle non exerçable ici (domaine public requis). **Infra only — suite backend inchangée (baseline 250).** |
| 2026-07-01 | **C2-reliquat — pattern ARIA tablist complet** | Sémantique « Tabs » WAI-ARIA portée **en statique** par `patients/detail.html` (9 onglets) + `maternity/record.html` (4) : onglets `id`/`aria-controls`/`tabindex` roving, panneaux `role=tabpanel`/`aria-labelledby`/`tabindex=0`. `js/ui.js initTabs` gère l'état dynamique + **clavier** ←/→/↑/↓/Origine/Fin (activation auto+focus) ; liaison ARIA en filet « si absent ». Comme `A11yAxeTest` audite le HTML **pré-JS**, le statique permet de **lever l'exclusion** `aria-required-children`/`aria-required-attr` (`DEFERRED_RULES` vide) + ajout `/maternity/1` aux vues. Axe **réellement exécuté** (5 tests, 0 skip) → 0 violation tablist. **250 verts, 0 skip.** |
| 2026-06-30 | **D4d — i18n des reliquats FR en dur → bloc D4 + chantier D TERMINÉS** | Balayage outillé (templates sans `#{` + sweep accents non liés à `th:*`/`#{`). Onglet Aperçu déjà traduit (note backlog périmée). 6 vues FR en dur traduites FR/EN/AR : `notifications/list.html`, `dashboard-doctor.html` (titres/colonnes/états + badges enum→`#{status.*}`/`#{priority.*}`), `error.html`, `fragments/ui.html` (← Retour partagé→`common.back`), `setup/wizard.html` (+`th:lang`/`th:dir` RTL), `teleconsultation/room.html` (+RTL). Faux positifs écartés : défauts `th:text` multi-lignes, commentaires, emojis, noms de langue (Français/English/العربية). ~60 clés ×3 (`error/notifications/dashboard.doctor/setup/teleconsultation.*` + `common.{patient,doctor,reason,diagnosis,number,priority}`). **Bundles ré-alignés 1401 clés** (diff vide). +2 tests `PageRenderSmokeTest`. **250 verts, 0 skip.** |
| 2026-06-30 | **D4c — recherche globale étendue + libellés CIM-10 + top pathologies sur codes** | **(1)** `GlobalSearchService` +3 catégories gatées par module : Consultations (`searchForPalette` nom patient OU code CIM-10, non chiffré), Rendez-vous (`searchForPalette` → `/appointments/{id}/edit`), Médicaments (`DrugRepository.search` → `/pharmacy/drugs/{id}/edit`). 3 clés `search.section.{consultations,appointments,drugs}` ×3. **(2)** `Icd10Service.{splitCodes,titlesByCode,resolveCodes,displayLabel}` + repo `findByCodeInUpper` ; `ConsultationDto.icd10Resolved` rempli seulement dans `getDtoById` ; `detail.html` liste « CODE — Titre ». **(3)** `ReportService.topDiagnoses` agrège `findCompletedIcd10Codes` (découpe multi-codes, compte/code, résout libellés en lot) → remplace `findCompletedDiagnoses` (supprimé). Test D3a `top_pathologies_*` réécrit sur codes (B54=2/J45=2, preuve découpage) ; +2 `GlobalSearchTest` (consult K29 / drug Paracétamol) +1 `Icd10CatalogTest` (résolution). **248 verts, 0 skip.** |
| 2026-06-30 | **D4b — portail patient : annulation RDV + PDF + profil/mot de passe** | 3 actions sous `hasRole('PATIENT')`, cloisonnées au dossier (`PortalService.currentPatient()`). Annulation : `POST /portal/appointments/{id}/cancel` (ownership→403, PLANIFIE/CONFIRME seulement). PDF : `PortalDocumentService` (ownership + validé pour labo/imagerie, réutilise `PdfExportService`+`getBulletinDto` images base64 D4a) → endpoints `/portal/{lab,radiology,prescriptions,invoices}/…/pdf` ; `BillingWebController.pdfInline` passé **public** ; liens ⬇ PDF + section Ordonnances dans `record.html`. Profil : `/portal/profile` + `POST /profile/password` → `UserService.changeOwnPassword` (vérifie l'actuel, politique ≥8+chiffre, bump token-version + revokeAll). `portal/profile.html` + lien nav. 22 clés i18n ×3. +9 tests `PortalTest` (dont doc/RDV d'autrui→403). NB : `AccessDeniedException` contrôleur/service → 403 via `ExceptionTranslationFilter`. **245 verts, 0 skip.** |
| 2026-06-30 | **D4a — export pdf/excel API rapports + images radio base64 en PDF** | Nouveau `export/ReportExportService` centralise rapport→PDF(template `reports/pdf-report`)/Excel pour les 6 rapports tabulaires. `ReportApiController` : `?format=pdf|excel` (défaut JSON, binaire en pièce jointe) sur daily-cash/monthly-financial/activity/epidemiology/outstanding/stock ; dashboards JSON-only. `ReportWebController` **refactoré** pour déléguer (suppression helpers dupliqués `reportPdf`/`kpi`/`section`/… → controller raccourci, `/reports/*/pdf`+`/outstanding/excel` intacts). Images radio en PDF : `RadiologyImageDto.dataUri` + `RadiologyService.getBulletinDto` (relit/déchiffre via `FileStorageService`→base64) + `bulletin.html` affiche en mode PDF + `bulletinPdf` embarque. +6 tests `ReportApiExportTest`. NB : `ResponseEntity<?>` (DTO→JSON par défaut, byte[] sinon). **236 verts, 0 skip.** |
| 2026-06-30 | **D3b — chiffrement fichiers au repos + rotation → bloc D3 TERMINÉ** | `AesGcmCipher` variante binaire (`encryptBytes`/`decryptBytes`/`isEncryptedBytes`, format `GCM1‖IV‖ct+tag`, marqueur ≠ JPEG/PNG). `FileEncryptionService` (clé courante + précédente en repli, tolère clair legacy) + `rotateAll(root)`. `FileStorageService.storeImage` écrit chiffré, `load` déchiffre. Handler statique `/uploads/**` **remplacé** par `UploadedFileController` (déchiffre, `Cache-Control: no-store`). Route `POST /api/admin/maintenance/rotate-file-encryption` (ADMIN/SUPER_ADMIN). Clé **dédiée** `app.storage.encryption.key` (défaut = clé maître) + `…previous-key` → découple la rotation fichiers de la base PHI. `.env.example`+`application.properties`. +6 tests `FileEncryptionTest`. **230 verts, 0 skip.** |
| 2026-06-30 | **D3a — chiffrement PHI clinique (consultation + labo)** | `@Convert(PhiStringConverter)` (AES-GCM P2.5) sur Consultation `chief_complaint`/`history`/`physical_exam`/`diagnosis`/`treatment_plan` + LabResult `result_value`/`reference_range`/`notes`. PAS `icd10_codes` (recherché, D4c) / `unit` / constantes numériques. **Aucune migration** (colonnes déjà TEXT). Point dur : `diagnosis` chiffré casse `GROUP BY` SQL (IV aléatoire) → repo `findCompletedDiagnoses` (valeurs déchiffrées) + agrégation top-pathologies **en Java** (`ReportService.topDiagnoses`) → épidémio intacte. Compat legacy via `decrypt` tolérant (pas de préfixe `gcm:`). +2 tests `ClinicalPhiEncryptionTest`. **224 verts, 0 skip.** |
| 2026-06-30 | **D2b — build-info (git/version) + alerting Prometheus** | `/actuator/info` gagne `build` (goal `spring-boot:build-info`→`BuildProperties`) + `git` (plugin `git-commit-id:9.0.1`→`GitProperties`, mode full). `.git` à la racine → `dotGitDirectory=${project.basedir}/../.git`, `failOnNoGitDirectory=false`. Config `management.info.{build,git}.enabled` + `git.mode=full`. Alerting : `monitoring/alert.rules.yml` (3 règles : backend down / heap>90% / 5xx>5%) via `rule_files` dans `prometheus.yml`, monté dans le conteneur ; Alertmanager optionnel (commenté). +2 tests `ActuatorInfoTest`. NB : fichiers générés dans `target/classes` → présents aux tests ; `/actuator/info` mappé sous MockMvc (≠ gotcha prometheus D2a). **222 verts, 0 skip.** |
| 2026-06-30 | **D2a — Grafana provisionné + métriques métier** | Pkg `metrics`/`BusinessMetrics` : compteurs Micrometer tagués **`clinic_id`** (via `TenantContext`) — `clinicapp.consultations.completed` (`ConsultationService.complete`) + `clinicapp.payments.{recorded,amount}` (tag `method`, `BillingService.recordPayment`), enregistrés à la volée (dédup nom+tags). Grafana auto-provisionné : `monitoring/grafana/provisioning/{datasources,dashboards}` + dashboard `clinicapp-business.json` (var `$clinic`, stats + débits + camembert mode + HTTP). `docker-compose` monte provisioning+dashboards dans `grafana`. NB : `baseUnit("XOF")` retiré (sinon nom `..._XOF_total`). NB test : `PrometheusMeterRegistry`/endpoint non câblés en profil test (gotcha P4.3) → assert sur le registre Micrometer, format vérifié en dev. +3 tests `BusinessMetricsTest`. **220 verts, 0 skip.** |
| 2026-06-30 | **D1d — rate-limit IP du login (Bucket4j) → bloc D1 TERMINÉ** | Dép. `bucket4j-core:8.7.0`. `LoginRateLimiter` (`@Component`, token-bucket/IP en mémoire) + `LoginRateLimitFilter` (classe simple, PAS un bean → pas d'auto-enregistrement Boot/double comptage) inséré sur les 2 chaînes avant l'auth → 429 + `Retry-After` au-delà de la limite (X-Forwarded-For-aware). Config `app.security.login-rate-limit.{max-attempts,window-minutes}` (20/15min). Complète le lockout compte (P1.3) contre le DoS distribué. +2 tests `LoginRateLimitTest` (limite=3 via `@TestPropertySource`). NB : limite relâchée (1e6) en profil test (suite enchaîne >20 logins 127.0.0.1 contexte partagé). **217 verts, 3 skip.** |
| 2026-06-30 | **D1c — vue admin « sessions actives » + révocation par appareil** | Page `/admin/users/{id}/sessions` (ADMIN) liste les refresh actifs + bouton Révoquer → `RefreshTokenService.revokeSession(tokenId, userId)` (garde-fou owner, idempotent). Métadonnées appareil (`user_agent`/`ip_address`/`last_used_at`/`created_at` reporté) estampillées au login + **reportées à la rotation** → migration **V32** (3 cols nullables) + `RefreshToken` enrichi ; `AuthController` capture UA+IP (X-Forwarded-For). DTO `RefreshSessionDto` (jamais le jeton). Lien dans `users/list.html` + template `sessions.html` + 15 clés `admin.sessions.*` ×3. **Affinage** : replay révoqué-par-rotation (`replacedById != null`) = vol → coupe la lignée ; révoqué-sans-remplacement (admin/logout) → 401 sans escalade. +3 tests `AdminSessionsTest`. NB : pas de blocklist par jeton → access courant expire en 15 min ; kill immédiat total = logout-all. **215 verts, 3 skip.** |
| 2026-06-30 | **D1b — purge planifiée des refresh tokens** | `RefreshTokenCleanupScheduler` (cron `0 30 3 * * *`) → `RefreshTokenService.purgeStaleTokens()` → repo `deleteExpiredOrRevokedBefore(cutoff)` (`@Modifying` JPQL `expiresAt < cutoff OR revokedAt < cutoff`). Tenant-agnostique (pas `@TenantId`) → **pas de `runAs`**. Rétention `app.jwt.refresh-cleanup-retention-days` (défaut 7 j ; `.env.example`) garde les jetons révoqués pour la détection de vol. +1 test `RefreshTokenCleanupTest` (FK user_id → seed sur id admin). **212 verts, 3 skip.** |
| 2026-06-30 | **D1a — refresh token en cookie HttpOnly (front web)** | Mode opt-in `login {"cookie":"true"}` → refresh en cookie `HttpOnly`/`Secure`/`SameSite=Strict` path `/api/auth`, **jamais en JSON** (access court reste JSON). Sans flag = JSON inchangé (API/desktop). Helper `security/RefreshCookieManager` (`ResponseCookie`→`Set-Cookie`). `/refresh`+`/logout` : cookie prioritaire, rotation repose le cookie, échec/logout efface (`Max-Age 0`) ; `@RequestBody(required=false)`+`HttpServletRequest`. Flag `app.jwt.refresh-cookie-secure` (true par défaut, false dev/test ; `JWT_REFRESH_COOKIE_SECURE` `.env.example`). +3 tests `RefreshCookieTest`. **211 verts, 3 skip.** |
| 2026-06-30 | **B4 — file de synchro hors-ligne → chantier B TERMINÉ** | Décisions verrouillées : RDV seul · file IndexedDB **chiffrée** AES-GCM (clé non-extractible) · conflit → échec+notif+conservé. `js/offline-sync.js` (global, no-op sans IDB/WebCrypto) intercepte le form RDV *hors-ligne*, chiffre+enfile (UUID+statut en clair), rejoue au `online`/`DOMContentLoaded` **en contexte page** (CSRF meta + session). Endpoint `POST /appointments/offline` → `createIdempotent` ; dédup `Idempotency-Key`/`findByRequestKey` → 0 doublon ; col `appointments.request_key VARCHAR(36) UNIQUE` (**V31**, nullable, UNIQUE portable H2+PG). Conflit → 409 → item ÉCHEC conservé. Bannière `#offline-queue-status` (compteurs sans déchiffrer). 6 clés `offline.*` ×3. Background Sync différé (clé/CSRF page + support). +2 tests `OfflineSyncTest`. JS pur non testé MockMvc (contrat serveur couvert). **208 verts, 3 skip.** |
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
