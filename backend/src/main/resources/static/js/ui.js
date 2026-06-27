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
 * Sûr à charger partout : si la page n'a pas de `.tab[data-tab]`, no-op.
 */
(function () {
    function initTabs() {
        const tabs = Array.from(document.querySelectorAll('.tab[data-tab]'));
        if (!tabs.length) return;

        const panels = Array.from(document.querySelectorAll('.tab-content'));
        panels.forEach(p => p.setAttribute('role', 'tabpanel'));

        const def = (tabs.find(t => t.classList.contains('active')) || tabs[0]).dataset.tab;

        function activate(name, updateHash) {
            const panel = document.getElementById('tab-' + name);
            if (!panel) return;
            panels.forEach(p => p.style.display = 'none');
            tabs.forEach(t => { t.classList.remove('active'); t.setAttribute('aria-selected', 'false'); });
            panel.style.display = 'block';
            const btn = tabs.find(t => t.dataset.tab === name);
            if (btn) { btn.classList.add('active'); btn.setAttribute('aria-selected', 'true'); }
            if (updateHash && location.hash !== '#' + name) {
                history.replaceState(null, '', '#' + name);
            }
        }

        tabs.forEach(t => t.addEventListener('click', () => activate(t.dataset.tab, true)));
        window.addEventListener('hashchange', () => activate((location.hash || '#' + def).slice(1), false));

        const initial = (location.hash || '#' + def).slice(1);
        activate(document.getElementById('tab-' + initial) ? initial : def, false);
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
