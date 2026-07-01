package com.clinic.backend;

import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.security.mfa.MfaService;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier E3 — cycle de vie MFA au niveau service : enrôlement (secret) → confirmation par TOTP
 * → codes de secours → vérification (TOTP + code de secours à usage unique) → désactivation.
 * {@code users} n'est pas {@code @TenantId} → aucun tenant requis ; {@code @Transactional} annule.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MfaServiceTest {

    @Autowired MfaService mfaService;
    @Autowired UserRepository userRepository;

    private Long adminId() {
        return userRepository.findByUsername("admin").orElseThrow().getId();
    }

    /** Code TOTP valide pour la fenêtre courante (le vérificateur tolère ±1 période). */
    private String validCode(String secret) throws Exception {
        return new DefaultCodeGenerator().generate(secret, Math.floorDiv(new SystemTimeProvider().getTime(), 30));
    }

    @Test
    void cycle_complet_enrolement_verification_desactivation() throws Exception {
        Long id = adminId();
        assertThat(mfaService.isEnabled(id)).isFalse();

        // Enrôlement : secret généré, MFA pas encore actif.
        MfaService.SetupData setup = mfaService.beginSetup(id);
        assertThat(setup.secret()).isNotBlank();
        assertThat(setup.qrDataUri()).startsWith("data:image/"); // QR inline (base64)
        assertThat(mfaService.isEnabled(id)).isFalse();

        // Confirmation par un vrai code TOTP → activé + 8 codes de secours.
        List<String> recovery = mfaService.confirmSetup(id, validCode(setup.secret()));
        assertThat(mfaService.isEnabled(id)).isTrue();
        assertThat(recovery).hasSize(8);
        assertThat(mfaService.remainingRecoveryCodes(id)).isEqualTo(8);

        // Vérification par TOTP.
        assertThat(mfaService.verify(id, validCode(setup.secret()))).isTrue();
        // Code faux → refusé.
        assertThat(mfaService.verify(id, "000000")).isFalse();

        // Un code de secours fonctionne UNE fois, puis est consommé.
        String backup = recovery.get(0);
        assertThat(mfaService.verify(id, backup)).isTrue();
        assertThat(mfaService.remainingRecoveryCodes(id)).isEqualTo(7);
        assertThat(mfaService.verify(id, backup)).isFalse(); // déjà utilisé

        // Désactivation : plus actif, plus de secret, codes purgés.
        mfaService.disable(id);
        assertThat(mfaService.isEnabled(id)).isFalse();
        assertThat(mfaService.remainingRecoveryCodes(id)).isZero();
        User reloaded = userRepository.findById(id).orElseThrow();
        assertThat(reloaded.getMfaSecret()).isNull();
    }
}
