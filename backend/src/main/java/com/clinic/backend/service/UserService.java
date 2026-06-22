package com.clinic.backend.service;

import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.dto.UserDto;
import com.clinic.backend.model.Role;
import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.security.RefreshTokenService;
import com.clinic.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Business logic for admin user management: create, edit, enable/disable, soft delete.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    // ── Liste (non supprimés) ──────────────────────────────────────────────
    // Multi-tenant (P4.2) : un ADMIN de clinique ne gère que les comptes de SA clinique.
    // Le SUPER_ADMIN (clinique courante = null) voit tout. users n'est pas @TenantId
    // (la connexion par username doit rester globale) → filtrage applicatif explicite.
    @Transactional(readOnly = true)
    public List<User> listActive() {
        Long clinic = TenantContext.currentClinicId();
        return clinic == null
                ? userRepository.findByDeletedAtIsNullOrderByUsernameAsc()
                : userRepository.findByDeletedAtIsNullAndClinicIdOrderByUsernameAsc(clinic);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + id));
        if (u.getDeletedAt() != null)
            throw new IllegalArgumentException("Utilisateur supprimé : " + id);
        // Cloisonnement tenant : pas d'accès à un compte d'une autre clinique.
        Long clinic = TenantContext.currentClinicId();
        if (clinic != null && !clinic.equals(u.getClinicId()))
            throw new ResourceNotFoundException("Utilisateur introuvable : " + id);
        return u;
    }

    /** Rôles assignables (hors SUPER_ADMIN, transverse et non créable ici). */
    public List<Role> assignableRoles() {
        return Arrays.stream(Role.values()).filter(r -> r != Role.SUPER_ADMIN).toList();
    }

    // ── Création ────────────────────────────────────────────────────────────
    public User create(UserDto dto) {
        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        if (username.isEmpty())
            throw new IllegalArgumentException("Le nom d'utilisateur est obligatoire.");
        if (userRepository.existsByUsername(username))
            throw new IllegalArgumentException("Ce nom d'utilisateur existe déjà : " + username);
        validateRole(dto.getRole());
        validatePassword(dto.getPassword());

        User u = new User(username, passwordEncoder.encode(dto.getPassword()),
                dto.getFullName(), dto.getRole());
        u.setActive(dto.isActive());
        // Le nouveau compte hérite de la clinique de l'admin créateur (multi-tenant P4.2).
        u.setClinicId(TenantContext.currentClinicId());
        User saved = userRepository.save(u);
        log.info("Utilisateur créé : {} (rôle {})", saved.getUsername(), saved.getRole());
        return saved;
    }

    // ── Modification ──────────────────────────────────────────────────────────
    public User update(Long id, UserDto dto) {
        User u = getById(id);
        validateRole(dto.getRole());
        u.setFullName(dto.getFullName());
        u.setRole(dto.getRole());
        u.setActive(dto.isActive());
        // Mot de passe optionnel : laissé vide => inchangé
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            validatePassword(dto.getPassword());
            u.setPassword(passwordEncoder.encode(dto.getPassword()));
            // Révocation JWT (P4.4) : un reset de mot de passe coupe les sessions en cours.
            refreshTokenService.revokeAllForUser(u);
            log.info("Mot de passe réinitialisé pour {}", u.getUsername());
        } else if (!u.isActive()) {
            // Désactivé via l'édition → on coupe ses sessions.
            refreshTokenService.revokeAllForUser(u);
        }
        return userRepository.save(u);
    }

    // ── Activer / désactiver ──────────────────────────────────────────────────
    public void toggleActive(Long id) {
        User u = getById(id);
        u.setActive(!u.isActive());
        // Révocation JWT (P4.4) : une désactivation coupe immédiatement les sessions.
        if (!u.isActive()) refreshTokenService.revokeAllForUser(u);
        log.info("Utilisateur {} : actif = {}", u.getUsername(), u.isActive());
    }

    // ── Suppression logique ───────────────────────────────────────────────────
    public void delete(Long id) {
        User u = getById(id);
        u.setDeletedAt(LocalDateTime.now());
        u.setActive(false); // un compte supprimé ne peut plus se connecter
        // Révocation JWT (P4.4) : plus aucune session après suppression.
        refreshTokenService.revokeAllForUser(u);
        log.info("Utilisateur supprimé (logique) : {}", u.getUsername());
    }

    // ── Mapping entité → DTO ──────────────────────────────────────────────────
    public UserDto toDto(User u) {
        UserDto dto = new UserDto();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setFullName(u.getFullName());
        dto.setRole(u.getRole());
        dto.setActive(u.isActive());
        dto.setCreatedAt(u.getCreatedAt());
        return dto;
    }

    // ── Validation ────────────────────────────────────────────────────────────
    private void validateRole(String role) {
        // SUPER_ADMIN est transverse : non assignable via la gestion des utilisateurs (P4.2).
        if (role == null || Role.SUPER_ADMIN.name().equals(role)
                || Arrays.stream(Role.values()).noneMatch(r -> r.name().equals(role)))
            throw new IllegalArgumentException("Rôle invalide : " + role);
    }

    /** Règle métier : minimum 8 caractères, au moins 1 chiffre. */
    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || !password.matches(".*\\d.*"))
            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir au moins 8 caractères et 1 chiffre.");
    }
}
