package com.clinic.backend.hospitalization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HospitalizationRepository extends JpaRepository<Hospitalization, Long> {

    /** Full graph for a single stay: patient + room→department + doctor. */
    @Query("""
        SELECT h FROM Hospitalization h
        LEFT JOIN FETCH h.patient
        LEFT JOIN FETCH h.room rm
        LEFT JOIN FETCH rm.department
        LEFT JOIN FETCH h.doctor
        WHERE h.id = :id
        """)
    Optional<Hospitalization> findWithRefsById(@Param("id") Long id);

    /** Filtered list (header refs fetched). Null/blank status returns everything. Most recent first. */
    @Query("""
        SELECT h FROM Hospitalization h
        LEFT JOIN FETCH h.patient
        LEFT JOIN FETCH h.room rm
        LEFT JOIN FETCH rm.department
        LEFT JOIN FETCH h.doctor
        WHERE (:status IS NULL OR :status = '' OR h.status = :status)
        ORDER BY h.admissionDate DESC
        """)
    List<Hospitalization> search(@Param("status") String status);

    /** All currently-admitted stays (patient fetched) — used to build the bed board occupancy. */
    @Query("""
        SELECT h FROM Hospitalization h
        LEFT JOIN FETCH h.patient
        WHERE h.status = 'ADMIS'
        ORDER BY h.admissionDate ASC
        """)
    List<Hospitalization> findActive();

    /** Chronological stay history for a patient's dossier (most recent first). */
    @Query("""
        SELECT h FROM Hospitalization h
        LEFT JOIN FETCH h.room rm
        LEFT JOIN FETCH rm.department
        LEFT JOIN FETCH h.doctor
        WHERE h.patient.id = :patientId
        ORDER BY h.admissionDate DESC
        """)
    List<Hospitalization> findByPatient(@Param("patientId") Long patientId);

    long countByRoomIdAndStatus(Long roomId, String status);

    boolean existsByPatientIdAndStatus(Long patientId, String status);
}
