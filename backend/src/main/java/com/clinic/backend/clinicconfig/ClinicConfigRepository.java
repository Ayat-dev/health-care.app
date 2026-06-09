package com.clinic.backend.clinicconfig;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClinicConfigRepository extends JpaRepository<ClinicConfig, Long> {

    Optional<ClinicConfig> findFirstByOrderByIdAsc();
}
