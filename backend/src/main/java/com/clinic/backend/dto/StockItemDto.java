package com.clinic.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
public class StockItemDto {
    private Long id;
    private Long drugId;
    private String batchNumber;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    private int quantity;
    private int quantityAlert = 10;
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private String supplier;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate receivedAt;

    // Read-only display fields (populated when mapping entity → DTO)
    private String drugName;
    private String drugForm;
    private String drugDosage;
    private String drugUnit;
    private String receivedByName;
    private boolean low;            // quantity ≤ alert
    private boolean expired;        // expiry < today
    private boolean expiringSoon;   // expiry within 30 days
}
