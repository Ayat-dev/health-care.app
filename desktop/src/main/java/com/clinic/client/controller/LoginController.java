package com.clinic.client.controller;

import com.clinic.client.model.AuthState;
import com.clinic.client.util.ApiClient;
import com.clinic.client.util.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.json.JSONObject;

public class LoginController {

    @FXML private TextField username;
    @FXML private PasswordField password;
    @FXML private Button loginBtn;
    @FXML private Label message;
    @FXML private ProgressIndicator spinner;

    @FXML
    public void initialize() {
        loginBtn.setOnAction(e -> doLogin());
        // Entrée sur le champ mot de passe → connexion
        password.setOnAction(e -> doLogin());
    }

    private void doLogin() {
        String user = username.getText().trim();
        String pass = password.getText();
        if (user.isEmpty() || pass.isEmpty()) {
            message.setText("Veuillez remplir tous les champs.");
            return;
        }
        setLoading(true);

        new Thread(() -> {
            JSONObject body = new JSONObject();
            body.put("username", user);
            body.put("password", pass);
            ApiClient.Response resp = ApiClient.post("/api/auth/login", body, false);

            Platform.runLater(() -> {
                setLoading(false);
                int status = resp.status();
                if (status == 200) {
                    JSONObject o = resp.asObject();
                    String role = o.getString("role");
                    if (!AuthState.isDesktopRole(role)) {
                        // Compte valide mais hors cible : ce poste est réservé aux soignants.
                        message.setText("Ce poste est réservé au personnel soignant "
                                + "(médecins, infirmiers). Votre profil « " + AuthState.roleLabel(role)
                                + " » s'utilise depuis l'application web.");
                        return;
                    }
                    AuthState.get().login(
                        o.getString("token"),
                        o.getString("username"),
                        role,
                        o.getString("fullName")
                    );
                    try { SceneManager.navigateTo("dashboard.fxml"); } catch (java.io.IOException ex) { message.setText("Erreur navigation : " + ex.getMessage()); }
                } else if (status == 401 || status == 403) {
                    message.setText("Identifiants incorrects.");
                } else if (status == -1) {
                    message.setText("Serveur inaccessible. Vérifiez la connexion.");
                } else {
                    message.setText("Impossible de joindre le serveur (code " + status + ").");
                }
            });
        }).start();
    }

    private void setLoading(boolean loading) {
        loginBtn.setDisable(loading);
        spinner.setVisible(loading);
        if (loading) message.setText("");
    }
}
