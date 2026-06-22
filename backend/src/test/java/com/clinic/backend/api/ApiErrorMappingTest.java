package com.clinic.backend.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mapping des erreurs API (P1.4 + P1.4b) via le {@code GlobalExceptionHandler} :
 * ressource adressée absente (lookup par id) → 404 ; FK invalide dans un body de
 * création → 400 ; surpaiement → 400 ; facture soldée → 409. Format JSON standard.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiErrorMappingTest {

    @Autowired MockMvc mvc;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void get_facture_inexistante_renvoie_404() throws Exception {
        mvc.perform(get("/api/billing/invoices/99999"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.status").value(404))
           .andExpect(jsonPath("$.path").value("/api/billing/invoices/99999"));
    }

    @Test
    @WithMockUser(username = "cash", roles = "CAISSIER")
    void create_facture_avec_patient_inexistant_renvoie_400() throws Exception {
        // FK de body invalide = validation de requête → 400 (pas 404)
        String body = "{\"patientId\":99999,\"items\":[{\"description\":\"x\",\"quantity\":1,\"unitPrice\":100}]}";
        mvc.perform(post("/api/billing/invoices")
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    // multi-tenant (P4.2) : la facture seedée (clinic1) n'est visible que sous un vrai
    // utilisateur de cette clinique → @WithUserDetails (le mock n'a pas de clinique).
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void surpaiement_renvoie_400() throws Exception {
        String body = "{\"amount\":99999999,\"method\":\"ESPECES\"}";
        mvc.perform(post("/api/billing/invoices/1/pay")
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
    }

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void paiement_sur_facture_soldee_renvoie_409() throws Exception {
        String body = "{\"amount\":100,\"method\":\"ESPECES\"}";
        mvc.perform(post("/api/billing/invoices/2/pay")
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isConflict());
    }
}
