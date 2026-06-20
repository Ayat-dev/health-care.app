/*
 * PWA — enregistrement du service worker (P3.1, mode hors-ligne).
 * Inclus dans base.html, portal/layout.html et login.html.
 * Sans effet sur les navigateurs sans support (dégradation gracieuse).
 */
(function () {
  if (!('serviceWorker' in navigator)) return;

  window.addEventListener('load', function () {
    navigator.serviceWorker.register('/sw.js').catch(function (err) {
      // Échec non bloquant : l'app reste pleinement fonctionnelle en ligne.
      console.warn('ClinicApp PWA : enregistrement du service worker échoué —', err);
    });
  });
})();
