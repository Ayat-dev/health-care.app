package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Liste des impayés : factures EN_ATTENTE / PARTIEL avec reste à recouvrer. */
@Getter @Setter
public class OutstandingReportDto {

    private long invoiceCount;
    private BigDecimal totalOutstanding = BigDecimal.ZERO;
    private List<InvoiceDto> invoices = new ArrayList<>();
}
