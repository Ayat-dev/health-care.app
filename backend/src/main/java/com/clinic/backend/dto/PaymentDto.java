package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
public class PaymentDto {

    private Long id;
    private BigDecimal amount;
    private String method; // ESPECES, CARTE, ORANGE_MONEY, MTN_MOMO, WAVE, VIREMENT, ASSURANCE
    private String reference;
    private LocalDateTime paidAt;
    private String notes;

    // Libellés (lecture)
    private String cashierName;
    private Long invoiceId;
    private String invoiceNumber;
    private String patientName;
}
