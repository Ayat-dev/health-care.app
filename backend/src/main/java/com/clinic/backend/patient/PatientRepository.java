package com.clinic.backend.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Query(value = """
        SELECT p FROM Patient p
        LEFT JOIN FETCH p.assignedDoctor
        WHERE p.deletedAt IS NULL
        AND (:q IS NULL OR :q = '' OR
             LOWER(p.firstName) LIKE LOWER(CONCAT('%',:q,'%')) OR
             LOWER(p.lastName)  LIKE LOWER(CONCAT('%',:q,'%')) OR
             LOWER(p.recordNumber) LIKE LOWER(CONCAT('%',:q,'%')) OR
             p.phone LIKE CONCAT('%',:q,'%') OR
             p.nationalId LIKE CONCAT('%',:q,'%'))
        ORDER BY p.lastName, p.firstName
        """,
        countQuery = """
        SELECT COUNT(p) FROM Patient p
        WHERE p.deletedAt IS NULL
        AND (:q IS NULL OR :q = '' OR
             LOWER(p.firstName) LIKE LOWER(CONCAT('%',:q,'%')) OR
             LOWER(p.lastName)  LIKE LOWER(CONCAT('%',:q,'%')) OR
             LOWER(p.recordNumber) LIKE LOWER(CONCAT('%',:q,'%')) OR
             p.phone LIKE CONCAT('%',:q,'%') OR
             p.nationalId LIKE CONCAT('%',:q,'%'))
        """)
    Page<Patient> search(@Param("q") String q, Pageable pageable);

    Optional<Patient> findByRecordNumberAndDeletedAtIsNull(String recordNumber);

    Optional<Patient> findByIdAndDeletedAtIsNull(Long id);

    /** Patient with assignedDoctor eagerly fetched — for the detached dossier view (OSIV is off). */
    @Query("SELECT p FROM Patient p LEFT JOIN FETCH p.assignedDoctor WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Patient> findWithDoctorById(@Param("id") Long id);

    /** Dossier patient lié à un compte portail (rôle PATIENT) — pour {@code /portal/**}. */
    Optional<Patient> findByPortalUserIdAndDeletedAtIsNull(Long userId);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(p.recordNumber, 10) AS int)), 0) FROM Patient p WHERE p.recordNumber LIKE :prefix%")
    int findMaxSequence(@Param("prefix") String prefix);

    // ── Agrégats reporting (module 14) ─────────────────────────────────────────

    @Query("SELECT COUNT(p) FROM Patient p WHERE p.deletedAt IS NULL")
    long countActive();

    @Query("SELECT COUNT(p) FROM Patient p WHERE p.deletedAt IS NULL " +
           "AND p.createdAt >= :from AND p.createdAt < :to")
    long countNewBetween(@Param("from") java.time.LocalDateTime from,
                         @Param("to") java.time.LocalDateTime to);
}
