package com.clinic.backend.notification;

import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A single queued outbound message. Upstream modules enqueue one of these (status EN_ATTENTE)
 * via {@link NotificationService}; the scheduler drains the queue and a {@link NotificationSender}
 * dispatches it, stamping the row ENVOYE (or ECHEC + {@code errorMessage}).
 * <p>
 * Channels: SMS / EMAIL go through a sender (a no-op logging sender in dev — no provider keys);
 * IN_APP needs no external send — the row itself is the message, read back by the in-app inbox.
 */
@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Internal recipient (staff). Null when the notification targets a patient. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Patient the notification is about / addressed to (optional). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(nullable = false, length = 30)
    private String type;    // RAPPEL_RDV, RESULTAT_LABO, STOCK_ALERTE, FACTURE_IMPAYEE, SYSTEM

    @Column(nullable = false, length = 10)
    private String channel; // SMS, EMAIL, IN_APP

    @Column(nullable = false, length = 150)
    private String recipient; // téléphone, email, ou nom d'utilisateur

    @Column(length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 20)
    private String status = "EN_ATTENTE"; // EN_ATTENTE, ENVOYE, ECHEC

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
