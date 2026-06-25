package com.clinic.backend.export;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Export PDF / Excel (P2.3) : les endpoints binaires renvoient un document valide
 * avec le bon type MIME. Le reçu PDF exerce toute la chaîne Thymeleaf→jsoup→openhtmltopdf.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// P6 : l'OWNER couvre facturation + rapports financiers/activité/épidémio (pilotage).
// Les bulletins labo/radio (PHI clinique) sont exercés par un médecin (override par méthode).
@WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
class ExportTest {

    @Autowired MockMvc mvc;

    @Test
    void recu_pdf_est_un_pdf_valide() throws Exception {
        MvcResult res = mvc.perform(get("/billing/invoices/1/receipt/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();
        byte[] body = res.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThan(500);
        assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");   // entête PDF
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void bulletin_labo_pdf_valide() throws Exception {
        assertPdf("/lab/requests/1/bulletin/pdf");
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void bulletin_radio_pdf_valide() throws Exception {
        assertPdf("/radiology/requests/1/bulletin/pdf");
    }

    @Test
    void rapport_financier_pdf_valide() throws Exception {
        assertPdf("/reports/financial/pdf");
    }

    @Test
    void rapport_activite_pdf_valide() throws Exception {
        assertPdf("/reports/activity/pdf");
    }

    @Test
    void rapport_epidemiologie_pdf_valide() throws Exception {
        assertPdf("/reports/epidemiology/pdf");
    }

    private void assertPdf(String url) throws Exception {
        MvcResult res = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();
        byte[] body = res.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThan(500);
        assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void impayes_excel_est_un_xlsx_valide() throws Exception {
        MvcResult res = mvc.perform(get("/reports/outstanding/excel"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();
        byte[] body = res.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThan(200);
        // un .xlsx est un zip → magic « PK »
        assertThat(body[0]).isEqualTo((byte) 0x50);
        assertThat(body[1]).isEqualTo((byte) 0x4B);
    }
}
