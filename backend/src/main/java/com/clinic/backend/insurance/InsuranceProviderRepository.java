package com.clinic.backend.insurance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InsuranceProviderRepository extends JpaRepository<InsuranceProvider, Long> {

    List<InsuranceProvider> findAllByOrderByNameAsc();

    List<InsuranceProvider> findByActiveTrueOrderByNameAsc();

    Optional<InsuranceProvider> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
