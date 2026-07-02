package com.clinic.backend.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Émetteur SMS réel via l'API HTTP d'Africa's Talking. N'est instancié que si une clé
 * API est fournie ({@code app.sms.africastalking.api-key}, p. ex. via l'env
 * {@code APP_SMS_AFRICASTALKING_API_KEY}) — sinon le bean n'existe pas et le canal SMS
 * retombe sur {@link LoggingNotificationSender} (simulation loggée). Prioritaire sur le fallback.
 *
 * <p>Une réponse HTTP non-2xx fait lever {@code RestClient} (via {@code retrieve()}),
 * ce qui propage l'échec à {@link NotificationService} → la ligne passe en ECHEC.
 */
@Component
@Order(10)
@ConditionalOnProperty(prefix = "app.sms.africastalking", name = "api-key")
@Slf4j
public class AfricasTalkingSmsSender implements NotificationSender {

    private final RestClient http;
    private final String username;
    private final String senderId;

    public AfricasTalkingSmsSender(
            @Value("${app.sms.africastalking.api-key}") String apiKey,
            @Value("${app.sms.africastalking.username:sandbox}") String username,
            @Value("${app.sms.africastalking.base-url:https://api.africastalking.com}") String baseUrl,
            @Value("${app.sms.africastalking.sender-id:}") String senderId) {
        this.username = username;
        this.senderId = senderId;
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("apiKey", apiKey)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public boolean supports(String channel) {
        return "SMS".equals(channel);
    }

    @Override
    public void send(Notification n) {
        String to = n.getRecipient();
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Numéro SMS manquant pour la notification #" + n.getId());
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("to", to);
        form.add("message", n.getBody());
        if (senderId != null && !senderId.isBlank()) {
            form.add("from", senderId);
        }

        http.post()
                .uri("/version1/messaging")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity(); // 4xx/5xx → exception → notification marquée ECHEC
        log.info("[Notif/SMS] envoyé à {} (type {})", to, n.getType());
    }
}
