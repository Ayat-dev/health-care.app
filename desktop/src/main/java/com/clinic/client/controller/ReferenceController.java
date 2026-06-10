package com.clinic.client.controller;

import com.clinic.client.util.ApiClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReferenceController extends BaseController {

    @FXML private TableView<JSONObject> deptTable;
    @FXML private TableColumn<JSONObject, String> deptCode, deptName, deptDesc, deptActive;
    @FXML private TableView<JSONObject> actTable;
    @FXML private TableColumn<JSONObject, String> actCode, actName, actDept, actPrice;
    @FXML private TableView<JSONObject> labTable;
    @FXML private TableColumn<JSONObject, String> labCode, labName, labCategory, labPrice;
    @FXML private Label statusLabel;

    private final ObservableList<JSONObject> depts = FXCollections.observableArrayList();
    private final ObservableList<JSONObject> acts  = FXCollections.observableArrayList();
    private final ObservableList<JSONObject> labs  = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        deptCode.setCellValueFactory(c -> str(c.getValue(), "code"));
        deptName.setCellValueFactory(c -> str(c.getValue(), "name"));
        deptDesc.setCellValueFactory(c -> str(c.getValue(), "description"));
        deptActive.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().optBoolean("active") ? "Oui" : "Non"));
        deptTable.setItems(depts);

        actCode.setCellValueFactory(c -> str(c.getValue(), "code"));
        actName.setCellValueFactory(c -> str(c.getValue(), "name"));
        actDept.setCellValueFactory(c -> str(c.getValue(), "departmentName"));
        actPrice.setCellValueFactory(c -> str(c.getValue(), "price"));
        actTable.setItems(acts);

        labCode.setCellValueFactory(c -> str(c.getValue(), "code"));
        labName.setCellValueFactory(c -> str(c.getValue(), "name"));
        labCategory.setCellValueFactory(c -> str(c.getValue(), "category"));
        labPrice.setCellValueFactory(c -> str(c.getValue(), "price"));
        labTable.setItems(labs);

        refresh();
    }

    private SimpleStringProperty str(JSONObject o, String key) {
        return new SimpleStringProperty(o.has(key) && !o.isNull(key) ? o.get(key).toString() : "");
    }

    @FXML
    public void refresh() {
        statusLabel.setText("Chargement…");
        async(() -> {
            int d = fill("/api/departments", depts);
            int a = fill("/api/acts", acts);
            int l = fill("/api/lab-tests", labs);
            Platform.runLater(() -> statusLabel.setText(
                    d + " département(s) · " + a + " acte(s) · " + l + " analyse(s)"));
        });
    }

    /** Charge un endpoint liste et remplit la collection observable. Renvoie le nombre d'éléments. */
    private int fill(String path, ObservableList<JSONObject> target) {
        ApiClient.Response r = ApiClient.get(path);
        JSONArray arr = r.asArray();
        Platform.runLater(() -> {
            target.clear();
            for (int i = 0; i < arr.length(); i++) target.add(arr.getJSONObject(i));
        });
        return arr.length();
    }
}
