package com.clinic.backend.web;

import com.clinic.backend.appointment.AppointmentService;
import com.clinic.backend.dto.AppointmentDto;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Télémédecine légère (P3.7) : activation d'une téléconsultation (génération de salle)
 * et page de jonction. Le personnel soignant accède à la salle de tout rendez-vous.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
class TeleconsultationTest {

    @Autowired MockMvc mvc;
    @Autowired AppointmentService appointmentService;
    @Autowired ClinicRepository clinicRepository;

    // multi-tenant (P4.2) : les appels directs au service (hors requête MockMvc, où le contexte
    // de sécurité est purgé en fin de requête) résolvent le tenant via cet override explicite.
    @BeforeEach
    void setTenant() {
        TenantContext.set(clinicRepository.findByCodeIgnoreCase("CENTRALE").orElseThrow().getId());
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void activation_genere_une_salle_et_un_lien() throws Exception {
        // Active la téléconsultation sur un RDV existant
        mvc.perform(post("/appointments/1/teleconsultation").with(csrf()))
                .andExpect(status().is3xxRedirection());

        AppointmentDto dto = appointmentService.getDtoById(1L);
        assertThat(dto.getType()).isEqualTo("TELECONSULTATION");
        assertThat(dto.getTeleconsultationRoom()).startsWith("clinic-");
        assertThat(dto.getTeleconsultationUrl()).startsWith("https://meet.jit.si/clinic-");
    }

    @Test
    void page_de_jonction_affiche_le_lien_visio() throws Exception {
        appointmentService.enableTeleconsultation(1L);

        mvc.perform(get("/teleconsultation/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rejoindre la salle")))
                .andExpect(content().string(containsString("meet.jit.si/clinic-")));
    }

    @Test
    void rdv_sans_teleconsultation_affiche_lien_indisponible() throws Exception {
        // Le RDV 2 n'est pas une téléconsultation → pas de lien
        mvc.perform(get("/teleconsultation/2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aucun lien de téléconsultation")));
    }

    // petit helper CSRF pour le POST sur la chaîne web (session)
    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
