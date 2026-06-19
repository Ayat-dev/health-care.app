package com.clinic.backend.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contexte HAPI FHIR R4 partagé (P2.1). Sa création est coûteuse (chargement du
 * modèle) — un seul bean, thread-safe, réutilisé par tous les endpoints {@code /fhir/**}.
 * Les {@code IParser} sont en revanche créés à la demande (non garantis thread-safe).
 */
@Configuration
public class FhirConfig {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }
}
