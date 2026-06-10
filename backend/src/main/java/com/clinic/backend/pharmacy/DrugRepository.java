package com.clinic.backend.pharmacy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DrugRepository extends JpaRepository<Drug, Long> {

    List<Drug> findByActiveTrueOrderByNameAsc();

    @Query("""
        SELECT d FROM Drug d
        WHERE (:q IS NULL OR :q = '' OR
               LOWER(d.name) LIKE LOWER(CONCAT('%',:q,'%')) OR
               LOWER(d.genericName) LIKE LOWER(CONCAT('%',:q,'%')) OR
               LOWER(d.code) LIKE LOWER(CONCAT('%',:q,'%')))
          AND (:category IS NULL OR :category = '' OR d.category = :category)
        ORDER BY d.name ASC
        """)
    List<Drug> search(@Param("q") String q, @Param("category") String category);

    boolean existsByCodeIgnoreCase(String code);

    Optional<Drug> findByCodeIgnoreCase(String code);

    @Query("SELECT DISTINCT d.category FROM Drug d WHERE d.category IS NOT NULL ORDER BY d.category")
    List<String> findDistinctCategories();
}
