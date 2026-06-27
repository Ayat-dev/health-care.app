package com.clinic.backend.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {

    Optional<Clinic> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Clinic> findAllByOrderByNameAsc();

    /** Cliniques actives — itérées par les tâches de fond multi-tenant (schedulers). */
    List<Clinic> findByActiveTrue();
}
