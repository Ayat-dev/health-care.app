package com.clinic.client.controller;

import com.clinic.client.util.ApiClient;
import com.clinic.client.util.SceneManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.json.*;

public class PatientsController {

    @FXML private TextField searchField;
    @FXML private TableView<JSONObject> table;
    @FXML private TableColumn<JSONObject, String> colRecord;
    @FXML private TableColumn<JSONObject, String> colName;
    @FXML private TableColumn<JSONObject, String> colBirth;
    @FXML private TableColumn<JSONObject, String> colPhone;
    @FXML private TableColumn<JSONObject, String> colDoctor;
    @FXML private Label statusLabel;

    private final ObservableList<JSONObject> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colRecord.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("recordNumber")));
        colName.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().optString("lastName") + " " + c.getValue().optString("firstName")));
        colBirth.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("birthDate")));
        colPhone.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("phone")));
        colDoctor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("assignedDoctorName")));
        table.setItems(data);
        loadPatients("");
    }

    @FXML
    public void search() {
        loadPatients(searchField.getText().trim());
    }

    @FXML
    public void goBack() throws java.io.IOException { SceneManager.navigateTo("dashboard.fxml"); }

    private void loadPatients(String q) {
        statusLabel.setText("Chargement…");
        new Thread(() -> {
            try {
                String path = "/api/patients" + (q.isEmpty() ? "" : "?q=" + q);
                JSONObject resp = ApiClient.get(path);
                // Si l'API renvoie un tableau JSON, il est dans _raw
                String raw = resp.optString("_raw", "[]");
                JSONArray arr = raw.startsWith("[") ? new JSONArray(raw) : new JSONArray();
                Platform.runLater(() -> {
                    data.clear();
                    for (int i = 0; i < arr.length(); i++) data.add(arr.getJSONObject(i));
                    statusLabel.setText(arr.length() + " patient(s)");
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Erreur de chargement."));
            }
        }).start();
    }
}
