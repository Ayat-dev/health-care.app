package com.clinic.backend.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabTestCatalogRepository extends JpaRepository<LabTestCatalog, Long> {

    List<LabTestCatalog> findAllByOrderByNameAsc();

    List<LabTestCatalog> findByActiveTrueOrderByNameAsc();

    Optional<LabTestCatalog> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
