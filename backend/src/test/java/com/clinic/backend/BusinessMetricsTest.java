package com.clinic.backend;

import com.clinic.backend.metrics.BusinessMetrics;
import com.clinic.backend.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D2a — métriques métier Micrometer. Vérifie que les compteurs portent le label
 * {@code clinic_id} (multi-tenant) et le label {@code method} (encaissements).
 * <p>
 * NB : le rendu Prometheus (suffixe {@code _total}, format {@code clinic_id="…"})
 * n'est pas assertable ici — le {@code PrometheusMeterRegistry}/endpoint
 * {@code /actuator/prometheus} ne sont pas câblés dans le profil de test (gotcha
 * documenté avec le travail Actuator P4.3). La sérialisation se vérifie en dev ;
 * la couverture de test porte sur le registre Micrometer, source de cette sortie.
 */
@SpringBootTest
@ActiveProfiles("test")
class BusinessMetricsTest {

    @Autowired BusinessMetrics businessMetrics;
    @Autowired MeterRegistry registry;

    @Test
    void consultationCompleted_porte_le_label_clinic_id() {
        double before = counter("clinicapp.consultations.completed", "clinic_id", "42");
        TenantContext.runAs(42L, () -> businessMetrics.consultationCompleted());
        assertThat(counter("clinicapp.consultations.completed", "clinic_id", "42"))
                .isEqualTo(before + 1.0);
    }

    @Test
    void paymentRecorded_compte_et_montant_par_clinique_et_mode() {
        TenantContext.runAs(7L, () -> businessMetrics.paymentRecorded("ESPECES", new BigDecimal("1500.00")));
        TenantContext.runAs(7L, () -> businessMetrics.paymentRecorded("ESPECES", new BigDecimal("500.00")));

        assertThat(registry.get("clinicapp.payments.recorded")
                .tag("clinic_id", "7").tag("method", "ESPECES").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("clinicapp.payments.amount")
                .tag("clinic_id", "7").tag("method", "ESPECES").counter().count())
                .isEqualTo(2000.0);
    }

    @Test
    void compteur_distinct_par_clinique() {
        TenantContext.runAs(10L, () -> businessMetrics.consultationCompleted());
        TenantContext.runAs(20L, () -> businessMetrics.consultationCompleted());
        TenantContext.runAs(20L, () -> businessMetrics.consultationCompleted());

        assertThat(counter("clinicapp.consultations.completed", "clinic_id", "10")).isEqualTo(1.0);
        assertThat(counter("clinicapp.consultations.completed", "clinic_id", "20")).isEqualTo(2.0);
    }

    private double counter(String name, String tagKey, String tagValue) {
        var found = registry.find(name).tag(tagKey, tagValue).counter();
        return found != null ? found.count() : 0.0;
    }
}
