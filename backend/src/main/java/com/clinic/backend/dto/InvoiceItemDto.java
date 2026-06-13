package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
public class InvoiceItemDto {

    private Long id;
    private Long actId;
    private String actCode;
    private String description;
    private int quantity = 1;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}
