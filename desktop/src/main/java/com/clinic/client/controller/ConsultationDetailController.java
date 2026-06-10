package com.clinic.client.controller;

import com.clinic.client.util.ApiClient;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

public class ConsultationDetailController extends BaseController {

    @FXML private Label patientLabel, statusBadge, rxNumberLabel, rxStatusLabel, statusLabel;
    @FXML private Button saveBtn, completeBtn, saveRxBtn;

    @FXML private TextField weightKg, heightCm, temperatureC, bpSystolic, bpDiastolic,
            pulseBpm, spo2Percent, respiratoryRate, chiefComplaint, icd10Codes;
    @FXML private TextArea history, physicalExam, diagnosis, treatmentPlan;

    @FXML private TableView<JSONObject> rxTable;
    @FXML private TableColumn<JSONObject, String> rxDrug, rxDosage, rxFreq, rxDuration, rxQty;
    @FXML private TextField inDrug, inDosage, inFreq, inDuration, inQty;

    private long consultationId;
    private JSONObject loaded;                 // consultation JSON telle que renvoyée par l'API
    private long prescriptionId = 0;           // > 0 si une ordonnance existe déjà → PUT plutôt que POST
    private final ObservableList<JSONObject> rxItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        rxDrug.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("drugName")));
        rxDosage.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("dosage")));
        rxFreq.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("frequency")));
        rxDuration.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optString("duration")));
        rxQty.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().has("quantity") && !c.getValue().isNull("quantity")
                        ? String.valueOf(c.getValue().optInt("quantity")) : ""));
        rxTable.setItems(rxItems);
    }

    /** Appelé après navigation pour charger la consultation et son ordonnance. */
    public void load(long id) {
        this.consultationId = id;
        statusLabel.setText("Chargement…");
        async(() -> {
            ApiClient.Response cr = ApiClient.get("/api/consultations/" + id);
            ApiClient.Response pr = ApiClient.get("/api/consultations/" + id + "/prescription");
            ui(() -> {
                if (cr.ok()) {
                    loaded = cr.asObject();
                    fillForm(loaded);
                } else {
                    statusLabel.setText("Consultation introuvable.");
                }
                // 200 = ordonnance présente ; 204 = aucune
                if (pr.status() == 200) {
                    JSONObject rx = pr.asObject();
                    prescriptionId = rx.optLong("id");
                    rxNumberLabel.setText("N° " + rx.optString("prescriptionNumber"));
                    rxItems.clear();
                    JSONArray items = rx.optJSONArray("items");
                    if (items != null) for (int i = 0; i < items.length(); i++) rxItems.add(items.getJSONObject(i));
                } else {
                    prescriptionId = 0;
                }
            });
        });
    }

    private void fillForm(JSONObject c) {
        patientLabel.setText(c.optString("patientName") + "  ·  " + c.optString("patientRecordNumber"));
        String status = c.optString("status");
        statusBadge.setText(status);

        setText(weightKg, c, "weightKg");       setText(heightCm, c, "heightCm");
        setText(temperatureC, c, "temperatureC"); setText(bpSystolic, c, "bpSystolic");
        setText(bpDiastolic, c, "bpDiastolic");  setText(pulseBpm, c, "pulseBpm");
        setText(spo2Percent, c, "spo2Percent");  setText(respiratoryRate, c, "respiratoryRate");
        setText(chiefComplaint, c, "chiefComplaint"); setText(icd10Codes, c, "icd10Codes");
        history.setText(c.optString("history", ""));
        physicalExam.setText(c.optString("physicalExam", ""));
        diagnosis.setText(c.optString("diagnosis", ""));
        treatmentPlan.setText(c.optString("treatmentPlan", ""));

        // Une consultation clôturée n'est plus modifiable côté client.
        boolean closed = "TERMINE".equals(status) || "ANNULE".equals(status);
        saveBtn.setDisable(closed);
        completeBtn.setDisable(closed);
        statusLabel.setText(closed ? "Consultation " + status.toLowerCase() + " — lecture seule." : "");
    }

    private void setText(TextField f, JSONObject c, String key) {
        f.setText(c.has(key) && !c.isNull(key) ? c.get(key).toString() : "");
    }

    // ── Enregistrer la consultation ───────────────────────────────────────
    @FXML
    public void save() {
        if (loaded == null) return;
        JSONObject body = new JSONObject();
        // Le service exige patient + médecin : on les conserve depuis la consultation chargée.
        body.put("patientId", loaded.optLong("patientId"));
        body.put("doctorId", loaded.optLong("doctorId"));
        if (loaded.has("departmentId") && !loaded.isNull("departmentId")) body.put("departmentId", loaded.optLong("departmentId"));
        if (loaded.has("consultationDate") && !loaded.isNull("consultationDate")) body.put("consultationDate", loaded.optString("consultationDate"));

        putNumber(body, "weightKg", weightKg, false);
        putNumber(body, "heightCm", heightCm, false);
        putNumber(body, "temperatureC", temperatureC, false);
        putNumber(body, "bpSystolic", bpSystolic, true);
        putNumber(body, "bpDiastolic", bpDiastolic, true);
        putNumber(body, "pulseBpm", pulseBpm, true);
        putNumber(body, "spo2Percent", spo2Percent, false);
        putNumber(body, "respiratoryRate", respiratoryRate, true);
        body.put("chiefComplaint", chiefComplaint.getText());
        body.put("history", history.getText());
        body.put("physicalExam", physicalExam.getText());
        body.put("diagnosis", diagnosis.getText());
        body.put("icd10Codes", icd10Codes.getText());
        body.put("treatmentPlan", treatmentPlan.getText());

        statusLabel.setText("Enregistrement…");
        async(() -> {
            ApiClient.Response r = ApiClient.put("/api/consultations/" + consultationId, body);
            ui(() -> {
                if (r.ok()) { loaded = r.asObject(); statusLabel.setText("Consultation enregistrée."); }
                else error("Enregistrement impossible", "Le serveur a répondu : " + r.status());
            });
        });
    }

    // ── Clôturer ──────────────────────────────────────────────────────────
    @FXML
    public void complete() {
        if (diagnosis.getText() == null || diagnosis.getText().isBlank()) {
            error("Diagnostic requis", "Le diagnostic est obligatoire pour clôturer la consultation.");
            return;
        }
        // On enregistre d'abord les saisies, puis on clôture.
        save();
        statusLabel.setText("Clôture…");
        async(() -> {
            ApiClient.Response r = ApiClient.patch("/api/consultations/" + consultationId + "/complete", null);
            ui(() -> {
                if (r.ok()) { fillForm(r.asObject()); info("Consultation clôturée", "La consultation est désormais terminée."); }
                else error("Clôture impossible", "Le serveur a répondu : " + r.status()
                        + (r.status() == 500 ? "\n(Vérifiez que le diagnostic est renseigné.)" : ""));
            });
        });
    }

    // ── Ordonnance ──────────────────────────────────────────────────────────
    @FXML
    public void addRx() {
        String drug = inDrug.getText() == null ? "" : inDrug.getText().trim();
        if (drug.isEmpty()) { rxStatusLabel.setText("Saisissez au moins le nom du médicament."); return; }
        JSONObject it = new JSONObject();
        it.put("drugName", drug);
        it.put("dosage", inDosage.getText());
        it.put("frequency", inFreq.getText());
        it.put("duration", inDuration.getText());
        String qty = inQty.getText();
        if (qty != null && qty.matches("\\d+")) it.put("quantity", Integer.parseInt(qty));
        rxItems.add(it);
        inDrug.clear(); inDosage.clear(); inFreq.clear(); inDuration.clear(); inQty.clear();
        rxStatusLabel.setText(rxItems.size() + " ligne(s).");
    }

    @FXML
    public void saveRx() {
        if (rxItems.isEmpty()) { rxStatusLabel.setText("Ajoutez au moins un médicament."); return; }
        JSONObject body = new JSONObject();
        body.put("issueDate", LocalDate.now().toString());
        body.put("validityDays", 30);
        JSONArray items = new JSONArray();
        for (JSONObject it : rxItems) items.put(it);
        body.put("items", items);

        rxStatusLabel.setText("Enregistrement de l'ordonnance…");
        async(() -> {
            // Édition en place si une ordonnance existe (évite les doublons), sinon création.
            ApiClient.Response r = prescriptionId > 0
                    ? ApiClient.put("/api/prescriptions/" + prescriptionId, body)
                    : ApiClient.post("/api/consultations/" + consultationId + "/prescription", body, true);
            ui(() -> {
                if (r.ok()) {
                    JSONObject rx = r.asObject();
                    prescriptionId = rx.optLong("id");
                    rxNumberLabel.setText("N° " + rx.optString("prescriptionNumber"));
                    rxStatusLabel.setText("Ordonnance enregistrée.");
                } else {
                    error("Ordonnance refusée", "Le serveur a répondu : " + r.status());
                    rxStatusLabel.setText("");
                }
            });
        });
    }

    private void putNumber(JSONObject body, String key, TextField f, boolean integer) {
        String t = f.getText();
        if (t == null || t.isBlank()) return;
        try {
            if (integer) body.put(key, Integer.parseInt(t.trim()));
            else body.put(key, Double.parseDouble(t.trim().replace(',', '.')));
        } catch (NumberFormatException ignored) { /* champ ignoré si non numérique */ }
    }
}
