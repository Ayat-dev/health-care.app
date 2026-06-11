package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class RoomDto {

    private Long id;
    private String roomNumber;
    private Long departmentId;
    private String departmentName;
    private String type = "STANDARD";
    private int capacity = 1;
    private BigDecimal dailyRate = BigDecimal.ZERO;
    private boolean active = true;
    private String notes;

    // Occupation dérivée (renseignée pour le plan des lits)
    private int occupiedCount;
    private int availableCount;

    /** Séjours en cours dans cette chambre (plan des lits). */
    private List<HospitalizationDto> occupants = new ArrayList<>();

    public boolean isFull() {
        return availableCount <= 0;
    }
}
