package com.clinic.backend.license;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseRegistryTest {

    @Test
    void append_then_read_roundtrips(@TempDir Path dir) {
        LicenseRegistry registry = new LicenseRegistry(dir.resolve("sub/licenses.jsonl"));

        License lic = new License("LIC-1", "Clinique A", "PRO", List.of("lab"), 5,
                LocalDate.of(2026, 7, 7), LocalDate.of(2027, 7, 7));
        registry.append(LicenseRecord.of(lic, "token-abc"));
        registry.append(LicenseRecord.of(
                new License("LIC-2", "Clinique B", "STANDARD", List.of(), null,
                        LocalDate.of(2026, 7, 7), LocalDate.of(2027, 1, 1)), "token-def"));

        List<LicenseRecord> all = registry.readAll();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).id()).isEqualTo("LIC-1");
        assertThat(all.get(0).clinic()).isEqualTo("Clinique A");
        assertThat(all.get(0).edition()).isEqualTo("PRO");
        assertThat(all.get(0).maxUsers()).isEqualTo(5);
        assertThat(all.get(0).expires()).isEqualTo(LocalDate.of(2027, 7, 7));
        assertThat(all.get(0).token()).isEqualTo("token-abc");
        assertThat(all.get(1).id()).isEqualTo("LIC-2");
        assertThat(all.get(1).maxUsers()).isNull();
    }

    @Test
    void read_missing_file_returns_empty(@TempDir Path dir) {
        assertThat(new LicenseRegistry(dir.resolve("nope.jsonl")).readAll()).isEmpty();
    }
}
