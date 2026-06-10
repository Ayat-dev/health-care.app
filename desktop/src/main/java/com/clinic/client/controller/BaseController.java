package com.clinic.client.controller;

import com.clinic.client.model.AuthState;
import com.clinic.client.util.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;

import java.io.IOException;

/**
 * Base partagée par tous les écrans principaux : navigation latérale,
 * exécution asynchrone des appels réseau et petites aides UI.
 * Les méthodes de navigation sont publiques pour être résolues depuis les
 * gestionnaires onAction="#..." des FXML, y compris par héritage.
 */
public abstract class BaseController {

    @FXML public void goDashboard()     throws IOException { SceneManager.navigateTo("dashboard.fxml"); }
    @FXML public void goPatients()      throws IOException { SceneManager.navigateTo("patients.fxml"); }
    @FXML public void goAppointments()  throws IOException { SceneManager.navigateTo("appointments.fxml"); }
    @FXML public void goConsultations() throws IOException { SceneManager.navigateTo("consultations.fxml"); }
    @FXML public void goReference()     throws IOException { SceneManager.navigateTo("reference.fxml"); }

    @FXML public void logout() throws IOException {
        AuthState.get().logout();
        SceneManager.navigateTo("login.fxml");
    }

    /** Lance un traitement (typiquement un appel réseau) hors du thread JavaFX. */
    protected void async(Runnable bg) {
        Thread t = new Thread(bg);
        t.setDaemon(true);
        t.start();
    }

    /** Exécute une mise à jour UI sur le thread JavaFX. */
    protected void ui(Runnable fx) {
        Platform.runLater(fx);
    }

    protected void error(String header, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Erreur");
        a.setHeaderText(header);
        a.setContentText(content);
        a.showAndWait();
    }

    protected void info(String header, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Information");
        a.setHeaderText(header);
        a.setContentText(content);
        a.showAndWait();
    }
}
