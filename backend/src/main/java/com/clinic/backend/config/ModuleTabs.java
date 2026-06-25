package com.clinic.backend.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.clinic.backend.config.Module.*;

/**
 * Sous-navigation par module (P6 WS5, couche 1 — « sans jonglage »).
 * <p>
 * Registre central, dans la même philosophie que {@link Module}/{@link RoleProfile} :
 * ajouter une barre d'onglets à un module = une entrée ici. {@code GlobalModelAdvice}
 * expose les onglets du module courant à {@code base.html}, qui rend une barre d'onglets
 * persistante sous le fil d'Ariane — aucune sous-vue n'est plus un cul-de-sac.
 * <p>
 * L'onglet actif est calculé par préfixe d'URL le plus long (cf.
 * {@code GlobalModelAdvice.activeTabUrl}) : sur {@code /pharmacy/stock/receive}, l'onglet
 * « Stock » ({@code /pharmacy/stock}) est actif, pas « Tableau de bord » ({@code /pharmacy}).
 */
public final class ModuleTabs {

    /**
     * Un onglet : clé i18n du libellé + URL de destination + rôles autorisés.
     * {@code roles} vide = visible à tout rôle qui voit déjà le module (cas mono-rôle,
     * ex. Pharmacie = PHARMACIEN). Pour un module hétérogène (Rapports : OWNER/MEDECIN/
     * CAISSIER/SECRETAIRE n'ont pas les mêmes sous-pages), on restreint onglet par onglet
     * pour ne jamais présenter une destination en 403 (cf. règle UX « empty-nav-state »).
     */
    public record Tab(String labelKey, String url, Set<String> roles) {
        public Tab(String labelKey, String url) { this(labelKey, url, Set.of()); }
        public boolean visibleTo(String role) {
            return roles.isEmpty() || (role != null && roles.contains(role));
        }
    }

    private static final Map<Module, List<Tab>> TABS = Map.of(
        PHARMACY, List.of(
            new Tab("nav.dashboard",                "/pharmacy"),
            new Tab("tab.pharmacy.drugs",           "/pharmacy/drugs"),
            new Tab("tab.pharmacy.stock",           "/pharmacy/stock"),
            new Tab("tab.pharmacy.dispensations",   "/pharmacy/dispensations"),
            new Tab("tab.pharmacy.prescriptions",   "/pharmacy/prescriptions")
        ),
        // Facturation — toutes les sous-pages partagent l'autorisation de classe
        // (OWNER/CAISSIER/SECRETAIRE) → onglets non gatés.
        BILLING, List.of(
            new Tab("nav.dashboard",          "/billing"),
            new Tab("tab.billing.queue",      "/billing/queue"),
            new Tab("tab.billing.invoices",   "/billing/invoices")
        ),
        // Rapports — hétérogène : chaque onglet est gaté sur les rôles qui y ont droit
        // (mêmes règles que les @PreAuthorize de ReportWebController, P6 WS3).
        REPORTS, List.of(
            new Tab("tab.reports.cockpit",      "/reports",              Set.of("OWNER")),
            new Tab("tab.reports.financial",    "/reports/financial",    Set.of("OWNER", "CAISSIER")),
            new Tab("tab.reports.activity",     "/reports/activity",     Set.of("MEDECIN", "OWNER")),
            new Tab("tab.reports.epidemiology", "/reports/epidemiology", Set.of("MEDECIN", "OWNER")),
            new Tab("tab.reports.outstanding",  "/reports/outstanding",  Set.of("OWNER", "CAISSIER", "SECRETAIRE"))
        )
    );

    private ModuleTabs() {}

    /** Onglets du module donné, ou liste vide si le module n'a pas de sous-navigation. */
    public static List<Tab> forModule(Module module) {
        return module == null ? List.of() : TABS.getOrDefault(module, List.of());
    }
}
