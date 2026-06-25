package com.clinic.backend.model;

/**
 * Source of truth for the set of assignable user roles. Stored on {@link User#getRole()}
 * as the enum name (String). Used by the admin user-management UI to populate the role
 * selector and to validate role assignment.
 */
public enum Role {
    /** Tenant transverse (multi-tenant P4.2) : gère le registre des cliniques. Non assignable depuis /admin/users. */
    SUPER_ADMIN,
    /** Propriétaire/exploitant business de la clinique : finances, catalogues commerciaux, stats agrégées — JAMAIS de PHI. Plateforme : desktop (cockpit) + web. */
    OWNER,
    ADMIN,
    MEDECIN,
    INFIRMIER,
    SECRETAIRE,
    PHARMACIEN,
    LABORANTIN,
    CAISSIER,
    PATIENT
}
