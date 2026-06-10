package com.clinic.backend.consultation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {

    /** Single consultation with patient/doctor/department eagerly fetched (for DTO mapping). */
    @Query("""
        SELECT c FROM Consultation c
        LEFT JOIN FETCH c.patient
        LEFT JOIN FETCH c.doctor
        LEFT JOIN FETCH c.department
        LEFT JOIN FETCH c.appointment
        WHERE c.id = :id
        """)
    Optional<Consultation> findWithRefsById(@Param("id") Long id);

    /**
     * Filtered list. Any param may be null to skip that filter. The date window
     * (from/to) bounds consultation_date. Ordered most-recent first.
     */
    @Query("""
        SELECT c FROM Consultation c
        LEFT JOIN FETCH c.patient
        LEFT JOIN FETCH c.doctor
        LEFT JOIN FETCH c.department
        WHERE (:from IS NULL OR c.consultationDate >= :from)
          AND (:to   IS NULL OR c.consultationDate <  :to)
          AND (:doctorId  IS NULL OR c.doctor.id  = :doctorId)
          AND (:patientId IS NULL OR c.patient.id = :patientId)
          AND (:status    IS NULL OR :status = '' OR c.status = :status)
        ORDER BY c.consultationDate DESC
        """)
    List<Consultation> search(@Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              @Param("doctorId") Long doctorId,
                              @Param("patientId") Long patientId,
                              @Param("status") String status);

    /** Chronological history for a patient's dossier (most recent first). */
    @Query("""
        SELECT c FROM Consultation c
        LEFT JOIN FETCH c.doctor
        LEFT JOIN FETCH c.department
        WHERE c.patient.id = :patientId
        ORDER BY c.consultationDate DESC
        """)
    List<Consultation> findByPatient(@Param("patientId") Long patientId);

    boolean existsByAppointmentId(Long appointmentId);
}
