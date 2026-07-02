package com.clinic.backend.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Intercepte les méthodes de service annotées {@link Audited} et enregistre une
 * entrée d'audit APRÈS exécution réussie. L'id de l'entité concernée est déduit :
 * <ol>
 *   <li>de la valeur de retour si elle expose {@code getId()} ;</li>
 *   <li>sinon du premier argument de type {@link Long}.</li>
 * </ol>
 * Toute erreur ici est avalée — l'audit ne doit jamais perturber le métier.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;

    /** Getters « clé métier » lisibles (référence, pas de PHI) tentés dans l'ordre pour {@code details}. */
    private static final String[] KEY_GETTERS = {
            "getInvoiceNumber", "getRequestNumber", "getPrescriptionNumber",
            "getRecordNumber", "getRoomNumber", "getUsername"
    };

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void onAudited(JoinPoint joinPoint, Audited audited, Object result) {
        try {
            Long entityId = extractId(result, joinPoint.getArgs());
            String details = extractDetails(result);
            auditService.record(audited.action(), audited.entity(), entityId, details);
        } catch (Exception e) {
            log.warn("AuditAspect: trace non écrite pour {} {} — {}",
                    audited.action(), audited.entity(), e.getMessage());
        }
    }

    /** Lit la première « clé métier » disponible sur la valeur de retour (n° facture, dossier, login…). */
    private String extractDetails(Object result) {
        if (result == null) return null;
        for (String getter : KEY_GETTERS) {
            try {
                Object value = result.getClass().getMethod(getter).invoke(result);
                if (value instanceof String s && !s.isBlank()) {
                    return getter.substring(3, 4).toLowerCase() + getter.substring(4) + "=" + s;
                }
            } catch (Exception ignored) {
                // getter absent sur ce type → on essaie le suivant
            }
        }
        return null;
    }

    private Long extractId(Object result, Object[] args) {
        Long fromResult = readId(result);
        if (fromResult != null) return fromResult;
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof Long l) return l;
            }
        }
        return null;
    }

    /** Lit {@code getId()} par réflexion si présent et renvoyant un Long. */
    private Long readId(Object obj) {
        if (obj == null) return null;
        try {
            Method getId = obj.getClass().getMethod("getId");
            Object value = getId.invoke(obj);
            return (value instanceof Long l) ? l : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
