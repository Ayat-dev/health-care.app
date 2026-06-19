package com.clinic.backend.audit;

import java.lang.annotation.*;

/**
 * Marque une méthode de service à tracer dans le journal d'audit.
 * {@link AuditAspect} écrit une entrée {@link AuditLog} après chaque exécution
 * réussie (l'auteur, l'IP et l'entité concernée sont déduits automatiquement).
 *
 * <pre>{@code @Audited(action = "CREATE", entity = "Patient")}</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Verbe métier : CREATE, UPDATE, VALIDATE, PAYMENT, CANCEL, DISPENSE, ADMIT, ... */
    String action();

    /** Type d'entité concernée : Patient, LabRequest, Invoice, ... */
    String entity();
}
