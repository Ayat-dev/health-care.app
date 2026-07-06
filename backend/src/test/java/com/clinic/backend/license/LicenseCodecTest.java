package com.clinic.backend.license;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie la signature/vérification Ed25519 hors-ligne des clés de licence, sans Spring.
 */
class LicenseCodecTest {

    private static KeyPair ed25519() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static License sample(LocalDate expires) {
        return new License("LIC-TEST-1", "Clinique Test", "STANDARD",
                List.of("pharmacy", "lab"), 10, LocalDate.now(), expires);
    }

    @Test
    void encode_then_verify_roundtrips_all_fields() throws Exception {
        KeyPair kp = ed25519();
        License original = sample(LocalDate.now().plusDays(365));

        String token = LicenseCodec.encode(original, kp.getPrivate());
        License decoded = LicenseCodec.verify(token, kp.getPublic());

        assertThat(decoded.id()).isEqualTo("LIC-TEST-1");
        assertThat(decoded.clinic()).isEqualTo("Clinique Test");
        assertThat(decoded.edition()).isEqualTo("STANDARD");
        assertThat(decoded.features()).containsExactly("pharmacy", "lab");
        assertThat(decoded.maxUsers()).isEqualTo(10);
        assertThat(decoded.expires()).isEqualTo(original.expires());
    }

    @Test
    void verify_rejects_token_signed_by_another_key() throws Exception {
        String token = LicenseCodec.encode(sample(LocalDate.now().plusDays(30)), ed25519().getPrivate());
        PublicKey otherPublic = ed25519().getPublic();

        assertThatThrownBy(() -> LicenseCodec.verify(token, otherPublic))
                .isInstanceOf(LicenseException.class);
    }

    @Test
    void verify_rejects_tampered_payload() throws Exception {
        KeyPair kp = ed25519();
        String token = LicenseCodec.encode(sample(LocalDate.now().plusDays(30)), kp.getPrivate());

        // Altère un caractère de la charge utile (avant le point).
        int dot = token.indexOf('.');
        char[] chars = token.toCharArray();
        chars[2] = (chars[2] == 'A') ? 'B' : 'A';
        String tampered = new String(chars);

        assertThatThrownBy(() -> LicenseCodec.verify(tampered, kp.getPublic()))
                .isInstanceOf(LicenseException.class);
        assertThat(dot).isPositive();
    }

    @Test
    void verify_rejects_garbage() throws Exception {
        PublicKey pub = ed25519().getPublic();
        assertThatThrownBy(() -> LicenseCodec.verify("not-a-token", pub))
                .isInstanceOf(LicenseException.class);
        assertThatThrownBy(() -> LicenseCodec.verify("", pub))
                .isInstanceOf(LicenseException.class);
    }

    @Test
    void license_expiry_check() {
        License lic = sample(LocalDate.of(2026, 1, 1));
        assertThat(lic.isExpiredOn(LocalDate.of(2025, 12, 31))).isFalse();
        assertThat(lic.isExpiredOn(LocalDate.of(2026, 1, 1))).isFalse();
        assertThat(lic.isExpiredOn(LocalDate.of(2026, 1, 2))).isTrue();
    }
}
