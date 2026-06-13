package com.clinic.backend.maternity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PrenatalVisitRepository extends JpaRepository<PrenatalVisit, Long> {

    /** A single CPN with its record + doctor (for the edit form). */
    @Query("""
        SELECT v FROM PrenatalVisit v
        LEFT JOIN FETCH v.maternityRecord
        LEFT JOIN FETCH v.doctor
        WHERE v.id = :id
        """)
    Optional<PrenatalVisit> findWithRefsById(@Param("id") Long id);

    /** Highest CPN number already recorded for a dossier; 0 if none (to suggest the next one). */
    @Query("SELECT COALESCE(MAX(v.visitNumber), 0) FROM PrenatalVisit v WHERE v.maternityRecord.id = :recordId")
    int findMaxVisitNumber(@Param("recordId") Long recordId);
}
