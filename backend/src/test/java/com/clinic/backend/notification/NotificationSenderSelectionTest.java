package com.clinic.backend.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de sélection des émetteurs de notification : un émetteur réel (email/SMS)
 * est toujours choisi avant le fallback {@link LoggingNotificationSender}, qui reste
 * en dernier recours (@Order LOWEST_PRECEDENCE). Sans émetteur réel pour un canal,
 * on retombe sur le fallback (simulation loggée).
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationSenderSelectionTest {

    /** Faux émetteur EMAIL prioritaire — simule l'ajout d'un vrai sender configuré. */
    @TestConfiguration
    static class Fakes {
        @Bean
        @Order(0)
        NotificationSender fakeEmailSender() {
            return new NotificationSender() {
                @Override public boolean supports(String channel) { return "EMAIL".equals(channel); }
                @Override public void send(Notification n) { /* no-op */ }
            };
        }
    }

    @Autowired List<NotificationSender> senders;

    private NotificationSender pick(String channel) {
        return senders.stream().filter(s -> s.supports(channel)).findFirst().orElseThrow();
    }

    @Test
    void un_sender_reel_est_prefere_au_fallback() {
        // EMAIL a un émetteur réel (le faux, @Order(0)) → il l'emporte sur le Logging.
        assertThat(pick("EMAIL")).isNotInstanceOf(LoggingNotificationSender.class);
    }

    @Test
    void canal_sans_sender_reel_retombe_sur_logging() {
        assertThat(pick("SMS")).isInstanceOf(LoggingNotificationSender.class);
        assertThat(pick("IN_APP")).isInstanceOf(LoggingNotificationSender.class);
    }

    @Test
    void le_fallback_logging_est_toujours_en_dernier() {
        assertThat(senders.get(senders.size() - 1)).isInstanceOf(LoggingNotificationSender.class);
    }
}
