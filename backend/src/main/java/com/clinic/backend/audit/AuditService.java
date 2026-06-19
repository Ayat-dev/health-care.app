package com.clinic.backend.audit;

import com.clinic.backend.model.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Écrit et relit le journal d'audit. L'écriture se fait dans une transaction
 * SÉPARÉE ({@link Propagation#REQUIRES_NEW}) pour que la trace persiste
 * indépendamment de la transaction métier appelante.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository repository;

    /** Écrit une entrée d'audit. Ne lève jamais d'exception vers l'appelant. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, Long entityId, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setDetails(details);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                Object principal = auth.getPrincipal();
                if (principal instanceof User u) {
                    entry.setUserId(u.getId());
                    entry.setUsername(u.getUsername());
                } else {
                    entry.setUsername(auth.getName());
                }
            } else {
                entry.setUsername("system");
            }

            HttpServletRequest request = currentRequest();
            if (request != null) {
                entry.setIpAddress(clientIp(request));
                String ua = request.getHeader("User-Agent");
                if (ua != null && ua.length() > 255) ua = ua.substring(0, 255);
                entry.setUserAgent(ua);
            }

            repository.save(entry);
        } catch (Exception e) {
            // Une trace d'audit ne doit jamais casser l'action métier.
            log.warn("Échec d'écriture du journal d'audit ({} {} #{}): {}",
                    action, entityType, entityId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLog> search(String username, String entityType, String action,
                                 LocalDateTime from, LocalDateTime to, int limit) {
        return repository.search(
                blankToNull(username), blankToNull(entityType), blankToNull(action),
                from, to, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<String> entityTypes() { return repository.distinctEntityTypes(); }

    @Transactional(readOnly = true)
    public List<String> actions() { return repository.distinctActions(); }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes sra) ? sra.getRequest() : null;
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
