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

### WS1 — Introduire le rôle `OWNER`
- [ ] `model/Role.java` : ajouter `OWNER`.
- [ ] `config/RoleProfile.java` : entrée `OWNER` (homepage = cockpit financier ; modules = voir §4 ;
      `notificationTypes` = `FACTURE_IMPAYEE`, `STOCK_ALERTE`, `SYSTEM`).
- [ ] `config/Module.java` : si besoin, scinder/nommer les entrées catalogues pour le gating D3
      (les `ADMIN_ACTS`/`ADMIN_INSURANCE`/`ADMIN_DEPTS` basculent côté OWNER ; restent physiquement
      en section ADMIN ou créer une section « PILOTAGE » — décision UX, voir WS5).
- [ ] **Migration `V25__owner_role.sql`** : créer le compte propriétaire de démo (profil `!prod`)
      + documenter qu'en prod le OWNER est créé via bootstrap/admin. *(Pas de changement de schéma
      `users` : `role` est déjà une chaîne libre ; juste des données + cohérence enum.)*
- [ ] `config/DataInitializer.java` (`@Profile("!prod")`) : seed `owner / owner123` (rôle OWNER).
- [ ] `config/SecurityConfig.java` + **balayage des `@PreAuthorize`** : partout où une route financière
      BI est `hasRole('ADMIN')`, ajouter/remplacer par `hasRole('OWNER')` (cf. WS2 pour le retrait ADMIN).
- **Critère d'acceptation** : login `owner/owner123` → atterrit sur le cockpit financier ; la sidebar
  montre finances + catalogues business ; aucun écran clinique nominatif accessible (403/absent).

### WS2 — Dégrader `ADMIN` (technique pur, sans PHI ni finances)
- [ ] `config/RoleProfile.ADMIN` : remplacer `EnumSet.complementOf(...)` par un **set explicite**
      = `{ADMIN_USERS, ADMIN_CONFIG, ADMIN_AUDIT, ADMIN_ICD10, ADMIN_LAB_TESTS, NOTIFICATIONS}`
      (+ un éventuel dashboard **système**). Retirer : patients, consultations, billing, pharmacy,
      lab, radiology, maternity, hospitalization, reports financiers, acts, insurance, depts.
- [ ] Homepage ADMIN : `/admin/users` (ou nouveau `/admin/system` synthèse audit+usage), **pas** `/dashboard`.
- [ ] Balayer les contrôleurs/`@PreAuthorize` qui donnaient l'accès clinique/financier à ADMIN et le retirer
      (chaque module : API writes `hasAnyRole(... ,'ADMIN')` → décider OWNER vs rôle métier au cas par cas).
- [ ] Vérifier que `SUPER_ADMIN` et le bootstrap prod (`ProdDataInitializer`) restent cohérents.
- **Critère d'acceptation** : login admin → ne voit que technique ; `/patients`, `/billing`, `/reports/financial`
  → **403** ; `/admin/audit`, `/admin/users`, `/admin/config` → **200**.

### WS3 — Colmater la fuite financière (point 2)
- [ ] `controller/web/ReportWebController.java:47` : retirer `MEDECIN` de la branche `adminDashboard()`.
      MEDECIN qui ouvre `/reports` → **redirect** vers `/reports/activity` (rapport clinique), jamais le
      tableau financier. La branche `adminDashboard()` devient **`OWNER`** (et plus ADMIN).
- [ ] Re-gater toutes les méthodes `@PreAuthorize` de `ReportWebController` / `ReportApiController` :
      financier/caisse → `OWNER` (+ `CAISSIER` pour la caisse du jour opérationnelle) ; activité/épidémio →
      `MEDECIN` ; retirer ADMIN du financier.
- [ ] Vérifier `dashboard-doctor.html` (déjà sain) et la sidebar : le module `REPORTS` côté MEDECIN ne doit
      mener qu'aux rapports cliniques.
- **Critère d'acceptation** : médecin connecté → **aucun** montant encaissé visible nulle part ;
  `/reports/financial` → 403 pour médecin, 200 pour owner.

### WS4 — Desktop = cockpit OWNER (points 3 & 5)
- [ ] `desktop/.../model/AuthState.java` : `DESKTOP_ROLES = {OWNER}` (retirer MEDECIN/INFIRMIER/ADMIN).
      Message de refus mis à jour (« poste réservé au propriétaire ; le personnel utilise l'app web »).
- [ ] Repenser `dashboard.fxml` + `DashboardController` en **cockpit business** : KPIs financiers,
      activité, occupation, stock — **via API agrégées** (`/api/reports/...`), **sans** écran clinique nominatif.
- [ ] Retirer/neutraliser les écrans cliniques desktop (consultations, dossier, demande d'examen) — ou les
      conserver gelés mais inatteignables ; à trancher selon l'effort (voir §7).
- [ ] Adapter `RealtimeClient.startForRole` aux besoins OWNER (alertes stock/impayés plutôt que worklists soin).
- **Critère d'acceptation** : seul `owner` peut se connecter au desktop ; il y voit un cockpit business ;
  tout autre rôle est refusé proprement.

### WS5 — Navigation « sans jonglage » + UX top-1% (points 1 & 6)
- [ ] **Fil d'Ariane** réutilisable dans `layouts/base.html` (ex. `Pharmacie / Nouvelle dispensation`),
      chaque segment cliquable → remonte au niveau module sans recliquer la sidebar.
- [ ] **Sous-navigation de module** : sur chaque landing de module, surfacer les actions clés
      (ex. Pharmacie : Tableau de bord · Médicaments · Stock · Dispensations) en barre d'onglets persistante,
      pour qu'aucune sous-vue ne soit un cul-de-sac.
- [ ] Bouton **Retour** systématique sur les formulaires/détails (cohérent, pas page-par-page).
- [ ] Audit UX transversal (états vides, focus, contraste, responsive, cohérence) — viser les standards
      des meilleurs EHR. Voir **§6 (ui-ux-pro-max)** pour la méthode d'adoption.
- **Critère d'acceptation** : depuis n'importe quelle sous-vue, l'utilisateur revient au tableau de bord
  de son module **et** au tableau de bord principal en ≤ 1 clic, sans recliquer la sidebar.

---

## 6. Adopter le skill `ui-ux-pro-max`

**Constat 2026-06-25** : recherche dans `~/.claude/skills`, `~/.claude/plugins` (+ marketplaces) et dans le
repo → **le skill `ui-ux-pro-max` est introuvable / non détecté**. Claude ne peut invoquer qu'un skill *surfacé*
au démarrage de session ; tel quel, il **ne peut pas** l'utiliser.

**Pour le rendre adoptable (au choix)** :
1. **Vérifier l'emplacement** : un skill personnel doit être à
   `~/.claude/skills/ui-ux-pro-max/SKILL.md` (ou fourni par un plugin installé). Confirmer ce chemin.
2. **Redémarrer la session** Claude Code après installation : les skills sont chargés à l'ouverture.
   Une fois surfacé, il s'invoque via `/ui-ux-pro-max` (ou Claude l'appelle via l'outil Skill).
3. **Le plus fiable / durable** : copier ses principes directeurs dans le repo
   (`docs/UX-GUIDELINES.md`, et un renvoi depuis `CLAUDE.md`) pour qu'**ils soient en contexte à chaque
   session** sans dépendre du chargement d'un skill. *Recommandé* pour WS5.

> **Action à clarifier avec l'utilisateur** : fournir le chemin du skill **ou** coller son contenu, afin de
> l'intégrer en `docs/UX-GUIDELINES.md`.

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
| 2026-06-25 | Doc | Création de ce plan ; constats code vérifiés ; décisions D1–D3 verrouillées ; rien d'implémenté encore. |
