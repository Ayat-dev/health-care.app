package com.clinic.backend.catalog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface Icd10CodeRepository extends JpaRepository<Icd10Code, Long> {

    List<Icd10Code> findAllByOrderByCodeAsc();

    Optional<Icd10Code> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /**
     * Auto-complétion : codes actifs dont le code ou le libellé contient {@code q}.
     * Une correspondance par code passe avant une correspondance par libellé seul,
     * puis tri alphabétique par code. {@code Pageable} borne le nombre de résultats.
     */
    @Query("""
        SELECT i FROM Icd10Code i
        WHERE i.active = true
          AND (UPPER(i.code) LIKE UPPER(CONCAT('%', :q, '%'))
            OR UPPER(i.title) LIKE UPPER(CONCAT('%', :q, '%')))
        ORDER BY
          CASE WHEN UPPER(i.code) LIKE UPPER(CONCAT(:q, '%')) THEN 0 ELSE 1 END,
          i.code ASC
        """)
    List<Icd10Code> search(@Param("q") String q, Pageable pageable);
}
