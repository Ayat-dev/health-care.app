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

    /** Réponse binaire (téléchargement de fichier : PDF d'ordonnance, etc.). */
    public record BinaryResponse(int status, byte[] body) {
        public boolean ok() { return status >= 200 && status < 300; }
    }

    /** Sérialise la rotation du refresh token (cf. {@link #tryRefresh}). */
    private static final Object REFRESH_LOCK = new Object();

    // ── Méthodes pratiques ────────────────────────────────────────────────
    public static Response get(String path)                       { return send("GET", path, null, true); }
    public static Response post(String path, JSONObject body, boolean auth) { return send("POST", path, body, auth); }
    public static Response put(String path, JSONObject body)       { return send("PUT", path, body, true); }
    public static Response patch(String path, JSONObject body)     { return send("PATCH", path, body, true); }

    /**
     * Télécharge une ressource binaire authentifiée (ex: PDF d'ordonnance).
     * Même rotation transparente du token que {@link #send} (cf. {@link #tryRefresh}).
     */
    public static BinaryResponse getBinary(String path) {
        String tokenUsed = AuthState.get().getToken();
        BinaryResponse resp = exchangeBinary(path, tokenUsed);
        if ((resp.status() == 401 || resp.status() == 403)
                && AuthState.get().getRefreshToken() != null
                && accessTokenExpired(tokenUsed)
                && tryRefresh(tokenUsed)) {
            resp = exchangeBinary(path, AuthState.get().getToken());
        }
        return resp;
    }

    /**
     * Téléverse une image (multipart/form-data, champ {@code file}) avec la même
     * rotation transparente du token que {@link #send}. Utilisé pour la photo patient.
     */
    public static Response postImage(String path, java.io.File file) {
        String tokenUsed = AuthState.get().getToken();
        Response resp = exchangeMultipart(path, file, tokenUsed);
        if ((resp.status() == 401 || resp.status() == 403)
                && AuthState.get().getRefreshToken() != null
                && accessTokenExpired(tokenUsed)
                && tryRefresh(tokenUsed)) {
            resp = exchangeMultipart(path, file, AuthState.get().getToken());
        }
        return resp;
    }

    /**
     * Déconnexion côté serveur : révoque le refresh token fourni (best-effort).
     * À appeler avant d'effacer la session locale, sinon le refresh token reste
     * valide 7 jours. Le jeton est passé explicitement (et non lu depuis AuthState)
     * pour éviter toute course avec l'effacement de la session.
     */
    public static void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null) return;
        JSONObject body = new JSONObject();
        body.put("refreshToken", refreshToken);
        exchange("POST", "/api/auth/logout", body, null); // public, ignore le résultat
    }

    private static Response send(String method, String path, JSONObject body, boolean auth) {
        String tokenUsed = auth ? AuthState.get().getToken() : null;
        Response resp = exchange(method, path, body, tokenUsed);

        // P4.4 : l'access token est court (15 min). Quand il expire, le backend rejette
        // la requête — et la chaîne API stateless n'ayant pas d'authenticationEntryPoint
        // dédié, un token périmé tombe en utilisateur anonyme → AccessDenied → **403**
        // (pas 401). On déclenche donc la rotation transparente sur 401 OU 403, mais
        // uniquement si l'access token est réellement expiré (décodage du claim `exp`),
        // afin de ne pas gaspiller une rotation sur un vrai refus d'autorisation (rôle).
        if (auth && (resp.status() == 401 || resp.status() == 403)
                && AuthState.get().getRefreshToken() != null
                && accessTokenExpired(tokenUsed)
                && tryRefresh(tokenUsed)) {
            resp = exchange(method, path, body, AuthState.get().getToken());
        }
        return resp;
    }

    /**
     * L'access token JWT est-il expiré (ou illisible) ? Décode le claim {@code exp}
     * (epoch secondes) du payload. Un token absent/malformé est traité comme expiré :
     * on tente alors le refresh (sans danger — un échec renvoie l'erreur d'origine).
     */
    private static boolean accessTokenExpired(String jwt) {
        if (jwt == null) return true;
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return true;
            String payload = new String(
                    java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JSONObject o = new JSONObject(payload);
            if (!o.has("exp")) return true;
            return System.currentTimeMillis() / 1000L >= o.getLong("exp");
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Rotation transparente de l'access token. Sérialisée par {@link #REFRESH_LOCK} :
     * si un autre thread a déjà rafraîchi pendant l'attente du verrou (le token courant
     * diffère de celui qui a échoué), on ne rejoue PAS le refresh — le refresh token est
     * rotatif et le rejouer déclencherait la détection de réutilisation côté serveur,
     * qui révoquerait toute la session.
     *
     * @return true si un access token valide est désormais disponible.
     */
    private static boolean tryRefresh(String failedToken) {
        synchronized (REFRESH_LOCK) {
            String current = AuthState.get().getToken();
            if (current != null && !current.equals(failedToken)) {
                return true; // un autre thread a déjà rafraîchi
            }
            String refreshToken = AuthState.get().getRefreshToken();
            if (refreshToken == null) return false;

            JSONObject body = new JSONObject();
            body.put("refreshToken", refreshToken);
            Response resp = exchange("POST", "/api/auth/refresh", body, null);
            if (resp.ok()) {
                JSONObject o = resp.asObject();
                AuthState.get().updateTokens(o.getString("accessToken"), o.getString("refreshToken"));
                return true;
            }
            // Refresh refusé (expiré/révoqué/réutilisé) → session terminée côté serveur.
            AuthState.get().logout();
            return false;
        }
    }

    private static Response exchange(String method, String path, JSONObject body, String token) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");

            if (token != null) {
                b.header("Authorization", "Bearer " + token);
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

    /** Envoie un fichier en multipart/form-data (un seul champ {@code file}). */
    private static Response exchangeMultipart(String path, java.io.File file, String token) {
        try {
            String boundary = "----ClinicBoundary" + System.currentTimeMillis();
            String mime = guessImageMime(file.getName());
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            String header = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n"
                    + "Content-Type: " + mime + "\r\n\r\n";
            baos.write(header.getBytes(StandardCharsets.UTF_8));
            baos.write(java.nio.file.Files.readAllBytes(file.toPath()));
            baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()));
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(r.statusCode(), r.body());
        } catch (Exception e) {
            return new Response(-1, null);
        }
    }

    private static String guessImageMime(String filename) {
        String n = filename.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private static BinaryResponse exchangeBinary(String path, String token) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            HttpResponse<byte[]> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new BinaryResponse(r.statusCode(), r.body());
        } catch (Exception e) {
            return new BinaryResponse(-1, null);
        }
    }
}
