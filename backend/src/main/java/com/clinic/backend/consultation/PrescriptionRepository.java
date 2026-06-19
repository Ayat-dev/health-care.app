package com.clinic.backend.consultation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    /** Prescription with patient/doctor/items + consultation eagerly fetched (for DTO/PDF). */
    @Query("""
        SELECT DISTINCT p FROM Prescription p
        LEFT JOIN FETCH p.patient
        LEFT JOIN FETCH p.doctor
        LEFT JOIN FETCH p.consultation
        LEFT JOIN FETCH p.items
        WHERE p.id = :id
        """)
    Optional<Prescription> findWithRefsById(@Param("id") Long id);

    /** The ordonnance attached to a consultation, if any (one prescription per consultation here). */
    @Query("""
        SELECT DISTINCT p FROM Prescription p
        LEFT JOIN FETCH p.patient
        LEFT JOIN FETCH p.doctor
        LEFT JOIN FETCH p.items
        WHERE p.consultation.id = :consultationId
        ORDER BY p.id DESC
        """)
    java.util.List<Prescription> findByConsultation(@Param("consultationId") Long consultationId);

    /** Ordonnances d'un patient avec lignes + médecin (projection FHIR MedicationRequest). */
    @Query("""
        SELECT DISTINCT p FROM Prescription p
        LEFT JOIN FETCH p.patient
        LEFT JOIN FETCH p.doctor
        LEFT JOIN FETCH p.items
        WHERE p.patient.id = :patientId
        ORDER BY p.issueDate DESC, p.id DESC
        """)
    java.util.List<Prescription> findByPatientWithItems(@Param("patientId") Long patientId);

    /** Highest sequence used for a numbering prefix (e.g. "ORD-2026-"); 0 if none. */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(p.prescriptionNumber, 10) AS int)), 0) " +
           "FROM Prescription p WHERE p.prescriptionNumber LIKE :prefix%")
    int findMaxSequence(@Param("prefix") String prefix);
}
