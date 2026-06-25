package com.clinic.client.controller;

import com.clinic.client.model.AuthState;
import com.clinic.client.util.ApiClient;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Cockpit du propriétaire (P6 — WS4). Le poste bureau n'affiche plus aucune donnée
 * de santé nominative : uniquement des indicateurs de pilotage business agrégés,
 * servis par l'API rapports ({@code /api/reports/dashboard/admin} +
 * {@code /api/reports/monthly-financial}), toutes deux réservées au rôle OWNER.
 */
public class DashboardController extends BaseController {

    private static final NumberFormat MONEY = NumberFormat.getNumberInstance(Locale.FRENCH);
    private static final String CURRENCY = "FCFA";

    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;
    @FXML private Label revenueTodayValue;
    @FXML private Label revenueMonthValue;
    @FXML private Label revenueVariationValue;
    @FXML private Label outstandingValue;
    @FXML private Label consultationsValue;
    @FXML private Label occupancyValue;
    @FXML private Label stockAlertValue;
    @FXML private VBox paymentBreakdownBox;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        AuthState auth = AuthState.get();
        welcomeLabel.setText("Bonjour, " + auth.getFullName());
        roleLabel.setText(AuthState.roleLabel(auth.getRole()));
        refresh();
    }

    @FXML
    public void refresh() {
        statusLabel.setText("Chargement des indicateurs…");
        async(() -> {
            ApiClient.Response dash = ApiClient.get("/api/reports/dashboard/admin");
            ApiClient.Response fin  = ApiClient.get("/api/reports/monthly-financial");

            JSONObject d = dash.ok() ? dash.asObject() : null;
            JSONObject f = fin.ok()  ? fin.asObject()  : null;

            ui(() -> {
                if (d != null) bindDashboard(d);
                if (f != null) bindFinancial(f);

                boolean anyFail = d == null || f == null;
                statusLabel.setText(anyFail
                        ? "Certains indicateurs n'ont pas pu être chargés (serveur injoignable ?)."
                        : "Indicateurs à jour — " + AuthState.get().getUsername());
            });
        });
    }

    private void bindDashboard(JSONObject d) {
        revenueTodayValue.setText(money(d.optDouble("revenueToday", 0)));
        revenueMonthValue.setText(money(d.optDouble("revenueMonth", 0)));

        if (d.isNull("revenueMonthVariationPercent")) {
            revenueVariationValue.setText("");
        } else {
            double v = d.optDouble("revenueMonthVariationPercent", 0);
            boolean up = v >= 0;
            revenueVariationValue.setText((up ? "▲ +" : "▼ ") + trim(v) + " % vs mois précédent");
            revenueVariationValue.setStyle("-fx-font-size:11px; -fx-font-weight:bold; -fx-text-fill:"
                    + (up ? "#16a34a" : "#dc2626") + ";");
        }

        consultationsValue.setText(String.valueOf(d.optLong("consultationsMonth", 0)));

        long occupied = d.optLong("occupiedBeds", 0);
        long total = d.optLong("totalBeds", 0);
        double rate = d.optDouble("bedOccupancyRate", 0);
        occupancyValue.setText(occupied + " / " + total + "  (" + trim(rate) + " %)");

        long low = d.optLong("lowStockCount", 0);
        long expiring = d.optLong("expiringCount", 0);
        stockAlertValue.setText(low + " / " + expiring);

        // Répartition par mode de paiement (peut aussi venir du bilan financier ; on
        // privilégie le bilan dans bindFinancial s'il est disponible).
        if (paymentBreakdownBox.getChildren().isEmpty()) {
            renderBreakdown(d.optJSONObject("paymentMethodBreakdown"));
        }
    }

    private void bindFinancial(JSONObject f) {
        outstandingValue.setText(money(f.optDouble("totalOutstanding", 0)));
        renderBreakdown(f.optJSONObject("collectedByMethod"));
    }

    private void renderBreakdown(JSONObject breakdown) {
        paymentBreakdownBox.getChildren().clear();
        if (breakdown == null || breakdown.isEmpty()) {
            Label empty = new Label("Aucun encaissement ce mois.");
            empty.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:12px;");
            paymentBreakdownBox.getChildren().add(empty);
            return;
        }
        for (String method : breakdown.keySet()) {
            Label name = new Label(prettyMethod(method));
            name.setStyle("-fx-font-size:13px;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label amount = new Label(money(breakdown.optDouble(method, 0)));
            amount.setStyle("-fx-font-size:13px; -fx-font-weight:bold;");
            HBox row = new HBox(8, name, spacer, amount);
            row.setAlignment(Pos.CENTER_LEFT);
            paymentBreakdownBox.getChildren().add(row);
        }
    }

    private static String money(double v) {
        return MONEY.format(Math.round(v)) + " " + CURRENCY;
    }

    /** Nombre sans décimales superflues (ex. 12.0 → "12", 7.5 → "7,5"). */
    private static String trim(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.FRENCH, "%.1f", v);
    }

    private static String prettyMethod(String m) {
        if (m == null) return "—";
        return switch (m) {
            case "ESPECES"     -> "Espèces";
            case "AMANATA"     -> "Amana Ta";
            case "MYNITA"      -> "MyNITA";
            case "ORANGE_MONEY"-> "Orange Money";
            case "WAVE"        -> "Wave";
            case "MTN_MOMO"    -> "MTN MoMo";
            case "CARTE"       -> "Carte";
            case "VIREMENT"    -> "Virement";
            case "ASSURANCE"   -> "Assurance";
            default            -> m;
        };
    }
}
