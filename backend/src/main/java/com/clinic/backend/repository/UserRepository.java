package com.clinic.backend.repository;

import com.clinic.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    List<User> findByDeletedAtIsNullOrderByUsernameAsc();

    /** Comptes d'une clinique donnée (multi-tenant P4.2) — users n'est pas @TenantId. */
    List<User> findByDeletedAtIsNullAndClinicIdOrderByUsernameAsc(Long clinicId);

    List<User> findByRoleAndDeletedAtIsNullOrderByFullNameAsc(String role);

    /** Comptes d'un rôle pour une clinique donnée (multi-tenant) — users n'est pas @TenantId. */
    List<User> findByRoleAndClinicIdAndDeletedAtIsNullOrderByFullNameAsc(String role, Long clinicId);

    boolean existsByUsername(String username);
}
