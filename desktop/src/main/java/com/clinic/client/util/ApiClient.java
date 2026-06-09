package com.clinic.client.util;

import com.clinic.client.model.AuthState;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** Utilitaire HTTP léger pour appeler le backend REST. */
public class ApiClient {

    private static String BASE_URL = "http://localhost:8080";

    public static void setBaseUrl(String url) { BASE_URL = url; }

    // GET avec JWT
    public static JSONObject get(String path) throws IOException {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + AuthState.get().getToken());
        conn.setRequestProperty("Accept", "application/json");
        return readResponse(conn);
    }

    // POST avec corps JSON
    public static JSONObject post(String path, JSONObject body) throws IOException {
        return send("POST", path, body, false);
    }

    // POST authentifié
    public static JSONObject postAuth(String path, JSONObject body) throws IOException {
        return send("POST", path, body, true);
    }

    // PUT authentifié
    public static JSONObject put(String path, JSONObject body) throws IOException {
        return send("PUT", path, body, true);
    }

    private static JSONObject send(String method, String path, JSONObject body, boolean withAuth) throws IOException {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        if (withAuth) conn.setRequestProperty("Authorization", "Bearer " + AuthState.get().getToken());
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(conn);
    }

    private static JSONObject readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) return new JSONObject().put("_status", code);
        Scanner sc = new Scanner(is, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        while (sc.hasNextLine()) sb.append(sc.nextLine());
        sc.close();
        String raw = sb.toString().trim();
        JSONObject result = raw.startsWith("{") ? new JSONObject(raw) : new JSONObject().put("_raw", raw);
        result.put("_status", code);
        return result;
    }
}
