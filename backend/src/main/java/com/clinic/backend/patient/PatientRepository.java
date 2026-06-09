package com.clinic.backend.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Query("""
        SELECT p FROM Patient p
        WHERE p.deletedAt IS NULL
        AND (:q IS NULL OR :q = '' OR
             LOWER(p.firstName) LIKE LOWER(CONCAT('%',:q,'%')) OR
             LOWER(p.lastName)  LIKE LOWER(CONCAT('%',:q,'%')) OR
             LOWER(p.recordNumber) LIKE LOWER(CONCAT('%',:q,'%')) OR
             p.phone LIKE CONCAT('%',:q,'%') OR
             p.nationalId LIKE CONCAT('%',:q,'%'))
        ORDER BY p.lastName, p.firstName
        """)
    Page<Patient> search(@Param("q") String q, Pageable pageable);

    Optional<Patient> findByRecordNumberAndDeletedAtIsNull(String recordNumber);

    Optional<Patient> findByIdAndDeletedAtIsNull(Long id);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(p.recordNumber, 10) AS int)), 0) FROM Patient p WHERE p.recordNumber LIKE :prefix%")
    int findMaxSequence(@Param("prefix") String prefix);
}
