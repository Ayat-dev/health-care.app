package com.clinic.backend.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Multi-tenant à discriminant (P4.2) — fournit à Hibernate l'identifiant de tenant
 * (= {@code clinic_id}) à appliquer aux entités annotées {@code @TenantId}.
 * <p>
 * Hibernate 6 ajoute alors automatiquement {@code WHERE clinic_id = ?} aux lectures et
 * renseigne la colonne aux écritures. Quand aucune clinique n'est résolue, on renvoie
 * une <b>sentinelle</b> ({@link #SYSTEM_TENANT}) qui ne correspond à aucune clinique réelle :
 * les lectures ne ramènent rien (comportement « fail-closed »), plutôt que de fuiter
 * entre cliniques.
 */
public class ClinicTenantResolver implements CurrentTenantIdentifierResolver<Long> {

    /** Tenant sentinelle « fermé » : utilisé quand aucune clinique n'est résolue. */
    public static final Long SYSTEM_TENANT = 0L;

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long clinicId = TenantContext.currentClinicId();
        return clinicId != null ? clinicId : SYSTEM_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
