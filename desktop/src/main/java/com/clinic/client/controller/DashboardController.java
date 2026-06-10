package com.clinic.client.controller;

import com.clinic.client.model.AuthState;
import com.clinic.client.util.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.time.LocalDate;

public class DashboardController extends BaseController {

    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;
    @FXML private Label patientsValue;
    @FXML private Label appointmentsValue;
    @FXML private Label consultationsValue;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        AuthState auth = AuthState.get();
        welcomeLabel.setText("Bonjour, " + auth.getFullName());
        roleLabel.setText(AuthState.roleLabel(auth.getRole()));
        loadStats();
    }

    private void loadStats() {
        statusLabel.setText("Chargement des indicateurs…");
        async(() -> {
            String today = LocalDate.now().toString();

            ApiClient.Response patients = ApiClient.get("/api/patients?size=1");
            ApiClient.Response appts    = ApiClient.get("/api/appointments?date=" + today);
            ApiClient.Response consults = ApiClient.get("/api/consultations?status=EN_COURS");

            Integer pTotal = patients.ok() ? patients.asObject().optInt("totalElements", 0) : null;
            Integer aTotal = appts.ok()    ? appts.asArray().length()    : null;
            Integer cTotal = consults.ok() ? consults.asArray().length() : null;

            ui(() -> {
                patientsValue.setText(pTotal != null ? String.valueOf(pTotal) : "—");
                appointmentsValue.setText(aTotal != null ? String.valueOf(aTotal) : "—");
                consultationsValue.setText(cTotal != null ? String.valueOf(cTotal) : "—");
                boolean anyFail = pTotal == null || aTotal == null || cTotal == null;
                statusLabel.setText(anyFail
                        ? "Certains indicateurs n'ont pas pu être chargés (serveur injoignable ?)."
                        : "Indicateurs à jour — " + AuthState.get().getUsername());
            });
        });
    }
}
