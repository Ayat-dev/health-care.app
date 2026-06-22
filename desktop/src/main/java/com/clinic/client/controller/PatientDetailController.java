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

/**
 * Dossier patient (poste de soin). Lecture du dossier clinique + édition du
 * résumé médical. Le volet financier (factures) et les statistiques sont
 * volontairement exclus — réservés à l'admin / l'application web.
 */
public class PatientDetailController extends BaseController {

    @FXML private Label patientNameLabel, recordLabel, statusLabel;

    // Identité (lecture seule)
    @FXML private Label idBirth, idGender, idPhone, idPhoneAlt, idEmail,
            idAddress, idCity, idNationality, idNationalId, idEmergency, idInsurance;

    // Médical (éditable → PUT /api/patients/{id})
    @FXML private TextField bloodType;
    @FXML private TextArea allergies, chronicConditions, medicalHistory, notes;
    @FXML private Label medicalStatus;

    @FXML private TabPane tabs;
    @FXML private Tab maternityTab;

    @FXML private TableView<JSONObject> consultTable, labTable, radioTable, stayTable;
    @FXML private TableColumn<JSONObject, String> cDate, cReason, cDiag, cDoctor, cStatus;
    @FXML private TableColumn<JSONObject, String> lDate, lNum, lPrio, lStatus;
    @FXML private TableColumn<JSONObject, String> iDate, iNum, iPrio, iStatus, iReport;
    @FXML private TableColumn<JSONObject, String> sIn, sOut, sRoom, sReason, sStatus;

    // Maternité (si patiente)
    @FXML private Label matGravidity, matLmp, matEdd, matGestation, matVisits, matStatus;

    private long patientId;
    private JSONObject loaded;   // PatientDto complet (sert de base au PUT pour ne rien écraser)
    private final ObservableList<JSONObject> consults = FXCollections.observableArrayList();
    private final ObservableList<JSONObject> labs = FXCollections.observableArrayList();
    private final ObservableList<JSONObject> radios = FXCollections.observableArrayList();
    private final ObservableList<JSONObject> stays = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        cDate.setCellValueFactory(c -> str(prettyDate(c.getValue().optString("consultationDate"))));
        cReason.setCellValueFactory(c -> str(c.getValue().optString("chiefComplaint")));
        cDiag.setCellValueFactory(c -> str(c.getValue().optString("diagnosis")));
        cDoctor.setCellValueFactory(c -> str(c.getValue().optString("doctorName")));
        cStatus.setCellValueFactory(c -> str(c.getValue().optString("status")));
        consultTable.setItems(consults);
        consultTable.setRowFactory(tv -> {
            TableRow<JSONObject> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openConsultation(row.getItem().optLong("id"));
            });
            return row;
        });

        lDate.setCellValueFactory(c -> str(prettyDate(c.getValue().optString("requestedAt"))));
        lNum.setCellValueFactory(c -> str(c.getValue().optString("requestNumber")));
        lPrio.setCellValueFactory(c -> str(c.getValue().optString("priority")));
        lStatus.setCellValueFactory(c -> str(c.getValue().optString("status")));
        labTable.setItems(labs);

        iDate.setCellValueFactory(c -> str(prettyDate(c.getValue().optString("requestedAt"))));
        iNum.setCellValueFactory(c -> str(c.getValue().optString("requestNumber")));
        iPrio.setCellValueFactory(c -> str(c.getValue().optString("priority")));
        iStatus.setCellValueFactory(c -> str(c.getValue().optString("status")));
        iReport.setCellValueFactory(c -> str(c.getValue().optBoolean("hasReport") ? "Oui" : "—"));
        radioTable.setItems(radios);

        sIn.setCellValueFactory(c -> str(prettyDate(c.getValue().optString("admissionDate"))));
        sOut.setCellValueFactory(c -> str(prettyDate(c.getValue().optString("dischargeDate"))));
        sRoom.setCellValueFactory(c -> str(c.getValue().optString("roomNumber")));
        sReason.setCellValueFactory(c -> str(c.getValue().optString("admissionReason")));
        sStatus.setCellValueFactory(c -> str(c.getValue().optString("status")));
        stayTable.setItems(stays);
    }

    @FXML public void back() {
        try { SceneManager.navigateTo("patients.fxml"); }
        catch (Exception e) { error("Navigation impossible", e.getMessage()); }
    }

    /** Charge le dossier complet (démographie + médical + historiques cliniques). */
    public void load(long id) {
        this.patientId = id;
        statusLabel.setText("Chargement…");
        async(() -> {
            ApiClient.Response pr = ApiClient.get("/api/patients/" + id);
            ApiClient.Response cr = ApiClient.get("/api/consultations?patientId=" + id);
            ApiClient.Response lr = ApiClient.get("/api/lab/requests?patientId=" + id);
            ApiClient.Response rr = ApiClient.get("/api/radiology/requests?patientId=" + id);
            ApiClient.Response hr = ApiClient.get("/api/hospitalizations?patientId=" + id);
            ui(() -> {
                if (!pr.ok()) { statusLabel.setText("Dossier introuvable."); return; }
                loaded = pr.asObject();
                fillIdentity(loaded);
                fillMedical(loaded);
                fillTable(consults, cr);
                fillTable(labs, lr);
                fillTable(radios, rr);
                fillTable(stays, hr);
                statusLabel.setText("");
                // Maternité : onglet réservé aux patientes ; sinon on le retire.
                if ("F".equalsIgnoreCase(loaded.optString("gender"))) {
                    loadMaternity(id);
                } else {
                    tabs.getTabs().remove(maternityTab);
                }
            });
        });
    }

    private void loadMaternity(long id) {
        async(() -> {
            ApiClient.Response mr = ApiClient.get("/api/maternity/by-patient/" + id);
            ui(() -> {
                if (mr.status() == 200) {
                    JSONObject m = mr.asObject();
                    matGravidity.setText("G" + m.optInt("gravidity") + " P" + m.optInt("parity"));
                    matLmp.setText(m.optString("lastPeriodDate", "—"));
                    matEdd.setText(m.optString("expectedDueDate", "—"));
                    matGestation.setText(m.has("currentGestationalAgeWeeks") && !m.isNull("currentGestationalAgeWeeks")
                            ? m.optInt("currentGestationalAgeWeeks") + " SA" : "—");
                    matVisits.setText(String.valueOf(m.optInt("completedVisits")));
                    matStatus.setText(m.optString("status", "—"));
                } else {
                    matStatus.setText("Aucun dossier de maternité.");
                }
            });
        });
    }

    private void fillIdentity(JSONObject p) {
        patientNameLabel.setText(p.optString("lastName") + " " + p.optString("firstName"));
        recordLabel.setText("N° " + p.optString("recordNumber"));
        idBirth.setText(val(p, "birthDate"));
        idGender.setText(val(p, "gender"));
        idPhone.setText(val(p, "phone"));
        idPhoneAlt.setText(val(p, "phoneAlt"));
        idEmail.setText(val(p, "email"));
        idAddress.setText(val(p, "address"));
        idCity.setText(val(p, "city"));
        idNationality.setText(val(p, "nationality"));
        idNationalId.setText(val(p, "nationalId"));
        idInsurance.setText(val(p, "insuranceNumber"));
        String emName = p.optString("emergencyContactName", "");
        String emPhone = p.optString("emergencyContactPhone", "");
        idEmergency.setText((emName + " " + emPhone).trim().isEmpty() ? "—" : (emName + " " + emPhone).trim());
    }

    private void fillMedical(JSONObject p) {
        bloodType.setText(p.optString("bloodType", ""));
        allergies.setText(p.optString("allergies", ""));
        chronicConditions.setText(p.optString("chronicConditions", ""));
        medicalHistory.setText(p.optString("medicalHistory", ""));
        notes.setText(p.optString("notes", ""));
    }

    /** Enregistre uniquement le volet médical, en repartant de l'objet complet
     *  chargé (les autres champs — dont les colonnes PHI chiffrées — sont préservés). */
    @FXML
    public void saveMedical() {
        if (loaded == null) return;
        JSONObject body = new JSONObject(loaded.toString());   // copie de l'objet complet
        body.put("bloodType", bloodType.getText());
        body.put("allergies", allergies.getText());
        body.put("chronicConditions", chronicConditions.getText());
        body.put("medicalHistory", medicalHistory.getText());
        body.put("notes", notes.getText());

        medicalStatus.setText("Enregistrement…");
        async(() -> {
            ApiClient.Response r = ApiClient.put("/api/patients/" + patientId, body);
            ui(() -> {
                if (r.ok()) { loaded = r.asObject(); fillMedical(loaded); medicalStatus.setText("Enregistré."); }
                else error("Enregistrement impossible", "Le serveur a répondu : " + r.status());
            });
        });
    }

    private void openConsultation(long id) {
        try {
            SceneManager.Modal<ConsultationDetailController> m =
                    SceneManager.loadModal("consultation-detail.fxml", "Consultation");
            m.controller().initModal(m.stage());
            m.controller().load(id);
            m.stage().showAndWait();
            // La consultation a pu être clôturée → on rafraîchit la liste du dossier.
            async(() -> {
                ApiClient.Response cr = ApiClient.get("/api/consultations?patientId=" + patientId);
                ui(() -> fillTable(consults, cr));
            });
        } catch (Exception e) {
            error("Ouverture impossible", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private void fillTable(ObservableList<JSONObject> target, ApiClient.Response resp) {
        target.clear();
        if (resp.ok()) {
            JSONArray arr = resp.asArray();
            for (int i = 0; i < arr.length(); i++) target.add(arr.getJSONObject(i));
        }
    }

    private SimpleStringProperty str(String s) { return new SimpleStringProperty(s); }

    private String val(JSONObject o, String key) {
        String v = o.optString(key, "");
        return v == null || v.isEmpty() ? "—" : v;
    }

    /** "2026-06-10T09:30" ou "2026-06-10" → format lisible. */
    private String prettyDate(String iso) {
        if (iso == null || iso.isEmpty()) return "—";
        if (iso.length() >= 16)
            return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4) + " " + iso.substring(11, 16);
        if (iso.length() >= 10)
            return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
        return iso;
    }
}
