# ClinicApp — Refonte rôles (OWNER/ADMIN), confidentialité & UX top-1%

> **But de ce fichier** : tracker, **entre sessions**, la refonte transverse décidée le **2026-06-25**.
> Trois chantiers liés : (1) séparer **OWNER** (propriétaire/business) de **ADMIN** (technique/système),
> (2) appliquer le cloisonnement vie privée patient (PHI) + détails financiers, (3) repositionner le
> client desktop et hisser l'**UX/UI au niveau top-1% des EHR**.
>
> **Comment l'utiliser** : `[ ]` todo · `[~]` en cours · `[x]` fait. Un chantier (WS) ≈ une session ;
> `mvnd compile` pour vérifier ; coche + ajoute une note de résultat au **journal** en bas.
> Référencé depuis `docs/IMPROVEMENT-BACKLOG.md` (entrée P6). Source de vérité de cette refonte.
>
> **État de référence au 2026-06-25** : 14 modules cliniques faits, backlog P1→P5 largement traité,
> dernière migration **V24**, RBAC web piloté par `RoleProfile`/`Module` (source unique).

---

## 1. Motivation (demandes utilisateur, verbatim résumé)

1. **Navigation « sans jonglage »** : depuis une sous-vue (ex. *pharmacien → nouvelle dispensation*),
   l'utilisateur n'a aucun moyen de revenir au tableau de bord ; il doit recliquer le module
   « Pharmacie » pour repartir. Tout ce dont l'utilisateur a besoin doit être atteignable depuis le
   chrome (sidebar / fil d'Ariane), sans cul-de-sac.
2. **Fuite financière vers le médecin** : connecté en médecin sur le web, le « tableau de bord »
   affiche des montants (« encaissé aujourd'hui », « encaissé ce mois ») — réservés au propriétaire.
3. **Desktop sans cloisonnement** : en se connectant en *radiologue* ou *admin* sur le desktop, on voit
   exactement la même chose que `dr.martin` (médecin). Personne ne doit voir l'interface d'un autre.
4. **Créer le rôle OWNER**, le détacher d'ADMIN :
   - **ADMIN = l'exploitant technique (moi)** : accès, journaux (bons/mauvais), stats & configuration
     **système** ; **aucun accès au confidentiel** (données de santé patient).
   - **OWNER = le propriétaire de la clinique** : pilote tout comme un vrai chef d'entreprise.
5. **Tout le monde sur le web** ; les **détails financiers** sont réservés au OWNER, **seul** à utiliser
   le **desktop** (et le web au besoin).
6. **UX/UI top-1% des EHR** : le skill `ui-ux-pro-max` vient d'être installé ; comment le faire adopter.

---

## 2. État actuel (constats vérifiés dans le code — 2026-06-25)

| Sujet | Fichier(s) | Constat |
|---|---|---|
| Enum des rôles | `model/Role.java` | `SUPER_ADMIN, ADMIN, MEDECIN, INFIRMIER, SECRETAIRE, PHARMACIEN, LABORANTIN, CAISSIER, PATIENT`. **Pas de `OWNER`.** |
| RBAC web (source unique) | `config/RoleProfile.java` + `config/Module.java` | Pilote sidebar + homepage + types de notifs par rôle. **`ADMIN` reçoit TOUS les modules** (`EnumSet.complementOf(ADMIN_CLINICS)`) → voit clinique **et** finances. |
| Fuite financière médecin | `controller/web/ReportWebController.java:47` | `if (hasAnyRole(auth,"ADMIN","MEDECIN"))` → sert `reports/dashboard` construit avec `reportService.adminDashboard()` qui porte `revenueMonth`, encaissé du jour/mois. **C'est la source du point 2.** |
| Dashboard médecin dédié | `controller/web/AuthWebController.java:39` + `dashboard-doctor.html` + `dto/DoctorDashboardDto.java` | **Sain** : aucun champ financier (journée du médecin : RDV semaine, consults du jour, labo à valider). La fuite vient bien de `/reports`, pas de `/dashboard`. |
| Desktop — gating rôle | `desktop/.../model/AuthState.java:15` | `DESKTOP_ROLES = {MEDECIN, INFIRMIER, ADMIN}`. Login refusé hors de ce set (message « poste réservé… »). |
| Desktop — pas de cloisonnement | `desktop/.../controller/DashboardController.java` + `dashboard.fxml` | **Tous** les rôles desktop chargent le **même** `dashboard.fxml` avec la **même** nav. Aucune différenciation par rôle. **Source du point 3.** |
| Dernière migration Flyway | `db/migration/V24__billing_accumulation.sql` | Prochaine = **V25**. |

---

## 3. Décisions verrouillées (2026-06-25)

> Issues de l'échange de clarification. Ne pas re-litiguer sans raison nouvelle.

- **D1 — Desktop = OWNER uniquement.** Tout le personnel clinique (médecin, infirmier, etc.) passe
  sur le **web**. Le desktop devient le **cockpit du propriétaire** (pilotage business). Le point 3
  (cloisonnement desktop médecin/radiologue) **disparaît** : il n'y a plus qu'un rôle sur le desktop.
- **D2 — OWNER = business uniquement, SANS PHI.** Le OWNER voit finances, activité, stats agrégées,
  personnel/RH, occupation, stocks — **mais PAS** le contenu médical confidentiel (diagnostics,
  résultats labo/imagerie, dossiers patient nominatifs). Même mur de confidentialité que l'ADMIN.
- **D3 — Répartition des catalogues** : **OWNER** gère ce qui touche à l'argent/business
  (*Actes & tarifs*, *Assureurs*, *Départements*) ; **ADMIN** garde le technique
  (*Utilisateurs*, *Configuration système*, *Audit*, *CIM-10*, *Catalogue d'analyses*).
- **Implicite D2+D4** : **ni ADMIN ni OWNER ne voient le PHI**. Le contenu clinique nominatif reste
  aux **soignants** (MEDECIN, INFIRMIER) et au support clinique (LABORANTIN/labo, PHARMACIEN/pharmacie,
  SECRETAIRE/démographie+RDV).

---

## 4. Modèle de rôles cible

| Rôle | Plateforme | PHI (clinique nominatif) | Finances (BI/agrégat) | Périmètre |
|---|---|---|---|---|
| `SUPER_ADMIN` | web | non | non | registre des cliniques (multi-tenant) — inchangé |
| **`OWNER`** *(nouveau)* | **desktop** (principal) + web | **NON** | **OUI (complet)** | cockpit business : tableau de bord financier, facturation (vue pilotage), catalogues *actes & tarifs / assureurs / départements*, rapports (financier, activité agrégée, impayés), occupation (taux), valeur de stock & alertes, personnel |
| `ADMIN` | web | **NON** | **NON** | technique : utilisateurs & rôles, configuration système, **journal d'audit**, CIM-10, catalogue d'analyses, stats **système/usage** (connexions, audit), notifications système |
| `MEDECIN` | web | **oui** | non | clinique complet : patients, consultations, RDV, labo, imagerie, maternité, hospitalisation, **rapports cliniques** (activité, épidémiologie) — **plus de dashboard financier** |
| `INFIRMIER` | web | oui | non | sous-ensemble soins : patients, RDV, consultations, maternité, hospitalisation |
| `SECRETAIRE` | web | démographie + RDV | non (opérationnel facturation seulement) | accueil : patients, RDV, facturation opérationnelle, impayés |
| `PHARMACIEN` | web | lié ordonnance | non | pharmacie |
| `LABORANTIN` | web | labo | non | laboratoire |
| `CAISSIER` | web | minimal | **opérationnel** (encaisser, rapport de caisse du jour) — **pas** la BI stratégique | facturation/caisse |
| `PATIENT` | web (portail) | le sien | le sien | portail — inchangé |

### 4.1 Frontière financière (clé pour le point 2 et 5)

- **Opérationnel** (créer facture, encaisser, voir solde, **rapport de caisse du jour** pour
  réconciliation) → reste à **CAISSIER** / **SECRETAIRE**.
- **BI / pilotage stratégique** (revenu mensuel, encaissé jour/mois agrégé, impayés agrégés, marges,
  `adminDashboard()`) → **OWNER** (et ADMIN n'y a **pas** accès — c'est du business, pas du technique).
- **MEDECIN** : zéro chiffre financier.

### 4.2 Frontière PHI (clé pour le point 4)

- **ADMIN** & **OWNER** : aucun écran affichant diagnostic, résultat labo/imagerie, motif clinique,
  dossier nominatif. Les **stats agrégées/dé-identifiées** (comptes, taux, revenu) leur sont permises.
- Reste à trancher (voir §7) : épidémiologie agrégée (top pathologies, pyramide des âges) et
  **noms des occupants** dans le plan des lits — sont-ils « business » (OK owner) ou « PHI » ?

---

## 5. Chantiers (workstreams)

> Ordre conseillé : **WS1 → WS2 → WS3** (le socle rôles, indissociable), puis **WS4** (desktop),
> puis **WS5** (UX, le plus gros et le plus itératif). `mvnd compile` après chaque.

### WS1 — Introduire le rôle `OWNER` ✅ (2026-06-25)
- [x] `model/Role.java` : ajouter `OWNER` (entre SUPER_ADMIN et ADMIN). Assignable via `/admin/users`
      (l'ADMIN technique gère les accès) car `UserService.assignableRoles()` = tout sauf SUPER_ADMIN.
- [x] `config/RoleProfile.java` : entrée `OWNER` (homepage `/reports` = cockpit financier ;
      modules = `NOTIFICATIONS, BILLING, REPORTS, ADMIN_DEPTS, ADMIN_INSURANCE, ADMIN_ACTS` ;
      `notificationTypes` = `FACTURE_IMPAYEE`, `STOCK_ALERTE`, `SYSTEM`).
- [x] `config/Module.java` : **inchangé** — `ADMIN_ACTS/INSURANCE/DEPTS` existent déjà comme entrées
      distinctes ; il a suffi de les lister côté OWNER. Elles restent en section `ADMIN` de la sidebar
      (une vraie section « PILOTAGE » est une décision UX → WS5).
- [x] **Migration `V25` non créée** (déviation assumée) : aucun changement de schéma (`role` = chaîne libre),
      et tous les comptes de démo sont seedés via `DataInitializer` (`@Profile("!prod")`), jamais par SQL —
      un INSERT en migration tournerait aussi en prod (compte démo indésirable). V25 reste libre pour le
      prochain vrai changement de schéma. En prod, le OWNER est créé par l'ADMIN via `/admin/users`.
- [x] `config/DataInitializer.java` (`@Profile("!prod")`) : seed `owner / owner123` (OWNER, clinic1).
- [x] `@PreAuthorize` BI financier → `OWNER` (voir WS2/WS3 pour le détail du balayage).
- **Critère d'acceptation** : ✅ `owner/owner123` → atterrit sur `/reports` (cockpit financier) ; sidebar =
  finances + catalogues business ; aucun module clinique (PATIENTS absent → recherche globale ne renvoie
  pas de patient ; pages cliniques en 403). Tests : `owner_voit_le_cockpit_financier`,
  `owner_accede_au_rapport_financier` (verts).

### WS2 — Dégrader `ADMIN` (technique pur, sans PHI ni finances) ✅ (2026-06-25)
- [x] `config/RoleProfile.ADMIN` : set **explicite** = `{NOTIFICATIONS, ADMIN_USERS, ADMIN_LAB_TESTS,
      ADMIN_ICD10, ADMIN_AUDIT, ADMIN_CONFIG}` (fin du `complementOf`). `notificationTypes` réduit à `SYSTEM`.
- [x] Homepage ADMIN → `/admin/users` (était `/dashboard`).
- [x] Balayage `@PreAuthorize` (web **et** API) :
      - **Clinique/PHI → ADMIN retiré** : Patients, Appointments, Consultations, Prescriptions, Lab,
        Radiology, Maternity, Hospitalization (clinique), Pharmacy, Scribe. Soft-delete patient → `MEDECIN`.
      - **Finances → ADMIN remplacé par OWNER** : Billing (lecture `OWNER/CAISSIER/SECRETAIRE`, création/maj
        `CAISSIER/SECRETAIRE`, encaissement `CAISSIER`, annulation `OWNER`), Reports financiers, chambres
        d'hospitalisation (tarifs → `OWNER`).
      - **Catalogues business (D3) → OWNER** : `/admin/acts`, `/admin/insurance`, `/admin/departments`.
      - **Reste ADMIN (technique, D3)** : `/admin/users`, `/admin/config`, `/admin/audit`, `/admin/icd10`,
        `/admin/lab-tests`, `POST /api/auth/register`, `POST /api/notifications/test-sms`.
- [x] `SUPER_ADMIN` (registre cliniques) et `ProdDataInitializer` (seed un seul ADMIN) inchangés/cohérents.
- **Critère d'acceptation** : ✅ `/patients`, `/billing`, `/reports/financial` → **403** pour ADMIN ;
  `/admin/audit`, `/admin/users`, `/admin/config` → **200**. Tests verts : `admin_refuse_sur_les_patients_phi`,
  `admin_refuse_sur_la_facturation`, `admin_refuse_sur_le_rapport_financier`,
  `admin_sur_dashboard_redirige_vers_sa_page_technique`, `admin_accede_au_journal_audit`.
- **Reste à faire (hors socle)** : le gating temps réel STOMP (`WorklistChannels`) donne encore TOUTES les
  worklists cliniques à l'ADMIN (`WorklistAuthorizationTest.l_admin_voit_toutes_les_worklists`). C'est un
  canal PHI — à re-gater (le retirer d'ADMIN) lors d'un passage realtime/WS4 ; laissé tel quel ici car hors
  périmètre WS1–3 et pour ne pas casser la couche realtime.

### WS3 — Colmater la fuite financière (point 2) ✅ (2026-06-25)
- [x] `ReportWebController.dashboard()` : la branche `adminDashboard()` (revenu, encaissé jour/mois) est
      désormais **`OWNER` seul** (plus ADMIN ni MEDECIN). MEDECIN sur `/reports` → **redirect `/reports/activity`**.
- [x] Méthodes `@PreAuthorize` re-gatées (web `ReportWebController` + API `ReportApiController`) :
      financier/caisse/mensuel/impayés/stock/`dashboard/admin` → **OWNER** (+ CAISSIER/SECRETAIRE pour
      l'opérationnel) ; activité/épidémio → **MEDECIN + OWNER** (agrégé/dé-identifié, défauts R1) ;
      `dashboard/doctor` → MEDECIN seul. ADMIN retiré de tout le financier.
- [x] `dashboard-doctor.html` déjà sain (zéro champ financier) ; côté MEDECIN le module REPORTS ne mène
      qu'aux rapports cliniques (le hub redirige vers `/reports/activity`).
- **Critère d'acceptation** : ✅ médecin → aucun montant encaissé visible ; `/reports/financial` 403 médecin,
  200 owner ; `/reports` (hub) redirige le médecin vers `/reports/activity`. Tests :
  `medecin_refuse_sur_rapport_financier`, `medecin_redirige_du_hub_rapports_vers_activite`,
  `owner_accede_au_rapport_financier`.

### WS4 — Desktop = cockpit OWNER (points 3 & 5) ✅ (2026-06-25)
- [x] `desktop/.../model/AuthState.java` : `DESKTOP_ROLES = {OWNER}` ; `roleLabel` ajoute OWNER →
      « Propriétaire ». `LoginController` : message de refus « poste réservé au propriétaire ; votre profil
      s'utilise depuis l'application web ».
- [x] `dashboard.fxml` + `DashboardController` refondus en **cockpit business** : revenus (jour/mois +
      variation), reste à recouvrer, consultations du mois (agrégat), occupation des lits, alertes stock,
      ventilation des encaissements par mode — servis par `/api/reports/dashboard/admin` +
      `/api/reports/monthly-financial` (toutes deux OWNER). **Aucun appel PHI** (les anciens
      `/api/patients|appointments|consultations` du dashboard ont disparu).
- [x] Écrans cliniques desktop (**R5 = gel inatteignable**, moindre effort) : patients/RDV/consultations/
      dossier/demande d'examen restent dans le code mais ne sont **plus liés** depuis la nav du cockpit ;
      ils 403eraient de toute façon pour OWNER. La sidebar OWNER = Tableau de bord · Actualiser · Déconnexion.
- [x] `RealtimeClient` : `topicForRole`→`topicsForRole` (liste, multi-SUBSCRIBE). OWNER suit les canaux
      **business** `/topic/worklist/pharmacy` (stock) + `/topic/billing/queue` (caisse) ; plus de worklist soin.
- [x] **Backend realtime re-gaté** (l'item « hors socle » de WS2) : `WebSocketSecurityConfig` — ADMIN retiré
      de TOUTES les worklists ; LAB→LABORANTIN, RADIOLOGY→MEDECIN, PHARMACY→PHARMACIEN+**OWNER**,
      BILLING_QUEUE→CAISSIER+**OWNER**. Test `WorklistAuthorizationTest` mis à jour
      (`l_admin_ne_voit_aucune_worklist_clinique`, `l_owner_suit_les_canaux_business`).
- **Critère d'acceptation** : ✅ seul `owner` se connecte au desktop (tout autre rôle refusé proprement avec
  message web) ; cockpit 100 % business, zéro écran clinique nominatif. `mvnd compile` backend+desktop OK,
  tests 36/36 verts.
- **NB / reste possible** : la mise en forme monétaire desktop affiche « FCFA » en dur (déploiement Niger/XOF)
  plutôt que via `clinic_config.currency` — à brancher sur la config si multi-devise un jour.

### WS5 — Navigation « sans jonglage » + UX top-1% (points 1 & 6) — 🚧 en cours
> Cadrage retenu (2026-06-25) : **incrémental 2 couches**. Couche 1 = socle chrome partagé (fort levier,
> faible risque) ; Couche 2 = polish esthétique par lot prioritaire avec le skill `ui-ux-pro-max`.

**Couche 1 — socle chrome partagé**
- [x] **Fil d'Ariane** réutilisable, **auto-dérivé** dans `layouts/base.html` (Accueil / Module / Page)
      via `GlobalModelAdvice.currentModule` ; segments cliquables → retour module + accueil ≤ 1 clic ;
      masqué sur la home du rôle ; i18n FR/EN/AR + `aria-current` ; `.breadcrumb` dans app.css.
      Testé (`BreadcrumbNavigationTest`). **→ satisfait le critère d'acceptation principal.**
- [x] **Sous-navigation de module** — registre central **`config/ModuleTabs`** (1 ligne/module, philosophie
      `Module`/`RoleProfile`) ; `GlobalModelAdvice` expose `moduleTabs` + `activeTabUrl` (onglet actif =
      plus long préfixe d'URL) ; `base.html` rend la barre `.module-tabs` sous le fil d'Ariane ;
      i18n FR/EN/AR. **1er module câblé : Pharmacie** (Tableau de bord · Médicaments · Stock · Dispensations ·
      File ordonnances). Testé (`BreadcrumbNavigationTest`). Autres modules = ajouter une entrée au registre.
- [x] Bouton **Retour** généralisé : fragment réutilisable dans `templates/fragments/ui.html` —
      `back(href)` (retour in-app vers une URL connue) + `backHistory` (`history.back()` pour les pages
      d'impression autonomes). Appliqué à 7 templates : `patients/detail`, `lab/result-entry`,
      `radiology/report-form` (in-app) + `prescriptions/print`, `lab/bulletin`, `radiology/bulletin`,
      `billing/invoices/receipt` (toolbars d'impression). 2 smoke tests. Les affordances pharmacie existantes
      (« Annuler » → liste) restent, désormais doublées par fil d'Ariane + onglets.

**Couche 2 — polish UX par lot (skill `ui-ux-pro-max`)**
- [x] **Lot 1 — Pharmacie** : skill `ui-ux-pro-max` consulté (style « Data-Dense Dashboard », palette
      projet conservée). Sous-nav onglets + fil d'Ariane sur toutes les pages pharmacie ; dashboard quick-actions
      recentrées sur les vraies actions ; chiffres en `tabular-nums` ; nettoyage du `sec:authorize` ADMIN mort.
- [x] **Lot 2 — Cockpit OWNER** (`/reports`) : **`ModuleTabs` rendu role-aware** (chaque onglet gaté par rôle
      → un module hétérogène comme Rapports n'expose jamais une destination en 403 ; règle UX « empty-nav-state »).
      Onglets Rapports (Cockpit/Bilan financier/Activité/Épidémio/Impayés) gatés comme les `@PreAuthorize` WS3 →
      la rangée de boutons dupliquée du dashboard est supprimée. `reports/dashboard.html` repensé en hiérarchie :
      **carte héros** « Encaissé ce mois » (valeur 40px, dégradé vert, tendance vs mois préc.), puis sections
      groupées (Revenus / Activité &amp; exploitation / Alertes stock avec bords sémantiques amber/red), KPI en
      `tabular-nums`. `mvnd test` 143/143.
- [x] **Lot 3 — Dashboard médecin** (`dashboard-doctor.html`) : en-tête de journée personnalisé (« Bonjour,
      Dr X » + date du jour via `#temporals.createToday()`), KPI avec icônes (`.stat-card-header`/`.stat-card-icon`)
      et **accent ambre sur « labo à valider » quand > 0** (l'action saute aux yeux), rythme vertical homogénéisé
      (suppression des `margin-top` inline → cadence `.page-content`). `mvnd test` 143/143.
- [x] **Lot 4 — Parcours patient** : `patients/list.html` revu (déjà sain → conservé). `patients/detail.html`
      (le dossier) : onglets refondus en **deep-linkables** (`#hash` dans l'URL → lien partageable + l'onglet
      **survit au rechargement**, ex. retour après « Modifier ») et **accessibles** (`role="tablist"`/`tab`/
      `tabpanel`, `aria-selected`) ; JS robuste sans `event` global ni `onclick` inline. `mvnd test` 143/143.
- [x] **Lot 5 — Consultations** : `consultations/detail.html` resserré — les **4 panneaux quasi vides**
      (Labo/Imagerie/Hospitalisation/Facturation, un bouton + une phrase chacun) fusionnés en **un seul bloc
      « Actions cliniques »** (groupe de boutons + une note), rythme vertical homogénéisé (suppression des
      `margin-top` inline), « Retour à la liste » retiré (le fil d'Ariane le remplace) en gardant le lien
      latéral « Dossier patient ». Liste consultations conservée (saine). `mvnd test` 144/144.
- [x] **Lot 6 — Facturation** : entrée `ModuleTabs` BILLING (Tableau de bord · File caisse · Factures,
      non gatée — accès de classe partagé) → les rangées de boutons de nav des en-têtes
      (`dashboard.html`, `invoices/list.html`) supprimées. `billing/dashboard.html` repensé en hiérarchie
      (carte héros « Encaissé aujourd'hui » + reste à recouvrer, sections Cumul / Factures par statut avec
      accent ambre sur « En attente », rythme homogénéisé). `mvnd test` 144/144.

> **Couche 2 — liste prioritaire bouclée** (6 lots : Pharmacie · Cockpit OWNER · Dashboard médecin ·
> Parcours patient · Consultations · Facturation). Les modules restants (Labo, Imagerie, Maternité,
> Hospitalisation, Admin) bénéficient déjà du chrome partagé (fil d'Ariane + tabs si registre) ; un polish
> esthétique fin par module peut suivre au besoin, même méthode.

- [x] **Polish restant — catalogues admin (2026-06-26)** : cohérence monétaire des catalogues business
      (Actes & tarifs, Analyses) — tarifs bruts `formatDecimal(..,'NONE',..)` → convention app
      `WHITESPACE` + suffixe ` F` (« 5 000 F ») ; colonnes numériques (tarif, délai, prise en charge)
      alignées à droite via nouvelle utilitaire `.text-right` (app.css). Les pages admin sont chacune
      une entrée de sidebar distincte (pas un module à sous-pages) → pas de `ModuleTabs`, le fil
      d'Ariane suffit. 1 smoke test (`AdminCatalogPolishTest`, `@WithUserDetails owner`). `mvnd test` 163/163.
- [x] **Polish restant — modules secondaires (2026-06-26)** : sous-nav `ModuleTabs` câblée pour **Labo**
      (Travail du jour · Demandes), **Imagerie** (Travail du jour · Demandes) et **Hospitalisation**
      (Plan des lits · Séjours · Chambres) ; i18n FR/EN/AR. Les rangées de boutons de cross-nav des en-têtes
      (`lab/{worklist,list}`, `radiology/{worklist,list}`, `hospitalization/{beds,list,rooms}`) supprimées —
      remplacées par les onglets persistants, seules les vraies actions (« + Nouvelle demande », « + Admettre »,
      « + Nouvelle chambre ») restent. Maternité = page unique (`/maternity`) → pas d'onglets. 2 tests de rendu
      ajoutés (sous-nav labo active + 3 onglets hospitalisation). `mvnd test` 162/162.
- [x] **Polish esthétique fin — détails secondaires + Maternité (2026-06-27)** : guidé par
      `docs/UX-GUIDELINES.md`. (a) **Maternité** `record.html` : onglets refondus en patron **deep-linkable
      (`#hash`) + ARIA** (`role=tablist`/`tab`/`tabpanel`, `aria-selected`), **fin du `showTab` à `event`
      global** (alignement sur le dossier patient lot 4) ; back-link « ← Liste des grossesses » retiré (fil
      d'Ariane), lien « Dossier patient » conservé. (b) **Cross-nav redondante d'en-tête/pied retirée** sur
      `hospitalization/detail` (« ← Plan des lits », « Liste des séjours »), `lab/detail` (« ← Travail du
      jour »), `radiology/detail` (idem) — couverte par fil d'Ariane + `ModuleTabs` ; lien latéral « Dossier
      patient » gardé. (c) **Correctifs contraste WCAG AA** : remplacement du gris `#94a3b8` (≈2.8:1, échec AA)
      par `var(--text-500)` sur 4 textes en surimpression (`maternity/form` aide DPA, `pharmacy/stock` unité,
      `pharmacy/dispensations/{detail,list}`) + 2 légendes `#475569`→token (`lab/detail`, `radiology/detail`).
      (d) `alt` d'image radiologique descriptif (légende sinon « Image radiologique »). 4 tests de rendu
      ajoutés (`PageRenderSmokeTest` : tablist maternité + détails séjour/labo/imagerie). `mvnd test` 167/167.
- **Critère d'acceptation (atteint pour la nav)** : depuis n'importe quelle sous-vue, retour au tableau de
  bord du module **et** à l'accueil en ≤ 1 clic, sans recliquer la sidebar. ✅ (fil d'Ariane).

---

## 6. Adopter le skill `ui-ux-pro-max` — ✅ FAIT (2026-06-27)

**Constat initial 2026-06-25** : le skill était introuvable/non surfacé → non invocable.

**Résolu (2026-06-27)** : le skill est désormais surfacé (`~/.claude/skills/ui-ux-pro-max`) et a été
consulté pour « healthcare clinic management dashboard / data-dense ». **Sa reco valide notre système
existant à l'identique** (style « Data-Dense Dashboard », primaire `#2563EB`, accent vert `#059669`,
statuts green/amber/red, WCAG AA) — aucun pivot, on confirme le cap.

**Option durable retenue (la n°3)** : ses principes directeurs sont distillés dans **`docs/UX-GUIDELINES.md`**
(accessibilité → interaction → layout → typo/couleur → tables denses → formulaires → iconographie → nav →
impression + checklist de livraison + pièges Thymeleaf), **ancrés sur les tokens/classes réels d'`app.css`**.
Renvoi ajouté depuis `CLAUDE.md` (§ Design system) → en contexte à **chaque** session sans dépendre du
chargement du skill. Le skill reste invocable à la demande pour un lot de polish ciblé.

---

## 7. Questions ouvertes / raffinements à trancher

- **R1 — Épidémiologie agrégée** pour OWNER : top pathologies + pyramide des âges sont-ils « business BI »
  (autorisé) ou « PHI agrégé » (interdit) ? *Défaut proposé* : autorisé si purement agrégé/dé-identifié.
- **R2 — Plan des lits / occupation** pour OWNER : afficher **taux & comptes** oui ; **noms des occupants**
  = PHI → masquer pour OWNER. *Défaut proposé* : owner voit l'occupation anonymisée.
- **R3 — Rapport de caisse** : CAISSIER garde la **caisse du jour** (réconciliation) ; OWNER a la vue
  mensuelle/stratégique. Confirmer cette ligne de partage.
- **R4 — Personnel/RH** pour OWNER : périmètre exact (liste du staff, présence ?) — pas encore de module RH.
- **R5 — Écrans cliniques desktop** : suppression franche vs gel inatteignable (effort vs propreté).
- **R6 — Catalogue d'analyses** côté ADMIN bien que `lab_test_catalog` porte un **prix** (chevauchement
  business). Confirmé ADMIN par D3 ; signaler si le tarif doit migrer vers OWNER.

---

## 8. Journal de progression

| Date | Chantier | Résultat |
|---|---|---|
| 2026-06-27 | WS5 icônes nav (#6) | Emojis de la sidebar → **sprite SVG inline** (`fragments/icons.html`, 19 icônes Lucide, trait 2px). `Module.icon` porte désormais un id (`calendar`, `pill`…) rendu via `<svg class="nav-icon"><use href="#ic-…">` ; `.nav-icon` hérite `currentColor` (blanc à l'actif). Sprite inclus 1×/page dans `base.html`. **Vérifié visuellement** (capture headless Edge des 19 icônes sur fond sidebar — toutes nettes + état actif blanc/bleu). Smoke test renforcé (symbole `id="ic-user"` + ref `href="#ic-user"` concordent). `mvnd test` 167/167. |
| 2026-06-27 | WS5 filtre listes (#5) | `maternity/list` était le seul filtre à ne pas utiliser la classe partagée `.search-bar` (flex inline ad hoc + form-group/label) → aligné sur le patron des 9 autres listes (select nu « — Tous statuts — », auto-submit). Bonus dédup : `.search-bar` portant déjà `align-items:center`, le `style="align-items:center;"` redondant retiré des 8 listes sœurs (consultations, billing, labo, imagerie, hospit, RDV jour/semaine, pharmacie). Zéro changement visuel. `mvnd test` 167/167. |
| 2026-06-27 | WS5 sweep espacement (#4) | Utilitaires `.panel-body` (corps de panneau, padding 12/16) + `.mt-16` (panneaux empilés / pied d'actions) dans app.css → ~38 `style="padding:12px 16px"`/`"margin-top:16px"` inline ad hoc retirés sur 18 templates (détails clinique, dossiers, rapports, billing, portal). Les styles inline mixtes (flex/align) gardent leur part de mise en page, seule l'espacement passe en classe. Reste `maternity/list` filtre → traité en #5. `mvnd test` 167/167. |
| 2026-06-27 | WS5 sweep cohérence | **#1+#2+#3** de la liste cosmétique. (#2) utilitaires `.text-muted` / `.text-danger` (+ token `--red-strong`) dans app.css → ~25 `style="color:…"` répétés convertis en classes sur 14 templates (labo/imagerie/pharmacie/billing/reports/admin/portal/maternité). (#1) tout hex brut muet/danger en vue tokenisé (plus aucun `#64748b`/`#475569`/`#b91c1c` hors pages d'impression autonomes). (#3) JS d'onglets mutualisé dans `static/js/ui.js` (chargé via base.html, no-op sans onglets) → IIFE inline retirées de `patients/detail` + `maternity/record`. `mvnd test` 167/167. |
| 2026-06-27 | WS5 polish fin | **Détails secondaires + Maternité** (guidé par `docs/UX-GUIDELINES.md`). Maternité `record` : onglets deep-linkables + ARIA (fin du `event` global). Cross-nav d'en-tête/pied retirée sur `hospitalization/detail`, `lab/detail`, `radiology/detail` (couverte par fil d'Ariane + tabs ; lien « Dossier patient » gardé). Contraste WCAG AA : `#94a3b8`→`var(--text-500)` sur 4 textes (échec AA ≈2.8:1) + 2 légendes `#475569`→token. `alt` image radio descriptif. 4 tests de rendu ajoutés. `mvnd test` 167/167. |
| 2026-06-27 | WS5 §6 + Couche 1 | **Clôture WS5.** (a) Bouton **Retour** : case `[~]`→`[x]` (fragment `fragments/ui :: back/backHistory` déjà livré sur 7 templates, 2 smoke tests — checkbox périmée). (b) **`docs/UX-GUIDELINES.md` créé** : principes UX durables distillés du skill `ui-ux-pro-max` (consulté → valide notre système à l'identique : Data-Dense Dashboard, `#2563EB`/`#059669`, WCAG AA), ancrés sur les tokens/classes réels d'`app.css` ; renvoi ajouté dans `CLAUDE.md`. §6 passé en ✅. |
| 2026-06-26 | WS5 c2 polish | **Catalogues admin.** Cohérence monétaire (Actes/Analyses) : tarifs bruts → convention app `WHITESPACE` + ` F` ; colonnes numériques (tarif/délai/prise en charge) alignées à droite via utilitaire `.text-right` (app.css). Pages admin = entrées sidebar distinctes → pas de `ModuleTabs` (fil d'Ariane suffit). 1 smoke test. `mvnd test` 163/163. |
| 2026-06-26 | WS5 c2 polish | **Modules secondaires.** Sous-nav `ModuleTabs` câblée pour Labo (Travail du jour/Demandes), Imagerie (idem) et Hospitalisation (Plan des lits/Séjours/Chambres) ; i18n FR/EN/AR. Cross-nav des en-têtes retirée (remplacée par les onglets), seules les vraies actions conservées. Maternité = page unique → pas d'onglets. 2 tests de rendu ajoutés. `mvnd test` 162/162. |
| 2026-06-25 | Doc | Création de ce plan ; constats code vérifiés ; décisions D1–D3 verrouillées ; rien d'implémenté encore. |
| 2026-06-25 | WS5 c2 lot6 | **Facturation (clôt la liste prioritaire C2).** `ModuleTabs` BILLING (Tableau de bord/File caisse/Factures) → rangées de boutons de nav supprimées des en-têtes dashboard+liste. `billing/dashboard.html` repensé (carte héros « Encaissé aujourd'hui » + reste à recouvrer, sections Cumul/Statuts, accent ambre). i18n FR/EN/AR. Test billing renforcé (tabs). `mvnd test` 144/144. **Couche 2 priorité bouclée : 6 lots faits.** |
| 2026-06-25 | WS5 c2 lot5 | **Consultations.** `consultations/detail.html` : 4 panneaux quasi vides (labo/imagerie/hospit/facturation) fusionnés en un bloc « Actions cliniques » (groupe de boutons), rythme homogénéisé (margin-top inline retirés), « Retour à la liste » remplacé par le fil d'Ariane (lien « Dossier patient » conservé). Smoke test ajouté (`consultation_detail_rend_actions_cliniques`). `mvnd test` 144/144. |
| 2026-06-25 | WS5 c2 lot4 | **Parcours patient.** `patients/detail.html` : onglets du dossier refondus deep-linkables (#hash → partageable + survit au rechargement/retour) + ARIA (tablist/tab/tabpanel/aria-selected), JS robuste (plus de `event` global ni `onclick` inline). Liste patients revue et conservée (déjà saine). Test de rendu renforcé (role=tablist + data-tab). `mvnd test` 143/143. |
| 2026-06-25 | WS5 c2 lot3 | **Dashboard médecin.** `dashboard-doctor.html` : en-tête de journée (Bonjour Dr X + date), KPI avec icônes, accent ambre sur « labo à valider » si > 0, rythme vertical homogénéisé (suppression des margin-top inline). Aucune nouvelle classe CSS (réutilise le vocabulaire `.stat-card-header/.stat-card-icon/.stat-icon-*` + `.stat-card--amber`). Test de rendu renforcé (greeting). `mvnd test` 143/143. |
| 2026-06-25 | WS5 c2 lot2 | **Cockpit OWNER.** `ModuleTabs` rendu role-aware (filtre par rôle dans `GlobalModelAdvice`) → onglets Rapports gatés (Cockpit/Financier/Activité/Épidémio/Impayés) comme les `@PreAuthorize` WS3. `reports/dashboard.html` repensé : carte héros « Encaissé ce mois » + sections groupées (Revenus/Exploitation/Alertes, bords sémantiques) ; rangée de boutons supprimée (remplacée par les onglets). CSS étendu (`.cockpit-hero`, `.section-title`, `.stat-card--green/amber/red`, tabular-nums sur `.stat-card-value`). i18n FR/EN/AR. Tests role-aware ajoutés (owner voit tous les onglets, médecin ne voit pas Financier). `mvnd test` 143/143. |
| 2026-06-25 | WS5 c1b+c2 | **Sous-nav de module + polish Pharmacie.** Registre `ModuleTabs` (réutilisable, 1 ligne/module) → barre d'onglets persistante rendue par `base.html` sous le fil d'Ariane (actif = plus long préfixe), i18n FR/EN/AR ; Pharmacie câblée. Polish pharmacie (skill `ui-ux-pro-max` consulté) : quick-actions recentrées, `tabular-nums`, nettoyage `sec:authorize` ADMIN. **Correctif** : la suite complète a révélé 23 tests cassés par le cloisonnement WS1-4 (fixtures utilisant ADMIN comme acteur omnipotent) → re-câblés sur les bons rôles (medecin/caissier/owner/pharmacien). `mvnd test` 141/141 verts. |
| 2026-06-25 | WS5 c1a | **Fil d'Ariane partagé.** Cadrage incrémental 2 couches retenu. Couche 1a livrée : breadcrumb auto-dérivé (`GlobalModelAdvice.currentModule` + `base.html` + `.breadcrumb` app.css + i18n FR/EN/AR), masqué sur la home, testé (`BreadcrumbNavigationTest`). Satisfait le critère nav « ≤ 1 clic ». Reste : sous-nav module + bouton Retour (posés en Couche 2), puis polish esthétique par lot avec `ui-ux-pro-max`. |
| 2026-06-25 | WS4 | **Desktop = cockpit OWNER.** `AuthState.DESKTOP_ROLES={OWNER}` + label/message refus ; `dashboard.fxml`/`DashboardController` refondus en cockpit business (revenus, recouvrement, occupation, stock, modes de paiement via `/api/reports/dashboard/admin`+`/monthly-financial`, zéro appel PHI) ; écrans cliniques gelés/inatteignables (R5) ; `RealtimeClient` → multi-topic business (pharmacy+billing). Backend realtime re-gaté : ADMIN retiré de toutes les worklists, OWNER ajouté à pharmacy+billing (`WebSocketSecurityConfig`). `mvnd compile` backend+desktop OK ; tests 36/36 (Worklist 4 dont OWNER, SecurityMatrix 28, ApiError 4). Reste : **WS5** (nav sans jonglage + UX top-1%). |
| 2026-06-25 | WS1+WS2+WS3 | **Socle rôles livré.** OWNER ajouté (`Role`, `RoleProfile` homepage `/reports`, seed `owner/owner123`). ADMIN dégradé en set technique explicite (homepage `/admin/users`, notifs `SYSTEM`). Balayage `@PreAuthorize` web+API : clinique→ADMIN retiré, finances→OWNER, catalogues business (actes/assureurs/départements + tarifs chambres)→OWNER ; technique (users/config/audit/icd10/lab-tests/register/test-sms) reste ADMIN. Fuite financière colmatée : cockpit `adminDashboard()`→OWNER seul, MEDECIN redirigé `/reports/activity`. `mvnd compile` OK ; tests RBAC 39/39 verts (SecurityMatrix 28 + nouvelles assertions OWNER/ADMIN, ApiError 4, Icd10 4, Worklist 3). Déviation : pas de migration V25 (owner seedé via DataInitializer, aucun changement de schéma). Reste hors socle : gating realtime STOMP donne encore les worklists cliniques à l'ADMIN (à re-gater en WS4). |
