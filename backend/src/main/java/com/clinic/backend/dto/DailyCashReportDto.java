package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rapport de caisse journalier : tous les encaissements d'une journée. */
@Getter @Setter
public class DailyCashReportDto {

    private LocalDate day;
    private int paymentCount;
    private BigDecimal total = BigDecimal.ZERO;

    /** Total encaissé par mode de paiement (ESPECES, ORANGE_MONEY, …). */
    private Map<String, BigDecimal> totalByMethod = new LinkedHashMap<>();

    /** Détail ligne à ligne. */
    private List<PaymentDto> payments;
}
