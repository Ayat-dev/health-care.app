package com.clinic.backend.consultation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Accès direct à une ligne d'ordonnance par id — utilisé par la projection FHIR
 * (P2.1) pour servir {@code GET /fhir/MedicationRequest/{id}} (une ligne = un
 * MedicationRequest). Charge l'ordonnance + patient/médecin (OSIV off).
 */
public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {

    @Query("""
        SELECT i FROM PrescriptionItem i
        JOIN FETCH i.prescription p
        JOIN FETCH p.patient
        JOIN FETCH p.doctor
        WHERE i.id = :id
        """)
    Optional<PrescriptionItem> findWithRefsById(@Param("id") Long id);
}
