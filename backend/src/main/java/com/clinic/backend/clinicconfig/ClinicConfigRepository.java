package com.clinic.backend.clinicconfig;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClinicConfigRepository extends JpaRepository<ClinicConfig, Long> {

    Optional<ClinicConfig> findFirstByOrderByIdAsc();

    /** Config de la clinique courante (P4.2) — une par clinique (UNIQUE clinic_id). */
    Optional<ClinicConfig> findByClinicId(Long clinicId);
}
