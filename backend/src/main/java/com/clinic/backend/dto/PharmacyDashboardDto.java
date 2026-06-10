package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class PharmacyDashboardDto {
    private long lowStockCount;
    private long expiringCount;
    private long expiredCount;
    private BigDecimal stockValue = BigDecimal.ZERO;
    private List<TopDrug> topDispensed = new ArrayList<>();

    @Getter @Setter
    public static class TopDrug {
        private Long drugId;
        private String drugName;
        private long totalQuantity;

        public TopDrug(Long drugId, String drugName, long totalQuantity) {
            this.drugId = drugId;
            this.drugName = drugName;
            this.totalQuantity = totalQuantity;
        }
    }
}
