/*
 * ClinicApp — Service Worker (P3.1, mode hors-ligne / PWA).
 *
 * Objectif : rendre l'application installable et résiliente aux coupures réseau
 * (LAN de clinique africaine) SANS jamais mettre en cache de données médicales.
 *
 * Règles de sécurité (volontairement strictes pour un EHR) :
 *  - Seules les requêtes GET de même origine sont gérées.
 *  - L'« app shell » statique et stable (CSS/JS/icône/manifest) est mis en cache.
 *  - Les pages HTML (potentiellement des PHI), /api, /fhir, /uploads, /h2-console,
 *    /login, /logout ne sont JAMAIS mis en cache → toujours réseau d'abord.
 *  - Hors-ligne sur une navigation → page de repli /offline.html.
 *
 * La file de synchro des écritures hors-ligne (background sync) n'est PAS incluse
 * ici : elle nécessiterait une file IndexedDB + rejouabilité côté serveur.
 */

const CACHE_VERSION = 'clinicapp-shell-v1';

// Ressources « app shell » sûres à précharger (aucune donnée patient).
const SHELL_ASSETS = [
  '/offline.html',
  '/css/app.css',
  '/js/pwa.js',
  '/manifest.webmanifest',
  '/images/icon.svg'
];

// Chemins à NE jamais intercepter / mettre en cache (auth + données dynamiques).
const BYPASS_PREFIXES = ['/api/', '/fhir/', '/uploads/', '/h2-console', '/logout', '/login'];

// ── Installation : précharge l'app shell ─────────────────────────────────────
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_VERSION)
      .then((cache) => cache.addAll(SHELL_ASSETS))
      .then(() => self.skipWaiting())
  );
});

// ── Activation : purge les anciens caches versionnés ─────────────────────────
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys.filter((k) => k.startsWith('clinicapp-shell-') && k !== CACHE_VERSION)
            .map((k) => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

function isShellAsset(url) {
  return url.pathname.startsWith('/css/')
      || url.pathname.startsWith('/js/')
      || url.pathname.startsWith('/images/')
      || url.pathname === '/manifest.webmanifest'
      || url.pathname === '/favicon.ico';
}

function isBypassed(url) {
  return BYPASS_PREFIXES.some((p) => url.pathname.startsWith(p));
}

// ── Interception des requêtes ────────────────────────────────────────────────
self.addEventListener('fetch', (event) => {
  const req = event.request;

  // 1) Uniquement GET, même origine. Le reste passe au réseau natif.
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;

  // 2) Endpoints sensibles (auth/PHI/dynamiques) : réseau strict, zéro cache.
  if (isBypassed(url)) return;

  // 3) Navigations (pages HTML) : réseau d'abord, repli /offline.html hors-ligne.
  //    On ne met PAS les pages en cache (elles peuvent contenir des PHI / être périmées).
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req).catch(() => caches.match('/offline.html'))
    );
    return;
  }

  // 4) App shell statique : stale-while-revalidate (rapide hors-ligne, frais en ligne).
  if (isShellAsset(url)) {
    event.respondWith(
      caches.open(CACHE_VERSION).then((cache) =>
        cache.match(req).then((cached) => {
          const network = fetch(req)
            .then((res) => {
              if (res && res.ok) cache.put(req, res.clone());
              return res;
            })
            .catch(() => cached);
          return cached || network;
        })
      )
    );
    return;
  }

  // 5) Tout le reste : réseau, sans mise en cache.
});
