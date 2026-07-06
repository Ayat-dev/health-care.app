package com.clinic.backend.license;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LicenseActivationRepository extends JpaRepository<LicenseActivation, Long> {

    /** Ligne d'état unique (la plus ancienne si plusieurs existaient par accident). */
    Optional<LicenseActivation> findFirstByOrderByIdAsc();
}
