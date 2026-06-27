# Plan de complétion i18n (FR / EN / AR) — le tracker dédié

> **But** : amener TOUT l'app en 3 langues (FR par défaut, EN, AR + RTL). Le socle P3.2 est fait
> (résolveur cookie, `?lang=`, bundles, RTL du chrome, **dropdown de langue partout**). Reste le
> **volume** : ~83 templates, les messages flash des contrôleurs, et les libellés d'enum/statuts.
>
> **Pourquoi ce fichier** (≠ tout mettre dans `IMPROVEMENT-BACKLOG.md`) : une session i18n ne lit
> QUE ce fichier (conventions + check-list + journal) → contexte minimal, zéro re-dérivation, et la
> cohérence est garantie par des conventions **verrouillées**. Référencé depuis le backlog (P3.2) et la
> mémoire [[i18n-foundation]].

---

## 0. État (2026-06-28)
- **Fait** : `base.html` (chrome/sidebar/topbar/breadcrumb/module-tabs), `login.html`, `dashboard*`,
  dropdown de langue (`base` + `login` + `portal`), **socle transverse** (slice 0) et **Patients**
  (slice 1 : `list/form/detail` + dossier + overview Java). **8 / 88 templates** portent des clés `#{...}`.
- **Bundles** : `backend/src/main/resources/messages{,_en,_ar}.properties` (UTF-8, `fallback-to-system-locale=false`).
- **À traduire** : les ~83 autres templates + flash contrôleurs (~150 chaînes : Appointment 18, Hospit 16,
  Consultation 12, Billing 10, …) + libellés d'enum (statuts, méthodes de paiement, priorités, types).

---

## 1. Conventions VERROUILLÉES (ne pas dévier — c'est ce qui garantit la cohérence)

**Nommage des clés** — `module.vue.element`, en minuscules :
- Ex. `patients.list.title`, `patients.form.firstname`, `patients.detail.tab_overview`.
- Transverses dans **`common.*`** : `common.save`, `common.cancel`, `common.edit`, `common.delete`,
  `common.add`, `common.back`, `common.search`, `common.confirm`, `common.actions`, `common.status`,
  `common.date`, `common.name`, `common.none` (« — »), `common.required`, `common.yes`, `common.no`.
  **Toujours réutiliser `common.*`** avant de créer une clé module.
- **Statuts** dans **`status.*`** (un seul namespace, partagé) : `status.EN_ATTENTE`, `status.VALIDE`,
  `status.ADMIS`, `status.PARTIEL`, `status.PAYE`… (clé = la valeur d'enum brute).
- **Méthodes de paiement** dans **`paymethod.*`** : `paymethod.ESPECES`, `paymethod.AMANATA`, …
  (migrer le bean `@paymentMethods.label` pour lire le `MessageSource` — voir §2c).

**Bundles** : ajouter les clés **dans les 3 fichiers** à la fois, **même ordre**, sous un commentaire de
section par module (`# ── Patients ──`). Les 3 fichiers doivent rester **alignés clé-pour-clé** (un script
de diff de clés en fin de session = filet anti-oubli).

**Templates** :
- Texte : `th:text="#{cle}"` · placeholder : `th:placeholder="#{cle}"` · `title`/`aria` : `th:title`/`th:aria-label`.
- **Titre de page (layout)** : `~{layouts/base :: layout(#{module.vue.title}, ~{::content})}` — passer la
  clé `#{}` en argument de fragment **résout AUSSI le piège de l'apostrophe** `''` (le texte vit dans le bundle).
- **Statut/enum affiché** : garder la classe (`badge-${x.status}`) mais le **texte** via
  `th:text="#{${'status.' + x.status}}"` (clé dynamique). Idem `#{${'paymethod.' + p.method}}`.
- **Clé dynamique** : forme `#{${'prefix.' + var}}` (tout dans `${}`), jamais `#{'prefix.' + ${var}}`.

**Flash messages (contrôleurs web)** : injecter `MessageSource`, lire la locale via
`LocaleContextHolder.getLocale()`. Pattern recommandé — un helper unique :
```java
// dans un petit @Component WebI18n (à créer en slice 0) :
String t(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
}
// usage : ra.addFlashAttribute("success", i18n.t("patients.flash.created"));
```
Clés flash dans `module.flash.*` (`patients.flash.created`, `patients.flash.deleted`…).

**RTL** : après un module, vérifier l'arabe (forms/tables) ; n'ajouter du `[dir="rtl"]` dans `app.css`
qu'en cas de réel problème d'alignement (le socle gère déjà le gros).

---

## 2. Slices (1 slice = 1 session = `mvnd test` + coche + ligne de journal + `/clear`)

> **Ordre** : d'abord le **socle transverse** (slice 0), puis par **trafic décroissant**. Chaque slice de
> module traduit SES templates **+** ses flash messages **+** ses statuts, **en FR/EN/AR**, et ajoute/renforce
> un test de rendu (assertion qu'une clé se résout, et `?lang=en` bascule).

- [x] **Slice 0 — Socle transverse** : `common.*` (boutons/labels fréquents) + `status.*` (tous statuts) +
      `priority.*` + `paymethod.*` + helper `WebI18n.t(...)`. Migrer `@paymentMethods.label` → `MessageSource`. Aucun template
      module encore, mais ces clés débloquent tous les suivants. Test : `status.*` se résout en 3 langues.
- [x] **Slice 1 — Patients** (`patients/list,detail,form` + dossier) + flash `PatientWebController`.
- [ ] **Slice 2 — Rendez-vous** (`appointments/list,week,form`) + flash (18 chaînes).
- [ ] **Slice 3 — Consultations** (`consultations/list,detail,form` + `prescriptions/*`) + flash (12).
- [ ] **Slice 4 — Facturation** (`billing/dashboard,queue,invoices/*`) + flash (10) + `paymethod.*` appliqué.
- [ ] **Slice 5 — Laboratoire** (`lab/worklist,list,detail,form,result-entry,bulletin`) + flash.
- [ ] **Slice 6 — Imagerie** (`radiology/*`) + flash.
- [ ] **Slice 7 — Pharmacie** (`pharmacy/*` dont `drugs/*`, `dispensations/*`, `prescriptions/worklist`) + flash.
- [ ] **Slice 8 — Hospitalisation** (`hospitalization/*`) + flash (16).
- [ ] **Slice 9 — Maternité** (`maternity/*`) + flash.
- [ ] **Slice 10 — Rapports** (`reports/*`).
- [ ] **Slice 11 — Admin** (`admin/*` : users, config, clinics, departments, acts, lab-tests, insurance, icd10, audit) + flash.
- [ ] **Slice 12 — Portail patient** (`portal/*`).
- [ ] **Slice 13 — Audit RTL fin** : parcours AR des modules à fort formulaire/tableau ; correctifs `app.css` ciblés.

**Exclus volontairement** (restent FR / hors i18n) : pages d'**impression autonomes** (`*/bulletin`,
`*/receipt`, `prescriptions/print`, `reports/pdf-report`) — documents officiels, langue fixe ; `fragments/icons`,
`error.html` (peut être fait en slice 11 si désiré).

---

## 3. Définition de « fait » par slice
1. Toutes les chaînes visibles du module → clés, **présentes dans les 3 bundles** (alignées).
2. Flash du contrôleur via `WebI18n.t(...)`.
3. Statuts/enum affichés via clés dynamiques.
4. `mvnd test` vert ; test de rendu du module renforcé (FR + `?lang=en`).
5. Coche la case ici + 1 ligne de journal. `/clear`.

---

## 4. Journal
| Date | Slice | Résultat |
|---|---|---|
| 2026-06-28 | (plan) | Plan créé. Socle P3.2 fait (5/88 templates). Conventions verrouillées ; ordre par trafic ; impression exclue. |
| 2026-06-28 | 0 — Socle | `common.*` complété (add/confirm/status/date/name/none/required) + `status.*` (20 statuts) + `priority.*` (NORMAL/URGENT, namespace ajouté) + `paymethod.*` (9 modes) dans les 3 bundles, alignés. Helper `i18n/WebI18n.t(...)` créé. `PaymentMethods.label` migré sur `MessageSource` (suit la locale ; fallback code-brut/« — » conservé). Test `I18nBundleTest` (3 langues) + smoke vert (22 tests). |
| 2026-06-28 | 1 — Patients | `patients/{list,form,detail}` clés `#{}` (+ titres layout, statuts dynamiques `#{${'status.'+x}}`, `common.view/clear` ajoutés). Flash photo via `i18n.t`. `PatientOverviewService` (alertes + timeline P3.6) i18n via `WebI18n` (args paramétrés, locale du thread requête). Test render FR+`?lang=en` ajouté au smoke ; `PatientOverviewServiceTest` recâblé (MessageSource FR réel). Suite complète verte (175 tests). |
