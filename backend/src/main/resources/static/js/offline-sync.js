/*
 * ClinicApp — File de synchro des écritures hors-ligne (B4, PWA).
 *
 * Périmètre (tranche 1, verrouillé) : UNIQUEMENT la création de rendez-vous.
 * Quand le navigateur est hors-ligne, la soumission du formulaire RDV est mise en file
 * dans IndexedDB ; au retour réseau (événement `online` ou rechargement en ligne) la file
 * est rejouée vers POST /appointments/offline, idempotent via un en-tête Idempotency-Key
 * (UUID) → aucun doublon même si le rejeu part deux fois.
 *
 * PHI (décision B4) : la charge utile (dont le motif, texte libre potentiellement PHI) est
 * CHIFFRÉE au repos dans IndexedDB via AES-GCM (WebCrypto, clé 256 bits NON-extractible,
 * persistée comme objet CryptoKey — jamais exportable). Seul l'UUID de requête (aléatoire,
 * sans PHI) et le statut restent en clair, pour le dédoublonnage et l'affichage des compteurs.
 *
 * Conflits (décision B4) : un rejeu refusé par le serveur (409 : créneau pris, données
 * invalides) marque l'item en ÉCHEC et le CONSERVE (révision manuelle), sans rejeu en boucle.
 *
 * Dégradation gracieuse : sans IndexedDB ni WebCrypto (contexte non sécurisé), on n'intercepte
 * rien — le formulaire se soumet normalement. Inclus globalement par base.html.
 *
 * NB : le rejeu vit dans le CONTEXTE PAGE (pas le service worker) car (1) il a besoin du jeton
 * CSRF (balise <meta>) et du cookie de session, (2) la clé de chiffrement non-extractible y est
 * disponible. La Background Sync API (rejeu SW fenêtre fermée) est laissée en évolution future.
 */
(function () {
  'use strict';

  if (!('indexedDB' in window) || !window.crypto || !window.crypto.subtle || !window.crypto.randomUUID) {
    return; // pas de file hors-ligne : on laisse les formulaires se soumettre normalement
  }

  var DB_NAME = 'clinicapp-offline';
  var DB_VERSION = 1;
  var STORE_QUEUE = 'queue';     // items chiffrés
  var STORE_META = 'meta';       // clé de chiffrement
  var KEY_ID = 'cryptoKey';

  // ── IndexedDB ───────────────────────────────────────────────────────────
  function openDb() {
    return new Promise(function (resolve, reject) {
      var req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = function () {
        var db = req.result;
        if (!db.objectStoreNames.contains(STORE_QUEUE)) {
          db.createObjectStore(STORE_QUEUE, { keyPath: 'id', autoIncrement: true });
        }
        if (!db.objectStoreNames.contains(STORE_META)) {
          db.createObjectStore(STORE_META, { keyPath: 'k' });
        }
      };
      req.onsuccess = function () { resolve(req.result); };
      req.onerror = function () { reject(req.error); };
    });
  }

  function tx(db, store, mode) {
    return db.transaction(store, mode).objectStore(store);
  }

  function reqToPromise(req) {
    return new Promise(function (resolve, reject) {
      req.onsuccess = function () { resolve(req.result); };
      req.onerror = function () { reject(req.error); };
    });
  }

  // ── Clé de chiffrement (persistée, non-extractible) ──────────────────────
  function getCryptoKey(db) {
    return reqToPromise(tx(db, STORE_META, 'readonly').get(KEY_ID)).then(function (row) {
      if (row && row.key) return row.key;
      return crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt'])
        .then(function (key) {
          return reqToPromise(tx(db, STORE_META, 'readwrite').put({ k: KEY_ID, key: key }))
            .then(function () { return key; });
        });
    });
  }

  var enc = new TextEncoder();
  var dec = new TextDecoder();

  function encryptPayload(key, obj) {
    var iv = crypto.getRandomValues(new Uint8Array(12));
    return crypto.subtle.encrypt({ name: 'AES-GCM', iv: iv }, key, enc.encode(JSON.stringify(obj)))
      .then(function (ct) { return { iv: Array.from(iv), data: ct }; });
  }

  function decryptPayload(key, item) {
    return crypto.subtle.decrypt({ name: 'AES-GCM', iv: new Uint8Array(item.iv) }, key, item.data)
      .then(function (buf) { return JSON.parse(dec.decode(buf)); });
  }

  // ── File ─────────────────────────────────────────────────────────────────
  function enqueue(payload) {
    var requestKey = crypto.randomUUID();
    return openDb().then(function (db) {
      return getCryptoKey(db).then(function (key) {
        return encryptPayload(key, payload).then(function (sealed) {
          return reqToPromise(tx(db, STORE_QUEUE, 'readwrite').add({
            requestKey: requestKey,
            iv: sealed.iv,
            data: sealed.data,
            status: 'pending',
            error: null,
            createdAt: new Date().toISOString()
          }));
        });
      });
    });
  }

  function allItems(db) {
    return reqToPromise(tx(db, STORE_QUEUE, 'readonly').getAll());
  }

  function deleteItem(db, id) {
    return reqToPromise(tx(db, STORE_QUEUE, 'readwrite').delete(id));
  }

  function markFailed(db, item, message) {
    item.status = 'failed';
    item.error = message || 'error';
    return reqToPromise(tx(db, STORE_QUEUE, 'readwrite').put(item));
  }

  // ── Rejeu ──────────────────────────────────────────────────────────────
  function csrf() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    return {
      header: header ? header.getAttribute('content') : null,
      token: token ? token.getAttribute('content') : null
    };
  }

  function replayOne(db, key, item, syncUrl) {
    return decryptPayload(key, item).then(function (payload) {
      var headers = { 'Content-Type': 'application/json', 'Idempotency-Key': item.requestKey };
      var c = csrf();
      if (c.header && c.token) headers[c.header] = c.token;
      return fetch(syncUrl, {
        method: 'POST',
        credentials: 'same-origin',
        headers: headers,
        body: JSON.stringify(payload)
      }).then(function (res) {
        if (res.ok) return deleteItem(db, item.id);             // succès / idempotent → on retire
        if (res.status === 409) {                               // conflit métier → ÉCHEC conservé
          return res.json().catch(function () { return {}; })
            .then(function (b) { return markFailed(db, item, b.message); });
        }
        throw new Error('HTTP ' + res.status);                  // 5xx/401 → on laisse en pending
      });
    });
  }

  var draining = false;

  function drain() {
    if (draining || !navigator.onLine) return Promise.resolve();
    draining = true;
    var cfg = config();
    if (!cfg) { draining = false; return Promise.resolve(); }
    return openDb().then(function (db) {
      return getCryptoKey(db).then(function (key) {
        return allItems(db).then(function (items) {
          var pending = items.filter(function (i) { return i.status === 'pending'; });
          // Rejeu séquentiel : une erreur réseau interrompt (toujours hors-ligne).
          var chain = Promise.resolve();
          pending.forEach(function (item) {
            chain = chain.then(function () { return replayOne(db, key, item, cfg.syncUrl); })
              .catch(function () { /* réseau KO : on garde l'item en pending */ });
          });
          return chain;
        });
      });
    }).then(function () { draining = false; return refreshBanner(); })
      .catch(function () { draining = false; });
  }

  // ── UI : bannière de statut + interception du formulaire ─────────────────
  function config() {
    var el = document.getElementById('offline-queue-status');
    if (!el) return null;
    return {
      el: el,
      syncUrl: el.getAttribute('data-sync-url'),
      msgPending: el.getAttribute('data-msg-pending'),
      msgFailed: el.getAttribute('data-msg-failed'),
      msgQueued: el.getAttribute('data-msg-queued')
    };
  }

  function refreshBanner() {
    var cfg = config();
    if (!cfg) return Promise.resolve();
    return openDb().then(allItems).then(function (items) {
      var pending = items.filter(function (i) { return i.status === 'pending'; }).length;
      var failed = items.filter(function (i) { return i.status === 'failed'; }).length;
      var parts = [];
      if (pending > 0) parts.push(cfg.msgPending.replace('{0}', pending));
      if (failed > 0) parts.push(cfg.msgFailed.replace('{0}', failed));
      if (parts.length) {
        cfg.el.textContent = parts.join(' · ');
        cfg.el.classList.toggle('offline-banner-warn', failed > 0);
        cfg.el.hidden = false;
      } else {
        cfg.el.hidden = true;
      }
    });
  }

  function flash(message) {
    var cfg = config();
    if (!cfg || !message) return;
    cfg.el.textContent = message;
    cfg.el.hidden = false;
  }

  function interceptForm() {
    var form = document.querySelector('form[data-offline-form="appointment"]');
    if (!form) return;
    form.addEventListener('submit', function (e) {
      if (navigator.onLine) return; // en ligne : soumission serveur classique, inchangée
      e.preventDefault();
      var data = {};
      new FormData(form).forEach(function (v, k) { if (v !== '') data[k] = v; });
      var cfg = config();
      enqueue(data).then(function () {
        if (cfg && cfg.msgQueued) flash(cfg.msgQueued);
        var redirect = form.getAttribute('data-offline-redirect');
        // petit délai pour laisser voir le message avant la navigation
        setTimeout(function () { if (redirect) window.location.href = redirect; }, 600);
      });
    });
  }

  // ── Démarrage ────────────────────────────────────────────────────────────
  window.addEventListener('DOMContentLoaded', function () {
    interceptForm();
    refreshBanner();
    drain();
  });
  window.addEventListener('online', drain);
})();
