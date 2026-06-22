package com.clinic.backend.fhir;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Interopérabilité FHIR R4 (P2.1). Les ressources sont projetées depuis les données
 * amorcées (p1 = Aminata Diallo / consultation c1 / ordonnance + résultats labo).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FhirApiTest {

    @Autowired MockMvc mvc;

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void patient_read_renvoie_une_ressource_fhir_valide() throws Exception {
        mvc.perform(get("/fhir/Patient/1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.resourceType").value("Patient"))
           .andExpect(jsonPath("$.name[0].family").value("Diallo"))
           .andExpect(jsonPath("$.gender").value("female"))
           .andExpect(jsonPath("$.identifier[0].value").value("PAT-2026-00001"));
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void patient_search_renvoie_un_bundle() throws Exception {
        mvc.perform(get("/fhir/Patient").param("name", "Diallo"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.resourceType").value("Bundle"))
           .andExpect(jsonPath("$.type").value("searchset"))
           .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void encounter_read_reference_le_patient() throws Exception {
        mvc.perform(get("/fhir/Encounter/1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.resourceType").value("Encounter"))
           .andExpect(jsonPath("$.subject.reference").value("Patient/1"));
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void observation_search_combine_constantes_et_labo() throws Exception {
        // c1 (p1) porte des constantes ; lr1 (p1) porte des résultats labo (glycémie anormale).
        mvc.perform(get("/fhir/Observation").param("patient", "1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.resourceType").value("Bundle"))
           .andExpect(content().string(containsString("vital-1-")))
           .andExpect(content().string(containsString("\"id\":\"lab-")));
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void medicationrequest_search_liste_les_lignes_ordonnance() throws Exception {
        mvc.perform(get("/fhir/MedicationRequest").param("patient", "1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.resourceType").value("Bundle"))
           .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(2)))
           .andExpect(content().string(containsString("Oméprazole")));
    }

    @Test
    void metadata_est_public_et_renvoie_un_capabilitystatement() throws Exception {
        mvc.perform(get("/fhir/metadata"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.resourceType").value("CapabilityStatement"));
    }

    @Test
    void fhir_sans_token_est_refuse() throws Exception {
        mvc.perform(get("/fhir/Patient/1"))
           .andExpect(status().is4xxClientError());
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void ressource_inexistante_renvoie_404_operationoutcome() throws Exception {
        mvc.perform(get("/fhir/Patient/99999"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
           .andExpect(jsonPath("$.issue[0].code").value("not-found"));
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void parametre_patient_manquant_renvoie_400_operationoutcome() throws Exception {
        mvc.perform(get("/fhir/Observation"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
           .andExpect(jsonPath("$.issue[0].code").value("required"));
    }
}
