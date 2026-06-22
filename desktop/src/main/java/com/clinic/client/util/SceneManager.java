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

    public static void setStage(Stage stage) { primaryStage = stage; }

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
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.sizeToScene();
        return loader.getController();
    }
}
