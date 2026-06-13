package com.clinic.backend.maternity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaternityRecordRepository extends JpaRepository<MaternityRecord, Long> {

    /** Full graph for a single dossier: patient/doctor + visits→doctor. */
    @Query("""
        SELECT DISTINCT r FROM MaternityRecord r
        LEFT JOIN FETCH r.patient
        LEFT JOIN FETCH r.doctor
        LEFT JOIN FETCH r.visits v
        LEFT JOIN FETCH v.doctor
        WHERE r.id = :id
        """)
    Optional<MaternityRecord> findWithRefsById(@Param("id") Long id);

    /** The (at most one) dossier for a patiente, with its visits. */
    @Query("""
        SELECT DISTINCT r FROM MaternityRecord r
        LEFT JOIN FETCH r.patient
        LEFT JOIN FETCH r.doctor
        LEFT JOIN FETCH r.visits v
        LEFT JOIN FETCH v.doctor
        WHERE r.patient.id = :patientId
        """)
    Optional<MaternityRecord> findByPatientId(@Param("patientId") Long patientId);

    boolean existsByPatientId(Long patientId);

    /** Filtered list (header only — patient/doctor fetched). Status may be null/blank to skip. */
    @Query("""
        SELECT r FROM MaternityRecord r
        LEFT JOIN FETCH r.patient
        LEFT JOIN FETCH r.doctor
        WHERE (:status IS NULL OR :status = '' OR r.status = :status)
        ORDER BY r.createdAt DESC
        """)
    List<MaternityRecord> search(@Param("status") String status);
}
