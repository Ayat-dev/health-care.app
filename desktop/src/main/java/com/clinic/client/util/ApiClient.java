package com.clinic.client.util;

import com.clinic.client.model.AuthState;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Client HTTP léger pour le backend REST. Basé sur java.net.http.HttpClient
 * (supporte GET/POST/PUT/PATCH proprement, contrairement à HttpURLConnection).
 * Aucune exception checked n'est propagée : en cas d'échec réseau, le statut
 * vaut -1 et le corps est null.
 */
public class ApiClient {

    private static String BASE_URL = "http://localhost:8080";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void setBaseUrl(String url) { BASE_URL = url; }

    /** Réponse brute : statut HTTP + corps texte, avec accès pratique JSON. */
    public record Response(int status, String body) {
        public boolean ok() { return status >= 200 && status < 300; }

        public JSONObject asObject() {
            return body != null && body.trim().startsWith("{") ? new JSONObject(body) : new JSONObject();
        }

        public JSONArray asArray() {
            return body != null && body.trim().startsWith("[") ? new JSONArray(body) : new JSONArray();
        }
    }

    // ── Méthodes pratiques ────────────────────────────────────────────────
    public static Response get(String path)                       { return send("GET", path, null, true); }
    public static Response post(String path, JSONObject body, boolean auth) { return send("POST", path, body, auth); }
    public static Response put(String path, JSONObject body)       { return send("PUT", path, body, true); }
    public static Response patch(String path, JSONObject body)     { return send("PATCH", path, body, true); }

    private static Response send(String method, String path, JSONObject body, boolean auth) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");

            if (auth && AuthState.get().getToken() != null) {
                b.header("Authorization", "Bearer " + AuthState.get().getToken());
            }

            HttpRequest.BodyPublisher publisher;
            if (body != null) {
                publisher = HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8);
                b.header("Content-Type", "application/json");
            } else {
                publisher = HttpRequest.BodyPublishers.noBody();
            }
            b.method(method, publisher);

            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(r.statusCode(), r.body());
        } catch (Exception e) {
            return new Response(-1, null);
        }
    }
}
