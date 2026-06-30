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

    // ── Agrégats reporting (module 14) ─────────────────────────────────────────

    /** Nombre de consultations non annulées sur une période [from, to). */
    @Query("SELECT COUNT(c) FROM Consultation c " +
           "WHERE c.consultationDate >= :from AND c.consultationDate < :to AND c.status <> 'ANNULE'")
    long countBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Diagnostics (déchiffrés) des consultations clôturées sur la période. Le diagnostic
     * étant chiffré au repos (D3a, IV aléatoire), on ne peut PLUS faire de {@code GROUP BY}
     * en SQL ; on récupère les valeurs (déchiffrées par le convertisseur sur la projection)
     * et l'agrégation « top pathologies » se fait en Java côté service (recherche intacte).
     */
    @Query("""
        SELECT c.diagnosis FROM Consultation c
        WHERE c.status = 'TERMINE'
          AND c.diagnosis IS NOT NULL
          AND c.consultationDate >= :from AND c.consultationDate < :to
        """)
    List<String> findCompletedDiagnoses(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Consultations par département sur la période — [departmentName, count]. */
    @Query("""
        SELECT d.name, COUNT(c) FROM Consultation c
        LEFT JOIN c.department d
        WHERE c.consultationDate >= :from AND c.consultationDate < :to AND c.status <> 'ANNULE'
        GROUP BY d.name
        ORDER BY COUNT(c) DESC
        """)
    List<Object[]> countByDepartmentBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Sexe des patients consultés sur la période — [gender, count]. */
    @Query("""
        SELECT p.gender, COUNT(c) FROM Consultation c
        JOIN c.patient p
        WHERE c.consultationDate >= :from AND c.consultationDate < :to AND c.status <> 'ANNULE'
        GROUP BY p.gender
        """)
    List<Object[]> countBySexBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Dates de naissance des patients consultés sur la période — le découpage en tranches
     * d'âge est fait côté service (évite l'arithmétique de dates spécifique au SGBD H2/PostgreSQL).
     */
    @Query("""
        SELECT p.birthDate FROM Consultation c
        JOIN c.patient p
        WHERE c.consultationDate >= :from AND c.consultationDate < :to AND c.status <> 'ANNULE'
        """)
    List<java.time.LocalDate> findConsultationPatientBirthDates(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Mes consultations du jour (patient chargé pour le DTO, OSIV off). */
    @Query("""
        SELECT c FROM Consultation c
        LEFT JOIN FETCH c.patient
        LEFT JOIN FETCH c.department
        WHERE c.doctor.id = :doctorId
          AND c.consultationDate >= :from AND c.consultationDate < :to
        ORDER BY c.consultationDate
        """)
    List<Consultation> findForDoctorBetween(@Param("doctorId") Long doctorId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);
}
