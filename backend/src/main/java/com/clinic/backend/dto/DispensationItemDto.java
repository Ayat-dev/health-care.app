package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
public class DispensationItemDto {
    // Input: caller asks for a drug + quantity; the service allocates batches (FIFO).
    private Long drugId;
    private int quantity;

    // Output: resolved batch + pricing (populated when mapping entity → DTO).
    private Long id;
    private Long stockItemId;
    private String drugName;
    private String batchNumber;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}
