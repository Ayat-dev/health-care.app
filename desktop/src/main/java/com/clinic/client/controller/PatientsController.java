package com.clinic.client.controller;

import com.clinic.client.util.ApiClient;
import com.clinic.client.util.SceneManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class PatientsController extends BaseController {

    @FXML private TextField searchField;
    @FXML private TableView<JSONObject> table;
    @FXML private TableColumn<JSONObject, String> colRecord;
    @FXML private TableColumn<JSONObject, String> colName;
    @FXML private TableColumn<JSONObject, String> colBirth;
    @FXML private TableColumn<JSONObject, String> colPhone;
    @FXML private TableColumn<JSONObject, String> colCity;
    @FXML private Label statusLabel;

    private final ObservableList<JSONObject> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colRecord.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("recordNumber")));
        colName.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().optString("lastName") + " " + c.getValue().optString("firstName")));
        colBirth.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("birthDate")));
        colPhone.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("phone")));
        colCity.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("city")));
        table.setItems(data);

        table.setRowFactory(tv -> {
            TableRow<JSONObject> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openPatient(row.getItem().optLong("id"));
            });
            return row;
        });

        loadPatients("");
    }

    @FXML
    public void search() {
        loadPatients(searchField.getText().trim());
    }

    /** Ouvre le dossier du patient sélectionné (bouton « Ouvrir le dossier »). */
    @FXML
    public void open() {
        JSONObject sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { info("Aucune sélection", "Sélectionnez un patient."); return; }
        openPatient(sel.optLong("id"));
    }

    private void openPatient(long id) {
        try {
            PatientDetailController c = SceneManager.navigateTo("patient-detail.fxml");
            c.load(id);
        } catch (Exception e) {
            error("Ouverture impossible", e.getMessage());
        }
    }

    private void loadPatients(String q) {
        statusLabel.setText("Chargement…");
        async(() -> {
            String path = "/api/patients?size=200";
            if (!q.isEmpty()) path += "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
            ApiClient.Response resp = ApiClient.get(path);
            // /api/patients renvoie une Page : { content:[...], totalElements:N }
            JSONObject page = resp.asObject();
            JSONArray arr = page.optJSONArray("content");
            int total = page.optInt("totalElements", arr != null ? arr.length() : 0);
            ui(() -> {
                data.clear();
                if (resp.ok() && arr != null) {
                    for (int i = 0; i < arr.length(); i++) data.add(arr.getJSONObject(i));
                    statusLabel.setText(total + " patient(s)");
                } else {
                    statusLabel.setText("Erreur de chargement (serveur injoignable ?).");
                }
            });
        });
    }
}
