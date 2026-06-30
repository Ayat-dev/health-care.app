/* ============================================================
   ClinicApp — helpers UI partagés (chargés sur toutes les pages)
   ============================================================ */

/*
 * Onglets deep-linkables (#hash) + ARIA — patron mutualisé.
 * Marche-up attendu : une barre `.tabs` de boutons `.tab[data-tab="x"]`
 * et, pour chaque, un panneau `#tab-x.tab-content`. L'onglet portant la
 * classe `active` (sinon le premier) est l'onglet par défaut.
 *
 * Comportement : l'onglet actif est reflété dans l'URL (#x) → lien
 * partageable + survit au rechargement / au bouton « Modifier » qui
 * revient sur la page. Aucune dépendance, aucun `onclick` inline,
 * aucun usage de l'objet global `event`.
 *
 * Accessibilité (motif WAI-ARIA « Tabs », C2-reliquat) : le JS pose la
 * sémantique complète que le HTML ne porte pas — `role=tablist` sur la barre,
 * et pour chaque onglet `aria-controls` ↔ son panneau `aria-labelledby`, plus
 * un `tabindex` mobile (roving : seul l'onglet actif est tabbable). Navigation
 * clavier : ←/→ (et ↑/↓), Origine/Fin déplacent ET activent (activation
 * automatique), le panneau est focalisable (`tabindex=0`). Les onglets/panneaux
 * étant générés ensemble côté serveur, on relie par convention `#tab-<name>`.
 *
 * Sûr à charger partout : si la page n'a pas de `.tab[data-tab]`, no-op.
 */
(function () {
    function initTabs() {
        const tabs = Array.from(document.querySelectorAll('.tab[data-tab]'));
        if (!tabs.length) return;

        // La barre porte role=tablist (déjà dans les templates, posé ici par sûreté).
        const tablist = tabs[0].closest('.tabs');
        if (tablist && tablist.getAttribute('role') !== 'tablist') {
            tablist.setAttribute('role', 'tablist');
        }

        const panels = Array.from(document.querySelectorAll('.tab-content'));
        const def = (tabs.find(t => t.classList.contains('active')) || tabs[0]).dataset.tab;

        // Liaison ARIA onglet ↔ panneau (un panneau = #tab-<name>, un onglet = #tab-btn-<name>).
        // Les templates portent déjà cette sémantique en statique (auditable par axe avant JS) ;
        // on ne complète ici que ce qui manquerait (filet pour une future page à onglets).
        tabs.forEach(t => {
            const name = t.dataset.tab;
            if (!t.id) t.id = 'tab-btn-' + name;
            const panel = document.getElementById('tab-' + name);
            if (panel) {
                if (!t.getAttribute('aria-controls')) t.setAttribute('aria-controls', panel.id);
                if (panel.getAttribute('role') !== 'tabpanel') panel.setAttribute('role', 'tabpanel');
                if (!panel.getAttribute('aria-labelledby')) panel.setAttribute('aria-labelledby', t.id);
                if (!panel.hasAttribute('tabindex')) panel.setAttribute('tabindex', '0');
            }
        });

        function activate(name, updateHash, setFocus) {
            const panel = document.getElementById('tab-' + name);
            if (!panel) return;
            panels.forEach(p => p.style.display = 'none');
            tabs.forEach(t => {
                t.classList.remove('active');
                t.setAttribute('aria-selected', 'false');
                t.setAttribute('tabindex', '-1'); // roving : non-actifs hors séquence de tabulation
            });
            panel.style.display = 'block';
            const btn = tabs.find(t => t.dataset.tab === name);
            if (btn) {
                btn.classList.add('active');
                btn.setAttribute('aria-selected', 'true');
                btn.setAttribute('tabindex', '0');
                if (setFocus) btn.focus();
            }
            if (updateHash && location.hash !== '#' + name) {
                history.replaceState(null, '', '#' + name);
            }
        }

        tabs.forEach((t, i) => {
            t.addEventListener('click', () => activate(t.dataset.tab, true, false));
            // Clavier : flèches/Origine/Fin déplacent le focus ET activent (activation auto).
            t.addEventListener('keydown', (e) => {
                let target = null;
                switch (e.key) {
                    case 'ArrowRight':
                    case 'ArrowDown': target = tabs[(i + 1) % tabs.length]; break;
                    case 'ArrowLeft':
                    case 'ArrowUp':   target = tabs[(i - 1 + tabs.length) % tabs.length]; break;
                    case 'Home':      target = tabs[0]; break;
                    case 'End':       target = tabs[tabs.length - 1]; break;
                    default: return;
                }
                e.preventDefault();
                activate(target.dataset.tab, true, true);
            });
        });
        window.addEventListener('hashchange', () => activate((location.hash || '#' + def).slice(1), false, false));

        const initial = (location.hash || '#' + def).slice(1);
        activate(document.getElementById('tab-' + initial) ? initial : def, false, false);
    }

    document.addEventListener('DOMContentLoaded', initTabs);
})();

/*
 * Tiroir de navigation (mobile) : le hamburger ouvre/ferme la sidebar hors-écran,
 * un voile l'assombrit. Fermeture par voile, touche Échap, ou clic sur un lien de nav.
 * No-op si la page n'a pas la sidebar (login, portail, impression).
 */
(function () {
    function initSidebarDrawer() {
        var burger = document.getElementById('sidebar-toggle');
        var sidebar = document.getElementById('sidebar');
        var scrim = document.getElementById('sidebar-scrim');
        if (!burger || !sidebar || !scrim) return;

        function isOpen() { return sidebar.classList.contains('is-open'); }
        function open() {
            sidebar.classList.add('is-open');
            scrim.hidden = false;
            burger.setAttribute('aria-expanded', 'true');
        }
        function close() {
            sidebar.classList.remove('is-open');
            scrim.hidden = true;
            burger.setAttribute('aria-expanded', 'false');
        }

        burger.addEventListener('click', function () { isOpen() ? close() : open(); });
        scrim.addEventListener('click', close);
        document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && isOpen()) close(); });
        // Sur mobile, suivre un lien de nav referme le tiroir.
        sidebar.querySelectorAll('.nav-item').forEach(function (a) {
            a.addEventListener('click', function () { if (window.innerWidth <= 768) close(); });
        });
        // Repasser en grand écran → réinitialise l'état du tiroir.
        window.addEventListener('resize', function () { if (window.innerWidth > 768 && isOpen()) close(); });
    }

    /* Menu déroulant de langue (<details>) : se referme au clic à l'extérieur. */
    function initLangDropdownAutoClose() {
        document.querySelectorAll('details.lang-dropdown').forEach(function (d) {
            document.addEventListener('click', function (e) {
                if (d.open && !d.contains(e.target)) d.removeAttribute('open');
            });
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        initSidebarDrawer();
        initLangDropdownAutoClose();
    });
})();
