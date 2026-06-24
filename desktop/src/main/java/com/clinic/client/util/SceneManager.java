package com.clinic.client.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class SceneManager {
    private static Stage primaryStage;

    /** Vrai une fois que la fenêtre a été dimensionnée pour l'app (après connexion).
     *  Repassé à faux sur l'écran de connexion pour réajuster à la reconnexion. */
    private static boolean appWindowInitialized = false;

    public static void setStage(Stage stage) { primaryStage = stage; }

    /** Fenêtre principale (pour ancrer un toast/notification temps réel). */
    public static Stage getStage() { return primaryStage; }

    /** Fenêtre modale chargée : le {@link Stage} (à afficher via {@code showAndWait})
     *  et son contrôleur (pour lui passer des données / le stage avant affichage). */
    public record Modal<T>(Stage stage, T controller) {}

    /**
     * Charge un FXML dans une nouvelle fenêtre modale (bloque la fenêtre principale).
     * L'appelant configure le contrôleur puis appelle {@code modal.stage().showAndWait()}.
     */
    public static <T> Modal<T> loadModal(String fxml, String title) throws IOException {
        URL url = SceneManager.class.getResource("/fxml/" + fxml);
        if (url == null) {
            throw new IOException("FXML introuvable dans le classpath : /fxml/" + fxml);
        }
        FXMLLoader loader = new FXMLLoader(url);
        Pane root = loader.load();
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (primaryStage != null) dialog.initOwner(primaryStage);
        dialog.setTitle(title);
        dialog.setScene(new Scene(root));
        return new Modal<>(dialog, loader.getController());
    }

    /**
     * Charge un FXML, l'affiche, et renvoie son contrôleur pour permettre à
     * l'appelant de lui passer des données (ex: un id) après navigation.
     */
    public static <T> T navigateTo(String fxml) throws IOException {
        URL url = SceneManager.class.getResource("/fxml/" + fxml);
        if (url == null) {
            throw new IOException("FXML introuvable dans le classpath : /fxml/" + fxml);
        }
        FXMLLoader loader = new FXMLLoader(url);
        Pane root = loader.load();

        boolean isLogin = fxml.equals("login.fxml");

        // On réutilise une seule Scene pour toute la durée de vie de l'app : on
        // remplace seulement sa racine. C'est ce qui préserve nativement la taille,
        // la position et l'état maximisé de la fenêtre d'une vue à l'autre — au lieu
        // d'en recréer une (qui réinitialisait la fenêtre à chaque navigation).
        Scene scene = primaryStage.getScene();
        if (scene == null) {
            primaryStage.setScene(new Scene(root));
        } else {
            scene.setRoot(root);
        }

        if (isLogin) {
            // Écran de connexion : fenêtre compacte recentrée. On réarme le drapeau
            // pour que la prochaine entrée dans l'app réajuste la fenêtre.
            appWindowInitialized = false;
            primaryStage.setMaximized(false);
            primaryStage.sizeToScene();
            primaryStage.centerOnScreen();
        } else if (!appWindowInitialized) {
            // Première vue applicative (juste après la connexion) : on agrandit la
            // fenêtre une seule fois. Ensuite on ne touche plus à sa géométrie, ce
            // qui respecte tout redimensionnement / maximisation fait par l'utilisateur.
            appWindowInitialized = true;
            primaryStage.setMaximized(true);
        }
        return loader.getController();
    }
}
