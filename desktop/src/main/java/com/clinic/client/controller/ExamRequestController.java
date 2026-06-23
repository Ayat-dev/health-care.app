package com.clinic.client.controller;

import com.clinic.client.util.ApiClient;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Demande d'examen (laboratoire ou imagerie) déclenchée depuis le dossier patient.
 * Le médecin choisit le type, coche un ou plusieurs actes du catalogue, fixe la
 * priorité, puis envoie. La demande est rattachée au patient et au prescripteur
 * courant (pas de consultation requise — demande directe depuis le dossier).
 */
public class ExamRequestController extends BaseController {

    @FXML private ComboBox<String> examType;
    @FXML private ComboBox<String> priority;
    @FXML private ListView<CatalogItem> catalogList;
    @FXML private TextArea notes;
    @FXML private Label statusLabel;
    @FXML private Button submitBtn;

    private long patientId;
    private long doctorId;
    private Long consultationId;          // optionnel — null pour une demande directe
    private Stage dialogStage;
    private boolean created = false;

    private final ObservableList<CatalogItem> items = FXCollections.observableArrayList();

    /** Item de catalogue cochable (analyse ou examen). */
    public static class CatalogItem {
        final long id;
        final String label;
        final BooleanProperty selected = new SimpleBooleanProperty(false);
        CatalogItem(long id, String label) { this.id = id; this.label = label; }
        public BooleanProperty selectedProperty() { return selected; }
    }

    public void initModal(Stage stage) { this.dialogStage = stage; }
    public boolean isCreated() { return created; }

    @FXML
    public void initialize() {
        examType.setItems(FXCollections.observableArrayList("Laboratoire", "Imagerie"));
        examType.getSelectionModel().select("Laboratoire");
        priority.setItems(FXCollections.observableArrayList("NORMAL", "URGENT"));
        priority.getSelectionModel().select("NORMAL");

        catalogList.setItems(items);
        catalogList.setCellFactory(CheckBoxListCell.forListView(
                CatalogItem::selectedProperty,
                new StringConverter<>() {
                    @Override public String toString(CatalogItem c) { return c == null ? "" : c.label; }
                    @Override public CatalogItem fromString(String s) { return null; }
                }));

        examType.valueProperty().addListener((obs, old, val) -> loadCatalog());
    }

    /** Initialise la demande pour un patient + prescripteur donnés, puis charge le catalogue. */
    public void init(long patientId, long doctorId, Long consultationId) {
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.consultationId = consultationId;
        loadCatalog();
    }

    @FXML
    public void cancel() { if (dialogStage != null) dialogStage.close(); }

    private boolean isLab() { return "Laboratoire".equals(examType.getValue()); }

    private void loadCatalog() {
        items.clear();
        statusLabel.setText("Chargement du catalogue…");
        String path = isLab() ? "/api/lab/catalog" : "/api/radiology/catalog";
        async(() -> {
            ApiClient.Response r = ApiClient.get(path);
            ui(() -> {
                if (r.ok()) {
                    JSONArray arr = r.asArray();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        items.add(new CatalogItem(o.optLong("id"), o.optString("name")));
                    }
                    statusLabel.setText(items.size() + " acte(s) disponible(s).");
                } else {
                    statusLabel.setText("Catalogue indisponible (code " + r.status() + ").");
                }
            });
        });
    }

    @FXML
    public void submit() {
        JSONArray ids = new JSONArray();
        for (CatalogItem c : items) if (c.selected.get()) ids.put(c.id);
        if (ids.isEmpty()) {
            statusLabel.setText("Sélectionnez au moins un acte.");
            return;
        }

        boolean lab = isLab();
        JSONObject body = new JSONObject();
        body.put("patientId", patientId);
        body.put("doctorId", doctorId);
        if (consultationId != null) body.put("consultationId", consultationId);
        body.put("priority", priority.getValue());
        String txt = notes.getText();
        if (lab) {
            body.put("testIds", ids);
            if (txt != null && !txt.isBlank()) body.put("notes", txt);
        } else {
            body.put("examIds", ids);
            if (txt != null && !txt.isBlank()) body.put("clinicalInfo", txt);
        }

        String path = lab ? "/api/lab/requests" : "/api/radiology/requests";
        submitBtn.setDisable(true);
        statusLabel.setText("Envoi de la demande…");
        async(() -> {
            ApiClient.Response r = ApiClient.post(path, body, true);
            ui(() -> {
                if (r.ok()) {
                    created = true;
                    info("Demande enregistrée",
                            "La demande d'" + (lab ? "analyses" : "imagerie") + " a été créée.");
                    cancel();
                } else {
                    submitBtn.setDisable(false);
                    error("Demande refusée", "Le serveur a répondu : " + r.status()
                            + (r.status() == 403 ? "\n(Seuls les médecins peuvent prescrire un examen.)" : ""));
                    statusLabel.setText("");
                }
            });
        });
    }
}
