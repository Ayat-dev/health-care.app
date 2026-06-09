package com.clinic.backend.service;

import com.clinic.backend.dto.UserDto;
import com.clinic.backend.model.Role;
import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
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

    // ── Liste (non supprimés) ──────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<User> listActive() {
        return userRepository.findByDeletedAtIsNullOrderByUsernameAsc();
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + id));
        if (u.getDeletedAt() != null)
            throw new IllegalArgumentException("Utilisateur supprimé : " + id);
        return u;
    }

    /** Rôles assignables, pour alimenter le sélecteur du formulaire. */
    public List<Role> assignableRoles() {
        return Arrays.asList(Role.values());
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
            log.info("Mot de passe réinitialisé pour {}", u.getUsername());
        }
        return userRepository.save(u);
    }

    // ── Activer / désactiver ──────────────────────────────────────────────────
    public void toggleActive(Long id) {
        User u = getById(id);
        u.setActive(!u.isActive());
        log.info("Utilisateur {} : actif = {}", u.getUsername(), u.isActive());
    }

    // ── Suppression logique ───────────────────────────────────────────────────
    public void delete(Long id) {
        User u = getById(id);
        u.setDeletedAt(LocalDateTime.now());
        u.setActive(false); // un compte supprimé ne peut plus se connecter
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
        if (role == null || Arrays.stream(Role.values()).noneMatch(r -> r.name().equals(role)))
            throw new IllegalArgumentException("Rôle invalide : " + role);
    }

    /** Règle métier : minimum 8 caractères, au moins 1 chiffre. */
    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || !password.matches(".*\\d.*"))
            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir au moins 8 caractères et 1 chiffre.");
    }
}
