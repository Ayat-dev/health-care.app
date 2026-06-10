package com.clinic.backend.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Single appointment with patient/doctor/department eagerly fetched (for DTO mapping). */
    @Query("""
        SELECT a FROM Appointment a
        LEFT JOIN FETCH a.patient
        LEFT JOIN FETCH a.doctor
        LEFT JOIN FETCH a.department
        WHERE a.id = :id
        """)
    Optional<Appointment> findWithRefsById(@Param("id") Long id);

    /**
     * Filtered list. Any param may be null to skip that filter. The date window
     * (from/to) bounds start_time; statuses ANNULE/ABSENT are still returned so
     * the agenda can show them — callers filter when needed.
     */
    @Query("""
        SELECT a FROM Appointment a
        LEFT JOIN FETCH a.patient
        LEFT JOIN FETCH a.doctor
        LEFT JOIN FETCH a.department
        WHERE (:from IS NULL OR a.startTime >= :from)
          AND (:to   IS NULL OR a.startTime <  :to)
          AND (:doctorId  IS NULL OR a.doctor.id  = :doctorId)
          AND (:patientId IS NULL OR a.patient.id = :patientId)
          AND (:status    IS NULL OR :status = '' OR a.status = :status)
        ORDER BY a.startTime
        """)
    List<Appointment> search(@Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to,
                             @Param("doctorId") Long doctorId,
                             @Param("patientId") Long patientId,
                             @Param("status") String status);

    /**
     * Active (non-cancelled, non-no-show) appointments for a doctor within a window,
     * ordered by start. Used for slot computation and conflict detection.
     */
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.doctor.id = :doctorId
          AND a.status NOT IN ('ANNULE', 'ABSENT')
          AND a.startTime < :to
          AND a.endTime   > :from
        ORDER BY a.startTime
        """)
    List<Appointment> findActiveForDoctorBetween(@Param("doctorId") Long doctorId,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    /**
     * Overlap count for conflict detection: same doctor, active status, time ranges
     * overlap (start < otherEnd AND end > otherStart). excludeId skips the row being
     * edited (pass a negative value when creating).
     */
    @Query("""
        SELECT COUNT(a) FROM Appointment a
        WHERE a.doctor.id = :doctorId
          AND a.id <> :excludeId
          AND a.status NOT IN ('ANNULE', 'ABSENT')
          AND a.startTime < :end
          AND a.endTime   > :start
        """)
    long countOverlaps(@Param("doctorId") Long doctorId,
                       @Param("start") LocalDateTime start,
                       @Param("end") LocalDateTime end,
                       @Param("excludeId") Long excludeId);
}
