package com.clinic.backend.lab;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Accès direct à un {@link LabResult} par id — utilisé par la projection FHIR
 * (P2.1) pour servir {@code GET /fhir/Observation/lab-{id}}. Charge le graphe
 * (item→test, requête→patient) car OSIV est désactivé.
 */
public interface LabResultRepository extends JpaRepository<LabResult, Long> {

    @Query("""
        SELECT r FROM LabResult r
        JOIN FETCH r.requestItem i
        JOIN FETCH i.test
        JOIN FETCH i.labRequest req
        JOIN FETCH req.patient
        WHERE r.id = :id
        """)
    Optional<LabResult> findWithRefsById(@Param("id") Long id);
}
