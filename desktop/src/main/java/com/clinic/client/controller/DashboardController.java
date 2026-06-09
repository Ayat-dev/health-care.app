package com.clinic.client.controller;

import com.clinic.client.model.AuthState;
import com.clinic.client.util.ApiClient;
import com.clinic.client.util.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class DashboardController {

    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;
    @FXML private Label totalPatientsLabel;
    @FXML private Label upcomingLabel;
    @FXML private TextArea logArea;

    @FXML
    public void initialize() {
        AuthState auth = AuthState.get();
        welcomeLabel.setText("Bonjour, " + auth.getFullName());
        roleLabel.setText(auth.getRole());

        loadStats();
    }

    private void loadStats() {
        new Thread(() -> {
            try {
                var patients = ApiClient.get("/api/patients");
                var appointments = ApiClient.get("/api/appointments");
                Platform.runLater(() -> {
                    // Les listes renvoient un array — on compte via _raw si besoin
                    logArea.setText("Connecté en tant que : " + AuthState.get().getUsername()
                        + "\nRôle : " + AuthState.get().getRole()
                        + "\n\nNaviguer via le menu latéral.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> logArea.setText("Erreur lors du chargement des données."));
            }
        }).start();
    }

    @FXML public void goPatients()      throws java.io.IOException { SceneManager.navigateTo("patients.fxml"); }
    @FXML public void goAppointments()  throws java.io.IOException { SceneManager.navigateTo("appointments.fxml"); }
    @FXML public void logout() throws java.io.IOException {
        AuthState.get().logout();
        SceneManager.navigateTo("login.fxml");
    }
}
