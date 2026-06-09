package com.clinic.client;

import com.clinic.client.util.SceneManager;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import java.io.PrintWriter;
import java.io.StringWriter;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("ClinicApp — Bureau");
        stage.setMinWidth(800);
        stage.setMinHeight(520);
        SceneManager.setStage(stage);
        stage.show();

        try {
            SceneManager.navigateTo("login.fxml");
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            TextArea ta = new TextArea(sw.toString());
            ta.setEditable(false);
            ta.setPrefSize(700, 400);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur de chargement");
            alert.setHeaderText("Impossible de charger login.fxml");
            alert.getDialogPane().setExpandableContent(ta);
            alert.getDialogPane().setExpanded(true);
            alert.showAndWait();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
