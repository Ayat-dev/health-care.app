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
