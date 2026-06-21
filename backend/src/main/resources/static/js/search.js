/* ============================================================
   Recherche globale — palette de commandes (P3.5)
   Vanilla JS, sans dépendance. Ouverture : clic, Ctrl/⌘+K, "/".
   Navigation clavier complète (↑ ↓ Entrée Échap) + ARIA combobox.
   ============================================================ */
(function () {
    'use strict';

    var overlay = document.getElementById('cmdk-overlay');
    var input   = document.getElementById('cmdk-input');
    var list    = document.getElementById('cmdk-results');
    var trigger = document.getElementById('global-search-trigger');
    if (!overlay || !input || !list) return; // pages sans chrome (portail/login)

    var SUGGEST_URL = overlay.getAttribute('data-suggest-url') || '/search/suggest';
    var SEARCH_URL  = overlay.getAttribute('data-search-url')  || '/search';
    var PROMPT_TXT  = list.getAttribute('data-prompt') || '';
    var EMPTY_TXT   = list.getAttribute('data-empty')  || '';

    var items = [];          // résultats à plat (pour la navigation clavier)
    var activeIndex = -1;
    var lastFocus = null;
    var debounceTimer = null;
    var seq = 0;             // anti course : on ignore les réponses obsolètes

    // ── Ouverture / fermeture ────────────────────────────────────────────────
    function open() {
        if (!overlay.hidden) return;
        lastFocus = document.activeElement;
        overlay.hidden = false;
        document.body.style.overflow = 'hidden';
        input.value = '';
        renderPrompt();
        input.setAttribute('aria-expanded', 'true');
        setTimeout(function () { input.focus(); }, 0);
    }

    function close() {
        if (overlay.hidden) return;
        overlay.hidden = true;
        document.body.style.overflow = '';
        input.setAttribute('aria-expanded', 'false');
        input.removeAttribute('aria-activedescendant');
        items = [];
        activeIndex = -1;
        if (lastFocus && typeof lastFocus.focus === 'function') lastFocus.focus();
    }

    // ── Rendu ────────────────────────────────────────────────────────────────
    function renderMessage(msg) {
        list.innerHTML = '';
        var li = document.createElement('li');
        li.className = 'cmdk-empty';
        li.textContent = msg;
        list.appendChild(li);
    }
    function renderPrompt() { renderMessage(PROMPT_TXT); }

    function render(results) {
        items = results || [];
        activeIndex = items.length ? 0 : -1;
        list.innerHTML = '';
        input.removeAttribute('aria-activedescendant');

        if (!items.length) { renderMessage(EMPTY_TXT); return; }

        var lastCat = null;
        items.forEach(function (r, i) {
            if (r.category !== lastCat) {
                lastCat = r.category;
                var header = document.createElement('li');
                header.className = 'cmdk-cat';
                header.setAttribute('role', 'presentation');
                header.textContent = r.category;
                list.appendChild(header);
            }
            var li = document.createElement('li');
            li.className = 'cmdk-item';
            li.id = 'cmdk-item-' + i;
            li.setAttribute('role', 'option');
            li.setAttribute('aria-selected', i === activeIndex ? 'true' : 'false');
            li.dataset.url = r.url;

            var icon = document.createElement('span');
            icon.className = 'cmdk-item-icon';
            icon.setAttribute('aria-hidden', 'true');
            icon.textContent = r.icon || '•';
            li.appendChild(icon);

            var body = document.createElement('span');
            body.className = 'cmdk-item-body';
            var label = document.createElement('span');
            label.className = 'cmdk-item-label';
            label.textContent = r.label;
            body.appendChild(label);
            if (r.sublabel) {
                var sub = document.createElement('span');
                sub.className = 'cmdk-item-sub';
                sub.textContent = r.sublabel;
                body.appendChild(sub);
            }
            li.appendChild(body);

            li.addEventListener('mousemove', function () { setActive(i); });
            li.addEventListener('click', function () { go(i); });
            list.appendChild(li);
        });
        updateActiveAttrs();
    }

    function itemEls() { return list.querySelectorAll('.cmdk-item'); }

    function updateActiveAttrs() {
        var els = itemEls();
        els.forEach(function (el, i) {
            el.setAttribute('aria-selected', i === activeIndex ? 'true' : 'false');
            el.classList.toggle('active', i === activeIndex);
        });
        if (activeIndex >= 0 && els[activeIndex]) {
            input.setAttribute('aria-activedescendant', els[activeIndex].id);
            els[activeIndex].scrollIntoView({ block: 'nearest' });
        }
    }

    function setActive(i) {
        if (i < 0 || i >= items.length) return;
        activeIndex = i;
        updateActiveAttrs();
    }

    function move(delta) {
        if (!items.length) return;
        activeIndex = (activeIndex + delta + items.length) % items.length;
        updateActiveAttrs();
    }

    function go(i) {
        var idx = (typeof i === 'number') ? i : activeIndex;
        if (idx >= 0 && items[idx]) window.location.href = items[idx].url;
    }

    // ── Récupération (debounce + anti course) ────────────────────────────────
    function query(q) {
        var mySeq = ++seq;
        fetch(SUGGEST_URL + '?q=' + encodeURIComponent(q), {
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin'
        })
            .then(function (res) { return res.ok ? res.json() : []; })
            .then(function (data) { if (mySeq === seq) render(data); })
            .catch(function () { if (mySeq === seq) renderMessage(EMPTY_TXT); });
    }

    // ── Événements ───────────────────────────────────────────────────────────
    input.addEventListener('input', function () {
        var q = input.value.trim();
        clearTimeout(debounceTimer);
        if (!q) { renderPrompt(); items = []; activeIndex = -1; return; }
        debounceTimer = setTimeout(function () { query(q); }, 160);
    });

    input.addEventListener('keydown', function (e) {
        switch (e.key) {
            case 'ArrowDown': e.preventDefault(); move(1); break;
            case 'ArrowUp':   e.preventDefault(); move(-1); break;
            case 'Enter':
                e.preventDefault();
                if (activeIndex >= 0) { go(); }
                else if (input.value.trim()) {
                    window.location.href = SEARCH_URL + '?q=' + encodeURIComponent(input.value.trim());
                }
                break;
            case 'Escape': e.preventDefault(); close(); break;
        }
    });

    overlay.addEventListener('mousedown', function (e) {
        if (e.target === overlay) close(); // clic sur le fond
    });

    if (trigger) trigger.addEventListener('click', open);

    // Raccourcis globaux : Ctrl/⌘+K partout ; "/" hors champ de saisie.
    document.addEventListener('keydown', function (e) {
        if ((e.ctrlKey || e.metaKey) && (e.key === 'k' || e.key === 'K')) {
            e.preventDefault();
            overlay.hidden ? open() : close();
            return;
        }
        if (e.key === '/' && overlay.hidden && !isTyping(e.target)) {
            e.preventDefault();
            open();
        }
    });

    function isTyping(el) {
        if (!el) return false;
        var tag = (el.tagName || '').toLowerCase();
        return tag === 'input' || tag === 'textarea' || tag === 'select' || el.isContentEditable;
    }
})();
