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

    /** B2 : l'invite d'installation PWA est présente dans le chrome, masquée par défaut
     *  (js/pwa.js l'affiche sur beforeinstallprompt) et étiquetée via i18n. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void invite_installation_pwa_presente_et_masquee() throws Exception {
        mvc.perform(get("/patients"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"pwa-install-btn\"")))
                .andExpect(content().string(containsString("Installer ClinicApp sur cet appareil")));
    }

    /** i18n slice 2 (docs/I18N-PLAN.md) : agenda jour/semaine porte des clés #{} (dont les
     *  statuts dynamiques #{${'status.' + …}} de la colonne + légende) et bascule en anglais
     *  via ?lang=en. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void appointments_i18n_fr_puis_en() throws Exception {
        mvc.perform(get("/appointments"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Agenda du jour")));
        mvc.perform(get("/appointments").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Week view")));
        // vue semaine : légende des statuts traduite (clés #{status.*})
        mvc.perform(get("/appointments/week").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Week of")));
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

    /** D4d : reliquats i18n — la boîte de notifications est traduite (était FR en dur). */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void notifications_i18n_fr_puis_en() throws Exception {
        mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Boîte de réception")));
        mvc.perform(get("/notifications").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Inbox")));
    }

    /** D4d : reliquats i18n — le tableau de bord médecin est traduit (titres/colonnes en dur). */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void dashboard_medecin_i18n_en() throws Exception {
        mvc.perform(get("/dashboard").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My consultations today")));
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

    /** Fiche de consultation : exerce le bloc « Actions cliniques » consolidé (P6 WS5 c2)
     *  + i18n slice 3 (docs/I18N-PLAN.md) — clés #{} sur la fiche, statut dynamique
     *  #{${'status.' + …}}, bascule en anglais via ?lang=en. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void consultation_detail_i18n_fr_puis_en() throws Exception {
        mvc.perform(get("/consultations/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Actions cliniques")));
        mvc.perform(get("/consultations/1").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Clinical actions")))
                .andExpect(content().string(containsString("Vital signs")));
    }

    /** i18n slice 3 : la liste consultations porte des clés #{} et bascule en anglais. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void consultations_liste_i18n_fr_puis_en() throws Exception {
        mvc.perform(get("/consultations").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("+ New consultation")));
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

    /** i18n slice 4 (docs/I18N-PLAN.md) : le module Facturation porte des clés #{} (tableau de
     *  bord, liste, détail, encaissement) — statuts dynamiques #{${'status.' + …}} + modes de
     *  paiement #{paymethod.*} — et bascule en anglais via ?lang=en. */
    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void billing_i18n_fr_puis_en() throws Exception {
        mvc.perform(get("/billing"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Caisse du jour")));
        mvc.perform(get("/billing").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Collected today")))
                .andExpect(content().string(containsString("Invoices by status")));
        // liste : bouton « + Nouvelle facture » traduit
        mvc.perform(get("/billing/invoices").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("+ New invoice")));
        // détail : reste à charge patient + statut dynamique traduit
        mvc.perform(get("/billing/invoices/1").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Patient out-of-pocket")));
        // encaissement : libellé de mode de paiement (#{paymethod.*}) traduit
        mvc.perform(get("/billing/invoices/1/pay").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Payment method")));
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

    /** i18n slice 8 (docs/I18N-PLAN.md) : le module Hospitalisation (plan des lits, liste,
     *  admission, détail, chambres) porte des clés #{} — statuts dynamiques #{${'status.' + …}},
     *  types de chambre #{${'roomtype.' + …}} — et bascule en anglais via ?lang=en. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void hospitalization_i18n_fr_puis_en() throws Exception {
        // plan des lits : en-tête « occupation en temps réel » en FR
        mvc.perform(get("/hospitalization"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("occupation en temps réel")));
        // liste des séjours : titre « Inpatient stays » traduit
        mvc.perform(get("/hospitalization/list").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Inpatient stays")));
        // formulaire d'admission : libellé « New admission » traduit
        mvc.perform(get("/hospitalization/admit").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("New admission")));
        // référentiel chambres : en-tête « Rooms & beds » traduit
        mvc.perform(get("/hospitalization/rooms").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rooms &amp; beds")));
        // détail séjour : type de chambre dynamique + statut résolus (seed séjour 1 = ADMIS)
        mvc.perform(get("/hospitalization/1").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Attending doctor")));
    }

    /** i18n slice 9 (docs/I18N-PLAN.md) : le module Maternité (liste, formulaire dossier,
     *  dossier à onglets, CPN, accouchement) porte des clés #{} — statuts dynamiques
     *  #{${'status.' + …}}, énumérations partagées #{${'deliverytype.' + …}}/#{${'gender.' + …}} —
     *  et bascule en anglais via ?lang=en. Le seed dossier 1 (p1, EN_COURS, 2 CPN) exerce
     *  l'en-tête + l'onglet visites + l'âge gestationnel. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void maternity_i18n_fr_puis_en() throws Exception {
        // liste : en-tête « Dossiers de grossesse » en FR
        mvc.perform(get("/maternity"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dossiers de grossesse")));
        // liste : titre « Pregnancy records » traduit
        mvc.perform(get("/maternity").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pregnancy records")));
        // formulaire d'ouverture : libellé LMP « Last menstrual period » traduit
        mvc.perform(get("/maternity/new").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Last menstrual period")));
        // dossier : onglet « ANC visits » + « Gestational age » traduits (seed dossier 1 = EN_COURS)
        mvc.perform(get("/maternity/1").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gestational age")));
    }

    /** Détail demande labo : exerce le tableau résultats + le lien latéral dossier patient. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void detail_demande_labo_rend_200() throws Exception {
        mvc.perform(get("/lab/requests/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Analyses &amp; résultats")));
    }

    /** i18n slice 5 (docs/I18N-PLAN.md) : le module Laboratoire (travail du jour, liste,
     *  formulaire, détail) porte des clés #{} — statuts dynamiques #{${'status.' + …}} +
     *  priorités #{priority.*} — et bascule en anglais via ?lang=en. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void lab_i18n_fr_puis_en() throws Exception {
        mvc.perform(get("/lab"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Travail du jour")));
        // travail du jour : colonne partagée (Prescripteur) traduite
        mvc.perform(get("/lab").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Prescriber")));
        // formulaire : libellé « Analyses demandées » traduit
        mvc.perform(get("/lab/requests/new").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Requested tests")));
        // détail : en-tête « Saisi par » traduit + statut dynamique résolu
        mvc.perform(get("/lab/requests/1").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Entered by")));
    }

    /** Détail demande imagerie : exerce le compte-rendu + la galerie d'images (th:alt). */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void detail_demande_imagerie_rend_200() throws Exception {
        mvc.perform(get("/radiology/requests/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Compte-rendu")));
    }

    /** i18n slice 6 (docs/I18N-PLAN.md) : le module Imagerie (travail du jour, liste,
     *  formulaire, détail + compte-rendu) porte des clés #{} — statuts dynamiques
     *  #{${'status.' + …}}, priorités #{priority.*}, types d'examen #{${'examtype.' + …}} —
     *  et bascule en anglais via ?lang=en. */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void radiology_i18n_fr_puis_en() throws Exception {
        mvc.perform(get("/radiology"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Travail du jour")));
        // travail du jour : colonne « Examens » traduite
        mvc.perform(get("/radiology").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Exams")));
        // formulaire : libellé « Examens demandés » traduit + type d'examen dynamique
        mvc.perform(get("/radiology/requests/new").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Requested exams")));
        // détail : compte-rendu « Findings / Description » traduit (seed req 1 = VALIDE avec rapport)
        mvc.perform(get("/radiology/requests/1").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Findings / Description")));
    }

    /** i18n slice 7 (docs/I18N-PLAN.md) : le module Pharmacie (tableau de bord, catalogue,
     *  stock, réception, file des ordonnances, dispensations) porte des clés #{} — colonnes
     *  partagées pharmacy.col.* + statuts de lot + toggle actif/inactif — et bascule en
     *  anglais via ?lang=en. */
    @Test
    @WithUserDetails(value = "pharmacien", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void pharmacy_i18n_fr_puis_en() throws Exception {
        // tableau de bord : carte « Ordonnances à dispenser » en FR
        mvc.perform(get("/pharmacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ordonnances à dispenser")));
        // catalogue : en-tête « Drug catalog » traduit + statut de délivrance
        mvc.perform(get("/pharmacy/drugs").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Drug catalog")));
        // état du stock : colonne partagée « Supplier » traduite
        mvc.perform(get("/pharmacy/stock").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Supplier")));
        // file des ordonnances : titre « Prescriptions to dispense » traduit (heading slice 7)
        mvc.perform(get("/pharmacy/prescriptions").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Prescriptions to dispense")));
    }

    /** i18n slice 10 (docs/I18N-PLAN.md) : le module Rapports (cockpit, bilan financier,
     *  activité, épidémiologie, impayés) porte des clés #{} — statut dynamique des impayés
     *  #{${'status.' + …}}, libellés démographiques (sexe/âge) localisés côté service — et
     *  bascule en anglais via ?lang=en. OWNER a accès à tous les onglets de rapports. */
    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void reports_i18n_fr_puis_en() throws Exception {
        // cockpit : section « Revenus » en FR
        mvc.perform(get("/reports"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Revenus")));
        // cockpit : section « Revenue » traduite
        mvc.perform(get("/reports").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Revenue")));
        // bilan financier : titre « Financial report » traduit
        mvc.perform(get("/reports/financial").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Financial report")));
        // épidémiologie : en-tête « Distribution by age range » + tranche d'âge localisée
        // côté service (« 0-4 yrs ») — prouve l'i18n des libellés démographiques.
        mvc.perform(get("/reports/epidemiology").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Distribution by age range")))
                .andExpect(content().string(containsString("0-4 yrs")));
    }

    /** Slice 11 — Administration : utilisateurs / config / audit traduits FR→EN
     *  (dont le rôle en badge via clé dynamique {@code #{role.*}} et la section QR). */
    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void admin_i18n_fr_puis_en() throws Exception {
        // Utilisateurs : panneau FR + badge de rôle dynamique (admin → « Administrateur »)
        mvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Comptes utilisateurs")))
                .andExpect(content().string(containsString("Administrateur")));
        // Utilisateurs : bascule EN (panneau + rôle traduit)
        mvc.perform(get("/admin/users").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("User accounts")))
                .andExpect(content().string(containsString("Administrator")));
        // Config : titre + section QR marchand traduits en EN
        mvc.perform(get("/admin/config").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Clinic configuration")))
                .andExpect(content().string(containsString("merchant QR")));
        // Journal d'audit : en-têtes de filtre traduits en EN
        mvc.perform(get("/admin/audit").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Audit log")))
                .andExpect(content().string(containsString("Entity type")));
    }

    /** Slice 12 — Portail patient : accueil / mes rendez-vous / mon dossier / demande de RDV
     *  traduits FR→EN (dont le statut dynamique des RDV {@code #{status.*}}). Le compte seedé
     *  {@code patient} (rôle PATIENT, rattaché au dossier de p1) accède à son espace. */
    @Test
    @WithUserDetails(value = "patient", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void portal_i18n_fr_puis_en() throws Exception {
        // Accueil : carte FR « Mon dossier médical »
        mvc.perform(get("/portal"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mon dossier médical")));
        // Accueil : bascule EN
        mvc.perform(get("/portal").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My medical record")))
                .andExpect(content().string(containsString("Upcoming appointments")));
        // Mon dossier : panneaux traduits + en-tête de colonne
        mvc.perform(get("/portal/record").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My invoices")))
                .andExpect(content().string(containsString("Lab results")));
        // Demande de RDV : formulaire traduit
        mvc.perform(get("/portal/appointments/request").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Request an appointment")))
                .andExpect(content().string(containsString("Send request")));
    }
}
