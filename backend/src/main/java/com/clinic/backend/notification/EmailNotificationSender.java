package com.clinic.backend.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Émetteur e-mail réel (SMTP via {@link JavaMailSender}). N'est instancié que si un
 * serveur SMTP est configuré ({@code spring.mail.host}, p. ex. via l'env
 * {@code SPRING_MAIL_HOST}) — sinon le bean n'existe pas et {@link LoggingNotificationSender}
 * (dernier recours) prend le canal EMAIL. Prioritaire ({@code @Order(10)}) sur le fallback.
 */
@Component
@Order(10)
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
@Slf4j
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationSender(
            JavaMailSender mailSender,
            @Value("${app.mail.from:${spring.mail.username:no-reply@clinicapp.local}}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public boolean supports(String channel) {
        return "EMAIL".equals(channel);
    }

    @Override
    public void send(Notification n) {
        String to = n.getRecipient();
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Destinataire e-mail manquant pour la notification #" + n.getId());
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subjectFor(n.getType()));
        msg.setText(n.getBody());
        mailSender.send(msg); // lève MailException en cas d'échec → le service marque ECHEC
        log.info("[Notif/EMAIL] envoyé à {} (type {})", to, n.getType());
    }

    /** Objet lisible dérivé du type métier (les corps sont déjà rédigés à l'enqueue). */
    private String subjectFor(String type) {
        if (type == null) return "ClinicApp";
        return switch (type) {
            case "RAPPEL_RDV" -> "Rappel de rendez-vous";
            case "RESULTAT_LABO" -> "Résultats d'analyses disponibles";
            case "FACTURE_IMPAYEE" -> "Facture en attente de règlement";
            case "STOCK_ALERTE" -> "Alerte de stock";
            default -> "ClinicApp";
        };
    }
}
