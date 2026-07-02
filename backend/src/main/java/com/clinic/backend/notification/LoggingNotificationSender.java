package com.clinic.backend.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev / fallback sender. Handles every channel by logging the message and reporting success —
 * there are no SMS/email provider keys in dev, and IN_APP needs no external delivery (the row
 * itself is the message). Real SMS/email senders, when added, declare a narrower
 * {@link #supports(String)} and are picked ahead of this one.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE) // filet de sécurité : tout sender réel (email/SMS) est choisi avant
@Slf4j
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public boolean supports(String channel) {
        return true; // catch-all
    }

    @Override
    public void send(Notification n) {
        if ("IN_APP".equals(n.getChannel())) {
            log.info("[Notif/IN_APP] → {} : {}", n.getRecipient(), oneLine(n.getBody()));
        } else {
            log.info("[Notif/{} (simulé)] → {} : {}", n.getChannel(), n.getRecipient(), oneLine(n.getBody()));
        }
    }

    private String oneLine(String body) {
        if (body == null) return "";
        String s = body.replaceAll("\\s+", " ").trim();
        return s.length() > 120 ? s.substring(0, 117) + "…" : s;
    }
}
