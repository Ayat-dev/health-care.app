package com.clinic.backend.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Branche {@link ClinicTenantResolver} dans Hibernate (P4.2).
 * <p>
 * Pour la multitenance <b>à discriminant</b> ({@code @TenantId}), il suffit d'enregistrer
 * le resolver de tenant — pas de {@code MultiTenantConnectionProvider} (réservé aux modes
 * SCHEMA / DATABASE). On passe par {@link HibernatePropertiesCustomizer} pour rester
 * compatible avec l'auto-configuration JPA de Spring Boot.
 */
@Configuration
public class HibernateTenantConfig {

    @Bean
    HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer() {
        ClinicTenantResolver resolver = new ClinicTenantResolver();
        return properties -> properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
