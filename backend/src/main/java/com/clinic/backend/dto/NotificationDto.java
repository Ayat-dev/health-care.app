package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class NotificationDto {

    private Long id;
    private String type;      // RAPPEL_RDV, RESULTAT_LABO, STOCK_ALERTE, FACTURE_IMPAYEE, SYSTEM
    private String channel;   // SMS, EMAIL, IN_APP
    private String recipient;
    private String subject;
    private String body;
    private String status;    // EN_ATTENTE, ENVOYE, ECHEC
    private LocalDateTime readAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime sentAt;
    private String errorMessage;
    private LocalDateTime createdAt;

    // Libellés (lecture)
    private Long userId;
    private String userName;
    private Long patientId;
    private String patientName;

    public boolean isRead() { return readAt != null; }
}
