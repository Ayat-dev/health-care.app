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

public class ConsultationsController extends BaseController {

    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<JSONObject> table;
    @FXML private TableColumn<JSONObject, String> colDate;
    @FXML private TableColumn<JSONObject, String> colPatient;
    @FXML private TableColumn<JSONObject, String> colDoctor;
    @FXML private TableColumn<JSONObject, String> colReason;
    @FXML private TableColumn<JSONObject, String> colDiag;
    @FXML private TableColumn<JSONObject, String> colStatus;
    @FXML private Label statusLabel;

    private final ObservableList<JSONObject> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        statusFilter.setItems(FXCollections.observableArrayList("Tous", "EN_COURS", "TERMINE", "ANNULE"));
        statusFilter.getSelectionModel().select("Tous");

        colDate.setCellValueFactory(c -> new SimpleStringProperty(prettyDate(c.getValue().optString("consultationDate"))));
        colPatient.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("patientName")));
        colDoctor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("doctorName")));
        colReason.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("chiefComplaint")));
        colDiag.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("diagnosis")));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("status")));
        table.setItems(data);

        table.setRowFactory(tv -> {
            TableRow<JSONObject> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openConsultation(row.getItem().optLong("id"));
            });
            return row;
        });

        load();
    }

    /** "2026-06-10T09:30" → "10/06/2026 09:30" */
    private String prettyDate(String iso) {
        if (iso == null || iso.length() < 16) return iso;
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4) + " " + iso.substring(11, 16);
    }

    @FXML public void refresh() { load(); }

    private void load() {
        String sel = statusFilter.getValue();
        String query = (sel == null || sel.equals("Tous")) ? "" : "?status=" + sel;
        statusLabel.setText("Chargement…");
        async(() -> {
            ApiClient.Response resp = ApiClient.get("/api/consultations" + query);
            JSONArray arr = resp.asArray();
            ui(() -> {
                data.clear();
                if (resp.ok()) {
                    for (int i = 0; i < arr.length(); i++) data.add(arr.getJSONObject(i));
                    statusLabel.setText(arr.length() + " consultation(s)");
                } else {
                    statusLabel.setText("Erreur de chargement (serveur injoignable ?).");
                }
            });
        });
    }

    @FXML
    public void open() {
        JSONObject sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { info("Aucune sélection", "Sélectionnez une consultation."); return; }
        openConsultation(sel.optLong("id"));
    }

    private void openConsultation(long id) {
        try {
            ConsultationDetailController c = SceneManager.navigateTo("consultation-detail.fxml");
            c.load(id);
        } catch (Exception e) {
            error("Navigation impossible", e.getMessage());
        }
    }
}
