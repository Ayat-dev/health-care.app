package com.clinic.backend.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActCatalogRepository extends JpaRepository<ActCatalog, Long> {

    List<ActCatalog> findAllByOrderByNameAsc();

    List<ActCatalog> findByActiveTrueOrderByNameAsc();

    Optional<ActCatalog> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
