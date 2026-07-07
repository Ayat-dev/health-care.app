package com.clinic.backend.desktop;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

/**
 * Ouvre l'interface dans le navigateur par défaut au démarrage du poste desktop.
 * <p>
 * En mode tout-en-un, l'application <b>est</b> le serveur : une fois prête, on ouvre
 * {@code http://localhost:<port>/} pour offrir un lancement « en un clic » (le launcher
 * jpackage démarre l'app, cette classe amène l'utilisateur sur l'écran de connexion /
 * l'assistant {@code /setup}). Désactivable par {@code app.desktop.open-browser=false}
 * (postes serveurs sans session graphique, ou accès uniquement via le LAN).
 */
@Component
@Profile("desktop")
@Slf4j
public class DesktopBrowserOpener {

    @Value("${server.port:8080}")
    private int port;

    @Value("${app.desktop.open-browser:true}")
    private boolean openBrowser;

    @EventListener(ApplicationReadyEvent.class)
    public void openOnStartup() {
        if (!openBrowser) {
            return;
        }
        String url = "http://localhost:" + port + "/";
        // 1) API AWT Desktop (poste avec session graphique).
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                log.info("Interface ouverte dans le navigateur : {}", url);
                return;
            }
        } catch (Exception e) {
            log.debug("Ouverture via Desktop API impossible : {}", e.getMessage());
        }
        // 2) Repli Windows : rundll32 (aucune dépendance graphique AWT requise).
        try {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            log.info("Interface ouverte dans le navigateur (rundll32) : {}", url);
        } catch (Exception e) {
            log.warn("Impossible d'ouvrir automatiquement le navigateur. Ouvrez manuellement {}", url);
        }
    }
}
