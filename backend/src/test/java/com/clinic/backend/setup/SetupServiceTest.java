package com.clinic.backend.setup;

import com.clinic.backend.clinicconfig.ClinicConfig;
import com.clinic.backend.clinicconfig.ClinicConfigRepository;
import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.tenant.Clinic;
import com.clinic.backend.tenant.ClinicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Logique de première installation, testée en isolation (sans Spring) :
 * détection « pas encore installé », verrou à sens unique, création admin/clinique/config,
 * et garde-fous de validation.
 */
@ExtendWith(MockitoExtension.class)
class SetupServiceTest {

    @Mock UserRepository userRepository;
    @Mock ClinicRepository clinicRepository;
    @Mock ClinicConfigRepository clinicConfigRepository;
    @Mock org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    SetupService service;

    @BeforeEach
    void setUp() {
        service = new SetupService(userRepository, clinicRepository, clinicConfigRepository, passwordEncoder);
    }

    private SetupForm validForm() {
        SetupForm f = new SetupForm();
        f.setAdminUsername("admin");
        f.setAdminFullName("Administrateur");
        f.setAdminPassword("motdepasse1");
        f.setAdminPasswordConfirm("motdepasse1");
        f.setClinicName("Clinique du Centre");
        return f;
    }

    // ── isSetupRequired ───────────────────────────────────────────────────────

    @Test
    void installation_requise_quand_aucun_utilisateur() {
        when(userRepository.count()).thenReturn(0L);
        assertThat(service.isSetupRequired()).isTrue();
    }

    @Test
    void installation_non_requise_quand_un_utilisateur_existe() {
        when(userRepository.count()).thenReturn(1L);
        assertThat(service.isSetupRequired()).isFalse();
    }

    @Test
    void le_verrou_evite_de_requeter_apres_premiere_observation_d_utilisateurs() {
        when(userRepository.count()).thenReturn(1L);
        assertThat(service.isSetupRequired()).isFalse();   // observe des users → verrouille
        assertThat(service.isSetupRequired()).isFalse();   // ne re-requête plus
        verify(userRepository, times(1)).count();
    }

    // ── complete : chemin nominal ─────────────────────────────────────────────

    @Test
    void complete_cree_admin_clinique_et_config() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(clinicRepository.findByCodeIgnoreCase("PRINCIPALE")).thenReturn(Optional.empty());
        when(clinicRepository.save(any(Clinic.class))).thenAnswer(inv -> inv.getArgument(0));
        when(clinicConfigRepository.findByClinicId(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("motdepasse1")).thenReturn("HASH");

        SetupForm f = validForm();
        f.setModuleMaternity(true);
        f.setCurrency("XOF");
        service.complete(f);

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        User admin = userCap.getValue();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        assertThat(admin.getPassword()).isEqualTo("HASH");
        assertThat(admin.isActive()).isTrue();

        ArgumentCaptor<ClinicConfig> cfgCap = ArgumentCaptor.forClass(ClinicConfig.class);
        verify(clinicConfigRepository).save(cfgCap.capture());
        ClinicConfig cfg = cfgCap.getValue();
        assertThat(cfg.getName()).isEqualTo("Clinique du Centre");
        assertThat(cfg.getCurrency()).isEqualTo("XOF");
        assertThat(cfg.isModuleMaternity()).isTrue();

        verify(clinicRepository).save(any(Clinic.class));
    }

    @Test
    void complete_refuse_si_deja_installe() {
        when(userRepository.count()).thenReturn(1L); // un utilisateur existe déjà
        assertThatThrownBy(() -> service.complete(validForm()))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    // ── complete : validation ─────────────────────────────────────────────────

    @Test
    void rejette_nom_utilisateur_vide() {
        when(userRepository.count()).thenReturn(0L);
        SetupForm f = validForm();
        f.setAdminUsername("  ");
        assertThatThrownBy(() -> service.complete(f)).isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejette_nom_utilisateur_deja_pris() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.existsByUsername("admin")).thenReturn(true);
        assertThatThrownBy(() -> service.complete(validForm())).isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejette_nom_clinique_vide() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        SetupForm f = validForm();
        f.setClinicName(" ");
        assertThatThrownBy(() -> service.complete(f)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejette_mot_de_passe_trop_court() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        SetupForm f = validForm();
        f.setAdminPassword("court");
        f.setAdminPasswordConfirm("court");
        assertThatThrownBy(() -> service.complete(f)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejette_confirmation_non_concordante() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        SetupForm f = validForm();
        f.setAdminPasswordConfirm("autremotdepasse");
        assertThatThrownBy(() -> service.complete(f)).isInstanceOf(IllegalArgumentException.class);
    }
}
