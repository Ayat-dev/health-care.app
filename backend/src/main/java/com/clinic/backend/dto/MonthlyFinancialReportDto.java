package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bilan financier mensuel : facturé / encaissé / restant + répartition par mode. */
@Getter @Setter
public class MonthlyFinancialReportDto {

    private int month;
    private int year;

    private long invoiceCount;
    private BigDecimal totalInvoiced = BigDecimal.ZERO;   // part patient facturée
    private BigDecimal totalCollected = BigDecimal.ZERO;  // paiements encaissés sur la période
    private BigDecimal totalOutstanding = BigDecimal.ZERO;

    /** Encaissements du mois par mode de paiement. */
    private Map<String, BigDecimal> collectedByMethod = new LinkedHashMap<>();
}
