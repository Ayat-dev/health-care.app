package com.clinic.backend.pharmacy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Une interaction médicamenteuse connue entre deux DCI (E2-B) — table de référence
 * <b>globale</b> (savoir médical universel, comme le catalogue CIM-10), non {@code @TenantId}.
 * <p>
 * Sert au recoupement <b>non-bloquant</b> à la dispensation : si deux médicaments de
 * l'ordonnance correspondent à {@link #dciA}/{@link #dciB}, on avertit le pharmacien.
 * La valeur croît avec la curation (base interne, hors licence commerciale).
 */
@Entity
@Table(name = "drug_interactions")
@Getter @Setter @NoArgsConstructor
public class DrugInteraction {

    /** Sévérité de l'interaction (badge + tri). */
    public enum Severity { MINEURE, MODEREE, MAJEURE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dci_a", nullable = false, length = 150)
    private String dciA;

    @Column(name = "dci_b", nullable = false, length = 150)
    private String dciB;

    @Column(nullable = false, length = 20)
    private String severity = Severity.MODEREE.name();

    @Column(length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
