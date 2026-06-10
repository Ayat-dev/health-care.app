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

import java.time.LocalDate;

public class AppointmentsController extends BaseController {

    @FXML private DatePicker datePicker;
    @FXML private TableView<JSONObject> table;
    @FXML private TableColumn<JSONObject, String> colTime;
    @FXML private TableColumn<JSONObject, String> colPatient;
    @FXML private TableColumn<JSONObject, String> colDoctor;
    @FXML private TableColumn<JSONObject, String> colType;
    @FXML private TableColumn<JSONObject, String> colReason;
    @FXML private TableColumn<JSONObject, String> colStatus;
    @FXML private Label statusLabel;

    private final ObservableList<JSONObject> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colTime.setCellValueFactory(c -> new SimpleStringProperty(
                hhmm(c.getValue().optString("startTime")) + " – " + hhmm(c.getValue().optString("endTime"))));
        colPatient.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("patientName")));
        colDoctor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("doctorName")));
        colType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("type")));
        colReason.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("reason")));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("status")));
        table.setItems(data);

        datePicker.setValue(LocalDate.now());
        load();
    }

    /** "2026-06-10T09:00" → "09:00" */
    private String hhmm(String iso) {
        return iso != null && iso.length() >= 16 ? iso.substring(11, 16) : "";
    }

    @FXML public void onDateChanged() { load(); }
    @FXML public void refresh()       { load(); }
    @FXML public void today()         { datePicker.setValue(LocalDate.now()); load(); }
    @FXML public void prevDay()       { datePicker.setValue(datePicker.getValue().minusDays(1)); load(); }
    @FXML public void nextDay()       { datePicker.setValue(datePicker.getValue().plusDays(1)); load(); }

    private void load() {
        LocalDate d = datePicker.getValue();
        if (d == null) return;
        statusLabel.setText("Chargement…");
        async(() -> {
            ApiClient.Response resp = ApiClient.get("/api/appointments?date=" + d);
            JSONArray arr = resp.asArray();
            ui(() -> {
                data.clear();
                if (resp.ok()) {
                    for (int i = 0; i < arr.length(); i++) data.add(arr.getJSONObject(i));
                    statusLabel.setText(arr.length() + " rendez-vous le " + d);
                } else {
                    statusLabel.setText("Erreur de chargement (serveur injoignable ?).");
                }
            });
        });
    }

    private JSONObject selected() {
        JSONObject sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) info("Aucune sélection", "Sélectionnez d'abord un rendez-vous dans la liste.");
        return sel;
    }

    @FXML public void confirm() { patchAction("confirm"); }
    @FXML public void start()   { patchAction("start"); }
    @FXML public void complete(){ patchAction("complete"); }
    @FXML public void cancel()  { patchAction("cancel"); }

    private void patchAction(String action) {
        JSONObject sel = selected();
        if (sel == null) return;
        long id = sel.optLong("id");
        statusLabel.setText("Mise à jour…");
        async(() -> {
            ApiClient.Response r = ApiClient.patch("/api/appointments/" + id + "/" + action, null);
            ui(() -> {
                if (r.ok()) load();
                else error("Action impossible", "Le serveur a répondu : " + r.status());
            });
        });
    }

    /** Crée une consultation à partir du RDV sélectionné puis ouvre sa fiche. */
    @FXML
    public void startConsultation() {
        JSONObject sel = selected();
        if (sel == null) return;
        statusLabel.setText("Création de la consultation…");
        JSONObject body = new JSONObject();
        body.put("appointmentId", sel.optLong("id"));
        body.put("patientId", sel.optLong("patientId"));
        body.put("doctorId", sel.optLong("doctorId"));
        if (sel.has("departmentId") && !sel.isNull("departmentId")) body.put("departmentId", sel.optLong("departmentId"));
        body.put("chiefComplaint", sel.optString("reason", ""));
        body.put("status", "EN_COURS");

        async(() -> {
            ApiClient.Response r = ApiClient.post("/api/consultations", body, true);
            ui(() -> {
                if (r.ok()) {
                    long cid = r.asObject().optLong("id");
                    try {
                        ConsultationDetailController c = SceneManager.navigateTo("consultation-detail.fxml");
                        c.load(cid);
                    } catch (Exception e) {
                        error("Navigation impossible", e.getMessage());
                    }
                } else {
                    statusLabel.setText("");
                    error("Création impossible", "Le serveur a répondu : " + r.status());
                }
            });
        });
    }
}
