package com.clinic.backend.notification;

/**
 * Strategy for actually delivering a notification on a given channel. The dev build ships a
 * single {@link LoggingNotificationSender} (no-op: logs + succeeds), since there are no provider
 * keys. Real integrations (Africa's Talking SMS, Spring Mail) would each implement this and
 * declare {@link #supports(String)} for their channel — {@link NotificationService} picks the
 * first sender that supports the row's channel.
 */
public interface NotificationSender {

    /** True if this sender can deliver the given channel (SMS, EMAIL, IN_APP). */
    boolean supports(String channel);

    /**
     * Deliver the notification. Implementations should throw to signal failure — the service
     * catches it and marks the row ECHEC with the exception message.
     */
    void send(Notification notification) throws Exception;
}
