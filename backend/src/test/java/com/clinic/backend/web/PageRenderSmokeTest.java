package com.clinic.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Anti-régression templating (P1.5) : les pages clés rendent en 200 avec un vrai
 * principal (User seedé), ce qui exerce le layout + la sidebar (GlobalModelAdvice).
 * C'est exactement la classe de bug type {@code mod}→{@code navMod} qui 500-ait
 * toutes les pages — un test qui l'aurait attrapé.
 *
 * <p>P6 : depuis le cloisonnement des rôles, l'ADMIN ne voit plus le clinique ni les
 * finances. Chaque page est donc exercée avec un VRAI utilisateur seedé du bon rôle
 * (via {@code @WithUserDetails}) : médecin pour le clinique, caissier pour la caisse,
 * owner pour le pilotage, admin pour le technique.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageRenderSmokeTest {

    @Autowired MockMvc mvc;

    /**
     * Tableau de bord médecin (vue dédiée) rendu avec un VRAI médecin seedé : ses
     * consultations/RDV/labos réels remplissent les boucles {@code th:each}, ce qui
     * exerce les expressions de ligne (dates, badges) qu'un principal mocké — dont
     * le dashboard serait vide — ne testerait pas.
     */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void dashboard_medecin_rend_200_avec_donnees() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                // en-tête de journée personnalisé (P6 WS5 c2) + table des consultations
                .andExpect(content().string(containsString("Bonjour, Dr. Martin")))
                .andExpect(content().string(containsString("Mes consultations du jour")));
    }

    /** Sidebar : icônes SVG (sprite + <use>) au lieu d'emojis (#6 polish UX). Vérifie que le
     *  sprite est inclus (symbole) ET que la nav le référence (use) — donc que l'id concorde. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void patients_rend_200_avec_icones_svg() throws Exception {
        mvc.perform(get("/patients"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"ic-user\"")))
                .andExpect(content().string(containsString("href=\"#ic-user\"")))
                // plus aucun emoji de nav (l'ancien rendu posait l'emoji en texte du <span>)
                .andExpect(content().string(containsString("class=\"nav-icon\"")));
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void appointments_rend_200() throws Exception {
        mvc.perform(get("/appointments")).andExpect(status().isOk());
    }

    /** i18n slice 1 (docs/I18N-PLAN.md) : la liste patients porte des clés #{} et bascule
     *  en anglais via ?lang=en (LocaleChangeInterceptor). Exerce aussi les statuts dynamiques
     *  du dossier (#{${'status.' + …}}) via /patients/1. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void patients_liste_i18n_fr_puis_en() throws Exception {
        mvc.perform(get("/patients"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Liste des patients")));
        mvc.perform(get("/patients").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Patient list")));
    }

    /** Dossier patient : exerce l'agrégat coup d'œil + timeline (P3.6). */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void patient_detail_rend_apercu_et_timeline() throws Exception {
        mvc.perform(get("/patients/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chronologie du dossier")))
                // onglets deep-linkables + ARIA (P6 WS5 c2)
                .andExpect(content().string(containsString("role=\"tablist\"")))
                .andExpect(content().string(containsString("data-tab=\"lab\"")));
    }

    /** Fiche de consultation : exerce le bloc « Actions cliniques » consolidé (P6 WS5 c2). */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void consultation_detail_rend_actions_cliniques() throws Exception {
        mvc.perform(get("/consultations/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Actions cliniques")));
    }

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void billing_rend_200() throws Exception {
        mvc.perform(get("/billing"))
                .andExpect(status().isOk())
                // sous-nav du module Facturation (P6 WS5 c2)
                .andExpect(content().string(containsString("class=\"module-tabs\"")))
                .andExpect(content().string(containsString("href=\"/billing/queue\"")))
                .andExpect(content().string(containsString("href=\"/billing/invoices\"")));
    }

    /** File d'attente caisse (P5.1) : exerce le template + le filtre JS inline. */
    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void file_attente_caisse_rend_200() throws Exception {
        mvc.perform(get("/billing/queue")).andExpect(status().isOk());
    }

    /** Détail facture : liste les paiements → exerce les libellés conviviaux
     *  ({@code @paymentMethods.label}), donc valide la résolution du bean en EL. */
    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void facture_detail_rend_200_avec_libelles_modes() throws Exception {
        mvc.perform(get("/billing/invoices/1")).andExpect(status().isOk());
    }

    /** Cockpit de pilotage financier : réservé à l'OWNER (P6). */
    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void reports_rend_200() throws Exception {
        mvc.perform(get("/reports")).andExpect(status().isOk());
    }

    /** Config clinique : exerce la nouvelle section QR marchand (AmanaTa / MyNITA). */
    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void config_rend_200_avec_section_qr_marchand() throws Exception {
        mvc.perform(get("/admin/config"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("QR marchand")));
    }

    /** Encaissement : exerce la liste de modes mise à jour + le bloc QR togglable. */
    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void encaissement_rend_200_avec_modes_amanata_mynita() throws Exception {
        mvc.perform(get("/billing/invoices/1/pay"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AmanaTa")))
                .andExpect(content().string(containsString("MyNITA")));
    }

    /** File des ordonnances pharmacie (P5.1 Lot C) : exerce le template + la boucle
     *  sur l'ordonnance seedée non dispensée (lignes, patient, médecin). */
    @Test
    @WithUserDetails(value = "pharmacien", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void file_ordonnances_pharmacie_rend_200() throws Exception {
        mvc.perform(get("/pharmacy/prescriptions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ordonnances à dispenser")));
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void journal_audit_rend_200() throws Exception {
        mvc.perform(get("/admin/audit")).andExpect(status().isOk());
    }

    /** Saisie des résultats labo : exerce le bouton « Retour » mutualisé
     *  ({@code fragments/ui :: back}) avec un argument @{...} à variable de chemin. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void saisie_resultats_labo_rend_200_avec_bouton_retour() throws Exception {
        mvc.perform(get("/lab/requests/1/results"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("← Retour")));
    }

    /** Compte-rendu d'imagerie : même bouton « Retour » mutualisé à variable de chemin. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void compte_rendu_imagerie_rend_200_avec_bouton_retour() throws Exception {
        mvc.perform(get("/radiology/requests/1/report"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("← Retour")));
    }

    /** Dossier maternité (polish P6 WS5) : onglets refondus en patron deep-linkable + ARIA
     *  (role=tablist / data-tab), comme le dossier patient — fin du global {@code event}. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void dossier_maternite_rend_onglets_aria() throws Exception {
        mvc.perform(get("/maternity/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("role=\"tablist\"")))
                .andExpect(content().string(containsString("data-tab=\"visits\"")));
    }

    /** Détail séjour : exerce le bloc actions (transfert/sortie) après retrait de la
     *  cross-nav d'en-tête (couverte par fil d'Ariane + onglets, P6 WS5 polish). */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void detail_sejour_rend_200() throws Exception {
        mvc.perform(get("/hospitalization/1")).andExpect(status().isOk());
    }

    /** Détail demande labo : exerce le tableau résultats + le lien latéral dossier patient. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void detail_demande_labo_rend_200() throws Exception {
        mvc.perform(get("/lab/requests/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Analyses &amp; résultats")));
    }

    /** Détail demande imagerie : exerce le compte-rendu + la galerie d'images (th:alt). */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void detail_demande_imagerie_rend_200() throws Exception {
        mvc.perform(get("/radiology/requests/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Compte-rendu")));
    }
}
