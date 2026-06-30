package com.clinic.backend.metrics;

import com.clinic.backend.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Métriques métier (D2a) — compteurs Micrometer exposés sur {@code /actuator/prometheus}
 * et visualisés dans Grafana. Chaque mesure porte le label {@code clinic_id} (résolu via
 * {@link TenantContext}) pour un découpage multi-tenant des dashboards.
 * <p>
 * On enregistre les compteurs <b>à la volée</b> ({@code Counter.builder(...).register}) :
 * Micrometer déduplique par nom+tags, donc un même (métrique, clinique, mode) renvoie
 * toujours le même compteur. La cardinalité reste bornée (nombre de cliniques × modes de
 * paiement), sans risque d'explosion. Aucune exception ne remonte à l'appelant : la métrique
 * ne doit jamais casser une transaction métier.
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Label de clinique pour la requête courante ; {@code "unknown"} hors contexte tenant. */
    private String clinicTag() {
        Long id = TenantContext.currentClinicId();
        return id != null ? id.toString() : "unknown";
    }

    /** Une consultation vient d'être clôturée (statut TERMINE). */
    public void consultationCompleted() {
        Counter.builder("clinicapp.consultations.completed")
                .description("Consultations clôturées")
                .tag("clinic_id", clinicTag())
                .register(registry)
                .increment();
    }

    /** Un encaissement vient d'être enregistré : incrémente le nombre et le montant cumulé. */
    public void paymentRecorded(String method, BigDecimal amount) {
        String clinic = clinicTag();
        String paymentMethod = (method != null && !method.isBlank()) ? method.trim() : "INCONNU";

        Counter.builder("clinicapp.payments.recorded")
                .description("Encaissements enregistrés (nombre)")
                .tag("clinic_id", clinic)
                .tag("method", paymentMethod)
                .register(registry)
                .increment();

        if (amount != null) {
            Counter.builder("clinicapp.payments.amount")
                    .description("Montant total encaissé (XOF)")
                    .tag("clinic_id", clinic)
                    .tag("method", paymentMethod)
                    .register(registry)
                    .increment(amount.doubleValue());
        }
    }
}
