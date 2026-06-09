package com.clinic.client.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class SceneManager {
    private static Stage primaryStage;

    public static void setStage(Stage stage) { primaryStage = stage; }

    public static void navigateTo(String fxml) throws IOException {
        URL url = SceneManager.class.getResource("/fxml/" + fxml);
        if (url == null) {
            throw new IOException("FXML introuvable dans le classpath : /fxml/" + fxml);
        }
        FXMLLoader loader = new FXMLLoader(url);
        Pane root = loader.load();
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.sizeToScene();
    }
}
