package com.clinic.backend.export;

import com.clinic.backend.radiology.RadiologyService;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D4a — exports {@code ?format=pdf|excel} de l'API rapports (clients API/desktop) +
 * embarquement base64 des images dans le bulletin radiologie PDF.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportApiExportTest {

    @Autowired MockMvc mvc;
    @Autowired RadiologyService radiologyService;
    @Autowired ClinicRepository clinicRepository;

    /** 1×1 PNG transparent (signature + IHDR/IDAT/IEND valides). */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    // ── API : ?format=pdf | excel | (défaut JSON) ───────────────────────────────

    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void rapport_financier_api_pdf() throws Exception {
        assertPdf("/api/reports/monthly-financial?format=pdf");
    }

    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void impayes_api_pdf() throws Exception {
        assertPdf("/api/reports/outstanding?format=pdf");
    }

    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void impayes_api_excel() throws Exception {
        MvcResult res = mvc.perform(get("/api/reports/outstanding").param("format", "excel"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml")))
                .andReturn();
        byte[] b = res.getResponse().getContentAsByteArray();
        assertThat(b.length).isGreaterThan(200);
        assertThat(b[0]).isEqualTo((byte) 0x50); // PK (zip)
        assertThat(b[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void rapport_financier_api_json_par_defaut() throws Exception {
        mvc.perform(get("/api/reports/monthly-financial"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.year").exists());
    }

    // ── Bulletin radiologie : image embarquée en base64 dans le PDF ─────────────

    @Test
    void bulletin_dto_embarque_les_images_en_base64() {
        Long centrale = clinicRepository.findByCodeIgnoreCase("CENTRALE").orElseThrow().getId();
        TenantContext.callAs(centrale, () -> {
            radiologyService.addImage(1L,
                    new MockMultipartFile("file", "cliche.png", "image/png", PNG), "cliché test");
            var dto = radiologyService.getBulletinDto(1L);
            assertThat(dto.getImages()).isNotEmpty();
            assertThat(dto.getImages().get(0).getDataUri()).startsWith("data:image/png;base64,");
            return null;
        });
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void bulletin_radio_pdf_apres_upload_est_valide() throws Exception {
        mvc.perform(multipart("/radiology/requests/2/images")
                        .file(new MockMultipartFile("file", "cliche.png", "image/png", PNG))
                        .param("caption", "cliché test")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertPdf("/radiology/requests/2/bulletin/pdf");
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
}
