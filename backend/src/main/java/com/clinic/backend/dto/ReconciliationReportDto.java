package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Rapprochement des paiements par QR marchand (AmanaTa/MyNITA) d'une journée (Z4b).
 * Les compteurs portent sur <b>tous</b> les paiements QR du jour (synthèse stable) ;
 * {@link #payments} peut être filtré (non-rapprochés seulement) pour l'affichage.
 */
@Getter @Setter
public class ReconciliationReportDto {

    private LocalDate day;
    private List<PaymentDto> payments = new ArrayList<>();

    private int total;
    private int reconciledCount;
    private int pendingCount;
    private int missingReferenceCount;

    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal pendingAmount = BigDecimal.ZERO;
}
