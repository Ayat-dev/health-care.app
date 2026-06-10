package com.clinic.backend.lab;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LabRequestRepository extends JpaRepository<LabRequest, Long> {

    /** Full graph for a single request: patient/doctor/consultation + items→test→result→users. */
    @Query("""
        SELECT DISTINCT r FROM LabRequest r
        LEFT JOIN FETCH r.patient
        LEFT JOIN FETCH r.doctor
        LEFT JOIN FETCH r.consultation
        LEFT JOIN FETCH r.items i
        LEFT JOIN FETCH i.test
        LEFT JOIN FETCH i.result res
        LEFT JOIN FETCH res.laborantin
        LEFT JOIN FETCH res.validatedBy
        WHERE r.id = :id
        """)
    Optional<LabRequest> findWithRefsById(@Param("id") Long id);

    /**
     * Filtered list (header only — patient/doctor fetched). Any param may be null to skip.
     * The date window bounds requested_at. Ordered most-recent first.
     */
    @Query("""
        SELECT r FROM LabRequest r
        LEFT JOIN FETCH r.patient
        LEFT JOIN FETCH r.doctor
        WHERE (:from IS NULL OR r.requestedAt >= :from)
          AND (:to   IS NULL OR r.requestedAt <  :to)
          AND (:patientId IS NULL OR r.patient.id = :patientId)
          AND (:doctorId  IS NULL OR r.doctor.id  = :doctorId)
          AND (:status    IS NULL OR :status = '' OR r.status = :status)
          AND (:priority  IS NULL OR :priority = '' OR r.priority = :priority)
        ORDER BY r.requestedAt DESC
        """)
    List<LabRequest> search(@Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            @Param("patientId") Long patientId,
                            @Param("doctorId") Long doctorId,
                            @Param("status") String status,
                            @Param("priority") String priority);

    /** Laborantin "travail du jour": pending/in-progress orders, urgent first then oldest. */
    @Query("""
        SELECT DISTINCT r FROM LabRequest r
        LEFT JOIN FETCH r.patient
        LEFT JOIN FETCH r.doctor
        LEFT JOIN FETCH r.items i
        LEFT JOIN FETCH i.test
        LEFT JOIN FETCH i.result
        WHERE r.status IN ('EN_ATTENTE', 'EN_COURS')
        ORDER BY CASE WHEN r.priority = 'URGENT' THEN 0 ELSE 1 END, r.requestedAt ASC
        """)
    List<LabRequest> findWorklist();

    /** Chronological history for a patient's dossier (most recent first). */
    @Query("""
        SELECT DISTINCT r FROM LabRequest r
        LEFT JOIN FETCH r.doctor
        LEFT JOIN FETCH r.items i
        LEFT JOIN FETCH i.test
        LEFT JOIN FETCH i.result
        WHERE r.patient.id = :patientId
        ORDER BY r.requestedAt DESC
        """)
    List<LabRequest> findByPatient(@Param("patientId") Long patientId);

    /** Highest sequence used for a numbering prefix (e.g. "LAB-2026-"); 0 if none. */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(r.requestNumber, 10) AS int)), 0) " +
           "FROM LabRequest r WHERE r.requestNumber LIKE :prefix%")
    int findMaxSequence(@Param("prefix") String prefix);
}
