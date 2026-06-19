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

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void onAudited(JoinPoint joinPoint, Audited audited, Object result) {
        try {
            Long entityId = extractId(result, joinPoint.getArgs());
            auditService.record(audited.action(), audited.entity(), entityId, null);
        } catch (Exception e) {
            log.warn("AuditAspect: trace non écrite pour {} {} — {}",
                    audited.action(), audited.entity(), e.getMessage());
        }
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
