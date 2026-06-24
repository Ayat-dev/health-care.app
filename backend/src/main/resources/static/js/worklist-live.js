/*
 * Worklists temps réel (P5.1 Lot D) — client STOMP minimal sur WebSocket natif.
 *
 * S'active uniquement si la page expose un élément [data-worklist-topic]. À réception
 * d'un message sur le topic, affiche un toast puis ré-actualise la liste (rechargement
 * débouncé — sauté si l'utilisateur est en train de saisir, pour ne pas le déranger).
 * Dégradation gracieuse : sans WebSocket ou si la connexion échoue, la page reste
 * pleinement fonctionnelle (rendu serveur), on perd seulement le direct.
 *
 * Pas de dépendance externe (SockJS/stomp.js) : la CSP du projet interdit les CDN et
 * l'endpoint serveur est un WebSocket STOMP brut. Le protocole utilisé ici se limite à
 * CONNECT / SUBSCRIBE / réception de MESSAGE — pas de heartbeat, pas d'envoi montant.
 */
(function () {
    "use strict";

    var anchor = document.querySelector("[data-worklist-topic]");
    if (!anchor || !("WebSocket" in window)) return;

    var TOPIC = anchor.getAttribute("data-worklist-topic");
    var NUL = "\u0000";
    var ws = null;
    var connected = false;
    var retry = 0;
    var pending = 0;            // nb de mises à jour reçues non encore intégrées
    var refreshTimer = null;
    var closedByUs = false;

    function meta(name) {
        var el = document.querySelector('meta[name="' + name + '"]');
        return el ? el.getAttribute("content") : "";
    }

    function wsUrl() {
        var proto = location.protocol === "https:" ? "wss:" : "ws:";
        return proto + "//" + location.host + "/ws";
    }

    // ── Encodage/décodage des trames STOMP ────────────────────────────────────
    function frame(command, headers, body) {
        var out = command + "\n";
        Object.keys(headers || {}).forEach(function (k) {
            out += k + ":" + headers[k] + "\n";
        });
        out += "\n" + (body || "") + NUL;
        return out;
    }

    function parseFrame(text) {
        var idx = text.indexOf("\n\n");
        var head = idx >= 0 ? text.substring(0, idx) : text;
        var body = idx >= 0 ? text.substring(idx + 2) : "";
        if (body.charAt(body.length - 1) === NUL) body = body.slice(0, -1);
        var lines = head.split("\n");
        return { command: lines[0], body: body };
    }

    // ── Connexion ─────────────────────────────────────────────────────────────
    function connect() {
        try {
            ws = new WebSocket(wsUrl(), ["v12.stomp", "v11.stomp", "v10.stomp"]);
        } catch (e) {
            return; // WebSocket indisponible → dégradation gracieuse
        }

        ws.onopen = function () {
            var headers = { "accept-version": "1.2,1.1,1.0", "heart-beat": "0,0" };
            var csrf = meta("_csrf");
            var csrfHeader = meta("_csrf_header");
            if (csrf && csrfHeader) headers[csrfHeader] = csrf;
            ws.send(frame("CONNECT", headers, ""));
        };

        ws.onmessage = function (evt) {
            String(evt.data).split(NUL).forEach(function (chunk) {
                if (!chunk || !chunk.trim()) return;
                var f = parseFrame(chunk + NUL);
                if (f.command === "CONNECTED") {
                    connected = true;
                    retry = 0;
                    ws.send(frame("SUBSCRIBE", { id: "sub-worklist", destination: TOPIC }, ""));
                } else if (f.command === "MESSAGE") {
                    onUpdate(f.body || "Mise à jour");
                } else if (f.command === "ERROR") {
                    // Abonnement refusé (rôle) ou autre : on cesse, sans bruit.
                    closedByUs = true;
                    try { ws.close(); } catch (e) {}
                }
            });
        };

        ws.onclose = function () {
            connected = false;
            if (closedByUs) return;
            retry++;
            var delay = Math.min(30000, 1000 * Math.pow(2, Math.min(retry, 5)));
            setTimeout(connect, delay);
        };

        ws.onerror = function () {
            try { ws.close(); } catch (e) {}
        };
    }

    // ── Réception d'une mise à jour ────────────────────────────────────────────
    function onUpdate(summary) {
        pending++;
        showToast(summary);
        scheduleRefresh();
    }

    function isTyping() {
        var el = document.activeElement;
        if (!el) return false;
        var tag = el.tagName;
        return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || el.isContentEditable;
    }

    function scheduleRefresh() {
        if (refreshTimer) clearTimeout(refreshTimer);
        refreshTimer = setTimeout(function () {
            // Ne pas recharger sous les doigts de l'utilisateur (ex. filtre caisse).
            if (isTyping()) { refreshTimer = null; return; }
            location.reload();
        }, 1500);
    }

    // ── Toast ──────────────────────────────────────────────────────────────────
    function toastWrap() {
        var w = document.getElementById("wl-toast-wrap");
        if (!w) {
            w = document.createElement("div");
            w.id = "wl-toast-wrap";
            w.className = "wl-toast-wrap";
            document.body.appendChild(w);
        }
        return w;
    }

    function showToast(summary) {
        var t = document.createElement("div");
        t.className = "wl-toast";
        var label = pending > 1 ? summary + " (" + pending + ")" : summary;
        t.innerHTML = '<span class="wl-toast-dot" aria-hidden="true"></span><span></span>';
        t.lastChild.textContent = "🔔 " + label;
        if (isTyping()) {
            // L'utilisateur saisit : pas de rechargement auto → toast cliquable pour actualiser.
            t.classList.add("wl-toast-action");
            t.title = "Cliquer pour actualiser";
            t.addEventListener("click", function () { location.reload(); });
        }
        var w = toastWrap();
        w.appendChild(t);
        setTimeout(function () { t.classList.add("wl-toast-in"); }, 10);
        setTimeout(function () {
            t.classList.remove("wl-toast-in");
            setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 300);
        }, 4000);
    }

    window.addEventListener("beforeunload", function () {
        closedByUs = true;
        if (ws) { try { ws.close(); } catch (e) {} }
    });

    connect();
})();
