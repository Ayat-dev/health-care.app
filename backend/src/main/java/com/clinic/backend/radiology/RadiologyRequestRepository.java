package com.clinic.backend.radiology;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RadiologyRequestRepository extends JpaRepository<RadiologyRequest, Long> {

    /**
     * Full graph for a single request: patient/doctor/consultation + items→exam + report→users.
     * Images are loaded lazily within the service transaction (kept out of this fetch so
     * {@code items} stays the only collection join — avoids a multi-bag fetch).
     */
    @Query("""
        SELECT DISTINCT r FROM RadiologyRequest r
        LEFT JOIN FETCH r.patient
        LEFT JOIN FETCH r.doctor
        LEFT JOIN FETCH r.consultation
        LEFT JOIN FETCH r.items i
        LEFT JOIN FETCH i.exam
        LEFT JOIN FETCH r.report rep
        LEFT JOIN FETCH rep.radiologist
        LEFT JOIN FETCH rep.validatedBy
        WHERE r.id = :id
        """)
    Optional<RadiologyRequest> findWithRefsById(@Param("id") Long id);

    /**
     * Filtered list (header only — patient/doctor fetched). Any param may be null to skip.
     * The date window bounds requested_at. Ordered most-recent first.
     */
    @Query("""
        SELECT r FROM RadiologyRequest r
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
    List<RadiologyRequest> search(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  @Param("patientId") Long patientId,
                                  @Param("doctorId") Long doctorId,
                                  @Param("status") String status,
                                  @Param("priority") String priority);

    /** Radiologue "travail du jour": pending/in-progress orders, urgent first then oldest. */
    @Query("""
        SELECT DISTINCT r FROM RadiologyRequest r
        LEFT JOIN FETCH r.patient
        LEFT JOIN FETCH r.doctor
        LEFT JOIN FETCH r.items i
        LEFT JOIN FETCH i.exam
        LEFT JOIN FETCH r.report
        WHERE r.status IN ('EN_ATTENTE', 'EN_COURS')
        ORDER BY CASE WHEN r.priority = 'URGENT' THEN 0 ELSE 1 END, r.requestedAt ASC
        """)
    List<RadiologyRequest> findWorklist();

    /** Chronological history for a patient's dossier (most recent first). */
    @Query("""
        SELECT DISTINCT r FROM RadiologyRequest r
        LEFT JOIN FETCH r.doctor
        LEFT JOIN FETCH r.items i
        LEFT JOIN FETCH i.exam
        LEFT JOIN FETCH r.report
        WHERE r.patient.id = :patientId
        ORDER BY r.requestedAt DESC
        """)
    List<RadiologyRequest> findByPatient(@Param("patientId") Long patientId);

    /** Highest sequence used for a numbering prefix (e.g. "RAD-2026-"); 0 if none. */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(r.requestNumber, 10) AS int)), 0) " +
           "FROM RadiologyRequest r WHERE r.requestNumber LIKE :prefix%")
    int findMaxSequence(@Param("prefix") String prefix);
}
