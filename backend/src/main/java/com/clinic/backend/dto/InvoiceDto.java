package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class InvoiceDto {

    private Long id;
    private String invoiceNumber;
    private Long patientId;
    private Long consultationId;
    private Long hospitalizationId;
    private Long insuranceId;
    private BigDecimal insuranceCoveragePercent;
    private BigDecimal subtotal;
    private BigDecimal insuranceAmount;
    private BigDecimal patientAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceDue;
    private String status;
    private LocalDate dueDate;
    private String notes;
    private LocalDateTime createdAt;

    // Libellés pour l'affichage (mappés en lecture)
    private String patientName;
    private String patientRecordNumber;
    private String insuranceName;
    private String createdByName;

    /** Lignes facturées (affichage + saisie). */
    private List<InvoiceItemDto> items = new ArrayList<>();

    /** Encaissements rattachés (lecture seule). */
    private List<PaymentDto> payments = new ArrayList<>();
}
