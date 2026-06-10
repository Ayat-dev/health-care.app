package com.clinic.backend.radiology;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RadiologyExamCatalogRepository extends JpaRepository<RadiologyExamCatalog, Long> {

    List<RadiologyExamCatalog> findByActiveTrueOrderByTypeAscNameAsc();

    Optional<RadiologyExamCatalog> findByCodeIgnoreCase(String code);
}
