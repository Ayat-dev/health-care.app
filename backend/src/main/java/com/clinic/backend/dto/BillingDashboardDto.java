package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
public class BillingDashboardDto {

    private long pendingCount;     // EN_ATTENTE
    private long partialCount;     // PARTIEL
    private long paidCount;        // PAYE
    private BigDecimal totalInvoiced;     // part patient facturée (hors annulées)
    private BigDecimal totalCollected;    // total encaissé
    private BigDecimal totalOutstanding;  // reste à recouvrer
    private BigDecimal todayCollected;    // encaissements du jour
}
