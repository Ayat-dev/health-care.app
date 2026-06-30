/*
 * PWA — service worker + invite d'installation (P3.1 + B2).
 * Inclus dans base.html, portal/layout.html et login.html.
 * Sans effet sur les navigateurs sans support (dégradation gracieuse).
 */
(function () {
  // ── Enregistrement du service worker (P3.1, mode hors-ligne) ──────────────
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () {
      navigator.serviceWorker.register('/sw.js').catch(function (err) {
        // Échec non bloquant : l'app reste pleinement fonctionnelle en ligne.
        console.warn('ClinicApp PWA : enregistrement du service worker échoué —', err);
      });
    });
  }

  // ── Invite d'installation personnalisée (B2, beforeinstallprompt) ─────────
  // Le navigateur n'émet l'événement que si l'app est installable et pas déjà
  // installée. On le capture, on affiche un bouton discret (présent dans le
  // chrome quand il existe), et on déclenche prompt() au clic. Aucun bouton sur
  // les pages sans chrome (login) → simple no-op.
  var deferredPrompt = null;

  function installBtn() {
    return document.getElementById('pwa-install-btn');
  }

  function hideBtn() {
    var btn = installBtn();
    if (btn) btn.hidden = true;
  }

  // Déjà lancée en mode autonome → ne jamais proposer l'installation.
  function isStandalone() {
    return (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches) ||
           window.navigator.standalone === true;
  }

  window.addEventListener('beforeinstallprompt', function (e) {
    // Empêche la mini-infobar par défaut de Chrome ; on pilote notre propre UI.
    e.preventDefault();
    if (isStandalone()) return;
    deferredPrompt = e;

    var btn = installBtn();
    if (!btn) return; // page sans chrome (ex. login) : rien à afficher
    btn.hidden = false;

    btn.addEventListener('click', function onClick() {
      if (!deferredPrompt) return;
      deferredPrompt.prompt();
      deferredPrompt.userChoice.finally(function () {
        // Quelle que soit l'issue, l'événement n'est rejouable qu'une fois.
        deferredPrompt = null;
        hideBtn();
      });
    }, { once: true });
  });

  // Installation effectuée (depuis notre bouton ou le menu navigateur) → masquer.
  window.addEventListener('appinstalled', function () {
    var btn = installBtn();
    if (btn) {
      var msg = btn.getAttribute('data-installed');
      if (msg) console.info('ClinicApp PWA :', msg);
    }
    deferredPrompt = null;
    hideBtn();
  });
})();
