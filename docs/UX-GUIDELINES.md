# ClinicApp — Guide UX (principes directeurs)

> **But** : codifier durablement les principes UX du projet pour qu'ils soient **en contexte à chaque
> session**, sans dépendre du chargement d'un skill. Source : distillation du skill `ui-ux-pro-max`
> (catégories prioritaires 1→10) **croisée avec notre système existant** (`static/css/app.css`).
> Référencé depuis `CLAUDE.md` (§ Design system) et `docs/REFACTOR-ROLES-UX.md` (WS5 §6).
>
> **Règle d'or** : `app.css` est la **source de vérité** du design. On **étend**, on ne réécrit pas, on
> ne crée pas de nouveau fichier CSS. Ces principes encadrent *comment* l'étendre.

---

## 0. Validation externe du système (2026-06-27)

Le skill `ui-ux-pro-max`, interrogé pour « healthcare clinic management dashboard / admin / data-dense »,
recommande très exactement ce que nous avons déjà — donc **on reste sur notre cap**, on ne pivote pas :

| Dimension | Reco skill | Notre `app.css` | Verdict |
|---|---|---|---|
| Style | **Data-Dense Dashboard** (tables, KPI cards, padding serré, grille) | `.stats-grid` / `.table` / `.panel` | ✅ aligné |
| Primaire | `#2563EB` | `--blue: #2563eb` | ✅ identique |
| Accent | `#059669` (vert) | `--green: #059669` | ✅ identique |
| Fond / texte | `#F8FAFC` / `#0F172A` | `--bg` / `--text-900` | ✅ identique |
| Statuts | green / amber / red | `--green` / `--amber` / `--red` | ✅ présents |
| A11y cible | WCAG **AA** | corrections P3.4 déjà faites | ✅ en cours |

**À éviter** (anti-patterns relevés par le skill, valables ici) : design ornemental, absence de filtrage,
emojis en guise d'icônes structurelles.

---

## 1. Accessibilité — CRITIQUE (priorité absolue)

Le socle est déjà posé (P3.4 : `focus-visible`, `skip-link`, `prefers-reduced-motion`, contrastes corrigés
dans `:root`). **Ne pas régresser.**

- **Contraste ≥ 4,5:1** pour le texte normal (3:1 pour le grand texte). Utiliser les tokens texte
  (`--text-900/700/500/400`) — `--text-400` (#6b7280) est le **plus clair admissible sur blanc**.
  Ne jamais descendre sous lui pour du texte porteur de sens. Sur la sidebar sombre, utiliser
  `--sidebar-section` (#7e8ba3), pas un gris plus foncé.
- **Focus visible** : tout élément interactif garde son `:focus-visible` (déjà global dans `app.css`).
  **Ne jamais** faire `outline: none` sans alternative visible.
- **La couleur ne porte jamais l'information seule** : un statut = badge **coloré + texte** (`badge-PAYE`,
  `badge-VALIDE`…), une anomalie labo = `.row-abnormal` **+ pictogramme ⚠**.
- **Labels de formulaire** : un `<label>` visible par champ (jamais le placeholder seul) ; lier
  `for`/`id`. Champs requis marqués (astérisque).
- **Hiérarchie de titres** séquentielle (h1→h2→h3), pas de saut de niveau.
- **Saut de contenu** : le `.skip-link` existe — ne pas le casser en réorganisant le layout.
- **Navigation clavier** : ordre de tabulation = ordre visuel. Les onglets du dossier patient sont déjà
  en `role="tablist"`/`tab`/`tabpanel` avec `aria-selected` — réutiliser ce patron pour tout nouvel onglet.

---

## 2. Interaction & états — CRITIQUE

- **Affordance de clic** : `cursor: pointer` sur tout élément cliquable ; zone de clic confortable
  (lignes de tableau, boutons d'icône ≥ ~32px de hauteur effective).
- **États distincts et stables** : hover / actif / focus / **désactivé** doivent être visuellement
  différents. Les transitions de press **ne déplacent pas** la mise en page (pas de jitter) — animer
  couleur / opacité / ombre, jamais la largeur/hauteur.
- **Désactivé = sémantique + visuel** : attribut `disabled` réel + opacité réduite ; un élément qui *a
  l'air* cliquable mais ne fait rien est interdit.
- **Feedback asynchrone** : un bouton de soumission se désactive pendant l'appel et montre un état
  (spinner/texte). Pas de double-submit possible.
- **Confirmation avant action destructrice** (suppression, annulation de facture/dossier). Les
  suppressions médicales restent **soft** (`deleted_at`) — jamais de hard-delete (cf. CLAUDE.md).
- **Transitions** : 150–300 ms, courbe `ease` (token `--transition: .15s ease`). Rien au-dessus de
  ~400 ms. Tout est désactivé sous `prefers-reduced-motion` (bloc déjà présent).

---

## 3. Mise en page & responsive — ÉLEVÉE

- **Largeur de contenu** cohérente : la zone `.main` / `.page-content` cadence l'espacement vertical —
  ne pas remettre de `margin-top` inline ad hoc (le polish WS5 les a justement supprimés).
- **Rythme d'espacement** par paliers (≈ 8/12/16/24/32). Utiliser l'espacement de `.page-content`,
  `.panel`, `.form-group` plutôt que des valeurs arbitraires.
- **Pas de scroll horizontal** : les tableaux denses passent par `.table-wrap` (scroll interne maîtrisé),
  pas par un débordement de page.
- **Hiérarchie visuelle** par taille / graisse / espacement / contraste — **pas par la couleur seule**.
- **Une seule action primaire** par écran (`.btn-primary`) ; le reste en `.btn-ghost` / `.btn-sm`.
  Les vraies actions vivent dans l'en-tête de page ; la **navigation** vit dans le fil d'Ariane +
  `.module-tabs` (ne pas re-dupliquer des rangées de boutons de nav — WS5 les a retirées).

---

## 4. Typographie & couleur — MOYENNE

- **Base 14px** (convention projet), `line-height: 1.5`. Corps de texte lisible, ~65–75 caractères/ligne
  pour les blocs longs (notes cliniques, comptes-rendus).
- **Échelle de graisses** : titres 600–700, libellés 500, corps 400. La graisse renforce la hiérarchie.
- **Chiffres tabulaires** obligatoires pour toute donnée numérique alignée : montants, quantités, délais,
  KPI. Déjà appliqué à `.table td/th`, `.stat-card-value`, `.num` ; pour une colonne ad hoc ajouter
  `.num` + l'alignement à droite `.text-right`.
- **Tokens sémantiques, pas de hex brut** dans les templates : référencer `--blue` / `--green` / `--red`
  / `--amber` (et leurs `*-light`), jamais `#2563eb` en dur dans une vue.
- **Utilitaires de texte** (au lieu d'un `style="color:…"` répété) : `.text-muted` (texte secondaire —
  légendes, unités, méta ; `var(--text-500)`, AA-safe) et `.text-danger` (montant dû / impayé / échéance ;
  `var(--red-strong)` = rouge assombri qui reste ≥4,5:1, contrairement à `--red` trop clair pour du texte).
- **Monnaie** : `BigDecimal` côté code ; au rendu, `#numbers.formatDecimal(x, 1, 'WHITESPACE', 2)`
  + suffixe ` F` (convention Niger/XOF), colonne **alignée à droite**. ⚠ le mot-clé milliers est
  `WHITESPACE`, **pas** `SPACE` (sinon 500 au rendu — cf. memory).

---

## 5. Tables & données denses — c'est notre pain quotidien

C'est le style retenu (« Data-Dense Dashboard ») : maximiser la lisibilité sans surcharger.

- **Toujours** envelopper dans `.table-wrap` ; en-têtes `<th>` clairs ; surbrillance de ligne au survol.
- **Colonnes numériques** : `.text-right` + chiffres tabulaires (alignement décimal visuel).
- **Tri** : si une table est triable, indiquer l'état via `aria-sort`.
- **État vide explicite** : `.empty-state` avec message utile **+ action** (« Aucune demande — Créer une
  demande »), jamais une table vide muette.
- **Filtrage** : prévoir un filtre dès qu'une liste peut grossir (l'absence de filtre est un anti-pattern
  explicite du style). Les worklists labo/imagerie priorisent déjà (URGENT d'abord).
- **Sémantique de ligne** : anomalie/alerte = classe sémantique (`.row-abnormal`) **+ texte/icône**.
- **Graphiques** (rapports) : légende visible, valeurs au survol, **jamais la couleur seule** (ajouter
  forme/texte) ; palette accessible (éviter rouge/vert seuls). Toujours offrir l'alternative tableau pour
  l'accessibilité. Pour > 5 catégories, barres plutôt que camembert.

---

## 6. Formulaires & feedback — MOYENNE

- **Label visible** par champ ; **erreur sous le champ** concerné (pas seulement un résumé en haut).
- **Message d'erreur = cause + correctif** (« Le stock est insuffisant pour ce lot », pas « Erreur »).
  Après échec, focus sur le premier champ invalide.
- **Type d'input sémantique** (`email`, `tel`, `number`, `date`) → bon clavier mobile + validation native.
- **Validation au `blur`**, pas à la frappe.
- **Divulgation progressive** : ne pas noyer l'utilisateur ; révéler les options complexes au besoin
  (ex. lignes de facture ajoutées dynamiquement, sélecteur d'acte qui pré-remplit prix/description).
- **États de soumission** : chargement → succès/erreur via flash (`.alert-info`/`.alert-error`).
- **`aria-live`** pour les erreurs/toasts afin que les lecteurs d'écran les annoncent sans voler le focus.
- **Distinction lecture-seule vs désactivé** : sémantiquement et visuellement différents.

---

## 7. Iconographie & cohérence visuelle

- **Pas d'emoji comme icône structurelle** de navigation/action (dépendant de la police, incohérent).
  Préférer du SVG cohérent (jeu unique, même épaisseur de trait). *NB : la sidebar utilise actuellement
  des emojis de section — toléré comme repère, mais toute nouvelle icône fonctionnelle = SVG.*
- **Un seul jeu d'icônes**, taille par paliers (tokens), alignées à la ligne de base du texte.
- **Style filled vs outline** : un seul par niveau de hiérarchie.
- **Ombres / rayons cohérents** : utiliser l'échelle (`--shadow-sm/--shadow`, `--radius-*`), pas de
  valeurs arbitraires.

---

## 8. Navigation (acquis WS5 — ne pas régresser)

- **Fil d'Ariane** auto-dérivé (Accueil / Module / Page) : retour module **et** accueil en ≤ 1 clic.
- **Sous-nav de module** via le registre **`config/ModuleTabs`** (1 ligne/module), **role-aware** : un
  onglet est gaté par rôle → on **n'expose jamais** une destination qui finirait en 403
  (règle « empty-nav-state »). Pour un nouveau module à sous-pages : ajouter une entrée au registre,
  ne pas remettre une rangée de boutons dans l'en-tête.
- **Bouton Retour** mutualisé : `fragments/ui :: back(href)` (in-app) / `backHistory` (pages d'impression).
- **Emplacement de nav constant** d'une page à l'autre ; ne pas mélanger les patrons.
- **Localisation active** toujours mise en évidence (`.nav-item.active`, onglet actif).
- **Deep-linking** : les vues clés sont atteignables par URL ; les onglets du dossier patient survivent
  au rechargement via `#hash`.
- **Onglets** : logique mutualisée dans **`static/js/ui.js`** (chargé partout, no-op sans onglets). Pour
  rendre une page à onglets : une barre `.tabs[role=tablist]` de boutons `.tab[data-tab="x"]` + un panneau
  `#tab-x.tab-content` chacun ; l'onglet par défaut porte la classe `active`. **Aucun JS inline**, aucun
  `onclick`/`event` global.

---

## 9. Impression (bulletins, ordonnances, reçus, rapports)

- Pas de lib PDF au pom → les documents sont des **HTML optimisés impression** (navigateur → Imprimer).
- Le bloc `@media print` global masque déjà `.sidebar` / `.topbar` / `.breadcrumb` / `.module-tabs` /
  `.no-print`. Marquer `.no-print` tout ce qui ne doit pas sortir au papier.

---

## Checklist avant livraison d'une vue

**Accessibilité**
- [ ] Contraste texte ≥ 4,5:1 (tokens texte, pas plus clair que `--text-400` sur blanc)
- [ ] `:focus-visible` préservé sur tous les interactifs ; `.skip-link` intact
- [ ] Aucune info portée par la couleur seule (badge/ligne = couleur **+** texte/icône)
- [ ] Labels de champs visibles et liés ; titres séquentiels ; nouveaux onglets en patron ARIA tablist

**Interaction & forme**
- [ ] `cursor: pointer` sur le cliquable ; états hover/actif/focus/désactivé distincts et **sans jitter**
- [ ] Une seule action primaire `.btn-primary` ; nav via fil d'Ariane + `ModuleTabs` (pas de boutons dupliqués)
- [ ] Confirmation avant action destructrice ; suppressions soft
- [ ] Transitions 150–300 ms ; rien ne casse sous `prefers-reduced-motion`

**Données**
- [ ] Tables dans `.table-wrap` ; colonnes numériques `.text-right` + chiffres tabulaires
- [ ] `.empty-state` utile **+ action** ; filtrage prévu si la liste peut grossir
- [ ] Monnaie : `formatDecimal(..,'WHITESPACE',..)` + ` F`, alignée à droite

**Cohérence**
- [ ] Couleurs/ombres/rayons via tokens (`--blue`/`--green`/`--shadow`/`--radius-*`), aucun hex brut en vue
- [ ] Pas d'emoji comme icône fonctionnelle nouvelle ; CSS **étendu** dans `app.css` (pas de nouveau fichier)
- [ ] i18n FR/EN/AR pour tout texte de chrome ajouté

---

## Pièges Thymeleaf récurrents (rappel — détail en memory)

- `formatDecimal(..)` milliers = **`WHITESPACE`** (pas `SPACE`).
- Apostrophe `''` doublée dans un titre `layout('…')` ou un `th:text` littéral → 500 au rendu ; utiliser
  une apostrophe typographique `'`.
- `@{}` ne s'imbrique **pas** dans `${}` → mettre le ternaire au niveau attribut
  (`th:action="${cond} ? @{a} : @{b}"`).
- `Boolean` nul dans un `and`/`or` SpEL → EL1001E ; garder `(x != null and x)`.
- Variable de boucle `th:each` ≠ mot réservé (`mod`, `div`, `and`…).
- `sec:authorize` en **attribut** sur `<th:block>`, jamais l'élément `<sec:authorize>`.

---

*Maintenir ce fichier en phase avec `app.css` et les conventions WS5. Dernière mise à jour : 2026-06-27.*
