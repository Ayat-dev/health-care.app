package com.clinic.backend.hospitalization;

import com.clinic.backend.department.Department;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A room (which may hold several beds via {@code capacity}) belonging to a department.
 * Occupancy is derived at read time from the count of {@link Hospitalization}s currently
 * in status ADMIS for the room — it is NOT stored on the row, so a transfer or discharge
 * frees a bed simply by flipping the hospitalization status.
 */
@Entity
@Table(name = "rooms")
@Getter @Setter @NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_number", nullable = false, unique = true, length = 20)
    private String roomNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false, length = 20)
    private String type = "STANDARD"; // STANDARD, PRIVE, SOINS_INTENSIFS, MATERNITE

    @Column(nullable = false)
    private int capacity = 1; // nombre de lits dans la chambre

    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
