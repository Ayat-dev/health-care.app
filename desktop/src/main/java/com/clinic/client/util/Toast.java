package com.clinic.client.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Petite notification éphémère (toast) ancrée en haut à droite de la fenêtre principale.
 * Utilisée pour les mises à jour temps réel poussées par le serveur ({@link RealtimeClient}).
 *
 * <p>Charte alignée sur le client web (cf. {@code .wl-toast} dans app.css) : fond sombre
 * (sidebar {@code #0f172a}), liseré bleu accent, point coloré, disparition automatique.
 */
public final class Toast {

    private Toast() {}

    /** Affiche un toast (à appeler depuis le thread JavaFX). Sans fenêtre visible, no-op. */
    public static void show(Stage owner, String text) {
        if (owner == null || !owner.isShowing()) return;

        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 12px;");
        Label label = new Label("🔔  " + text);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

        HBox box = new HBox(8, dot, label);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(12, 18, 12, 16));
        box.setStyle("-fx-background-color: #0f172a; -fx-background-radius: 8;"
                + " -fx-border-color: #2563eb; -fx-border-radius: 8; -fx-border-width: 1;");

        Popup popup = new Popup();
        popup.getContent().add(box);
        popup.setAutoFix(false);
        popup.show(owner);

        // Repositionne une fois la largeur réelle connue (après le premier layout).
        Platform.runLater(() -> {
            popup.setX(owner.getX() + owner.getWidth() - box.getWidth() - 24);
            popup.setY(owner.getY() + 58);
        });

        FadeTransition in = new FadeTransition(Duration.millis(200), box);
        in.setFromValue(0); in.setToValue(1);
        PauseTransition stay = new PauseTransition(Duration.seconds(4.5));
        FadeTransition out = new FadeTransition(Duration.millis(300), box);
        out.setFromValue(1); out.setToValue(0);
        SequentialTransition seq = new SequentialTransition(in, stay, out);
        seq.setOnFinished(e -> popup.hide());
        seq.play();
    }
}
