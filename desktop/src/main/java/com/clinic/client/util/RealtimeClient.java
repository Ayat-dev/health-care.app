package com.clinic.client.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Client temps réel STOMP du poste de soin (P5.1 Lot E).
 *
 * <p>Pendant desktop du client web {@code static/js/worklist-live.js} : un mini-client STOMP sur
 * {@code WebSocket} natif (JDK {@link java.net.http.WebSocket}), <b>sans dépendance externe</b>.
 * Le protocole se limite à CONNECT / SUBSCRIBE / réception de MESSAGE.
 *
 * <p><b>Authentification JWT</b> : le poste de soin est stateless. Le jeton d'accès est présenté
 * dans l'en-tête {@code Authorization: Bearer} de la trame CONNECT (cf.
 * {@code StompAuthChannelInterceptor} côté serveur) ; la poignée de main HTTP {@code /ws} est
 * ouverte. Le topic écouté dépend du rôle (le médecin/l'admin reçoit la file d'imagerie).
 *
 * <p><b>Best-effort</b> : toute panne (serveur injoignable, abonnement refusé) est silencieuse —
 * le poste reste pleinement fonctionnel, on perd seulement le direct. Reconnexion avec backoff.
 * Singleton à durée de vie « session » : démarré après connexion, arrêté à la déconnexion.
 */
public final class RealtimeClient {

    private static final String NUL = "\u0000";
    private static final RealtimeClient INSTANCE = new RealtimeClient();

    public static RealtimeClient get() { return INSTANCE; }

    private final HttpClient http = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "realtime-reconnect");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    private volatile boolean active = false;
    private volatile WebSocket ws;
    private String topic;
    private Consumer<String> onMessage;
    private int retry = 0;

    private RealtimeClient() {}

    /** Topic STOMP pertinent pour ce rôle sur le poste de soin, ou {@code null} si aucun. */
    public static String topicForRole(String role) {
        if (role == null) return null;
        return switch (role) {
            // Le médecin (dont le radiologue) et l'admin suivent la file d'imagerie en direct.
            case "MEDECIN", "ADMIN" -> "/topic/worklist/radiology";
            default -> null; // infirmier & co. : aucune worklist desktop abonnable
        };
    }

    /**
     * Démarre l'écoute temps réel adaptée au rôle. {@code listener} est invoqué (hors thread FX)
     * à chaque mise à jour, avec un libellé court ; à l'appelant de basculer sur le thread UI.
     * No-op si le rôle n'a pas de topic.
     */
    public synchronized void startForRole(String role, Consumer<String> listener) {
        String t = topicForRole(role);
        if (t == null) return;
        this.topic = t;
        this.onMessage = listener;
        this.active = true;
        this.retry = 0;
        connect();
    }

    /** Coupe l'écoute (à la déconnexion). Idempotent. */
    public synchronized void stop() {
        active = false;
        WebSocket s = ws;
        ws = null;
        if (s != null) {
            try { s.sendClose(WebSocket.NORMAL_CLOSURE, "logout"); } catch (Exception ignored) {}
        }
    }

    // ── Connexion ─────────────────────────────────────────────────────────────
    private void connect() {
        if (!active) return;
        String token = ApiClient.freshAccessToken();
        if (token == null) { scheduleReconnect(); return; }
        try {
            http.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .subprotocols("v12.stomp", "v11.stomp", "v10.stomp")
                    .buildAsync(URI.create(ApiClient.wsBaseUrl()), new StompListener(token))
                    .whenComplete((socket, err) -> { if (err != null) scheduleReconnect(); });
        } catch (Exception e) {
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!active) return;
        if (!reconnecting.compareAndSet(false, true)) return; // déjà planifié
        retry++;
        long delay = Math.min(30_000L, 1000L * (1L << Math.min(retry, 5)));
        scheduler.schedule(() -> { reconnecting.set(false); connect(); }, delay, TimeUnit.MILLISECONDS);
    }

    // ── Protocole STOMP minimal ────────────────────────────────────────────────
    private final class StompListener implements WebSocket.Listener {
        private final String token;
        private final StringBuilder partial = new StringBuilder();

        StompListener(String token) { this.token = token; }

        @Override
        public void onOpen(WebSocket webSocket) {
            ws = webSocket;
            webSocket.request(1);
            String connect = "CONNECT\n"
                    + "accept-version:1.2,1.1,1.0\n"
                    + "heart-beat:0,0\n"
                    + "Authorization:Bearer " + token + "\n"
                    + "\n" + NUL;
            webSocket.sendText(connect, true);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                for (String chunk : text.split(NUL)) {
                    if (!chunk.isBlank()) handleFrame(webSocket, chunk);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            scheduleReconnect();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            scheduleReconnect();
            return null;
        }

        private void handleFrame(WebSocket webSocket, String frame) {
            int sep = frame.indexOf("\n\n");
            String head = sep >= 0 ? frame.substring(0, sep) : frame;
            String body = sep >= 0 ? frame.substring(sep + 2) : "";
            String command = head.split("\n", 2)[0].trim();

            switch (command) {
                case "CONNECTED" -> {
                    retry = 0;
                    String subscribe = "SUBSCRIBE\n"
                            + "id:sub-desktop\n"
                            + "destination:" + topic + "\n"
                            + "\n" + NUL;
                    webSocket.sendText(subscribe, true);
                }
                case "MESSAGE" -> {
                    Consumer<String> l = onMessage;
                    if (l != null) {
                        String summary = body.isBlank() ? "Mise à jour" : body.trim();
                        try { l.accept(summary); } catch (Exception ignored) {}
                    }
                }
                case "ERROR" -> {
                    // Abonnement refusé (rôle) ou erreur protocole : on cesse sans bruit ni
                    // reconnexion (réessayer donnerait la même ERROR).
                    active = false;
                }
                default -> { /* RECEIPT, etc. : ignorés */ }
            }
        }
    }
}
