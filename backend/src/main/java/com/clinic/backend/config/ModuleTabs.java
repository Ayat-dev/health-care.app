package com.clinic.backend.config;

import java.util.List;
import java.util.Map;

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

    /** Un onglet : clé i18n du libellé + URL de destination. */
    public record Tab(String labelKey, String url) {}

    private static final Map<Module, List<Tab>> TABS = Map.of(
        PHARMACY, List.of(
            new Tab("nav.dashboard",                "/pharmacy"),
            new Tab("tab.pharmacy.drugs",           "/pharmacy/drugs"),
            new Tab("tab.pharmacy.stock",           "/pharmacy/stock"),
            new Tab("tab.pharmacy.dispensations",   "/pharmacy/dispensations"),
            new Tab("tab.pharmacy.prescriptions",   "/pharmacy/prescriptions")
        )
    );

    private ModuleTabs() {}

    /** Onglets du module donné, ou liste vide si le module n'a pas de sous-navigation. */
    public static List<Tab> forModule(Module module) {
        return module == null ? List.of() : TABS.getOrDefault(module, List.of());
    }
}
