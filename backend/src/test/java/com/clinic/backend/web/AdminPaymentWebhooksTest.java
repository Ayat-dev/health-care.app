package com.clinic.backend.web;

import com.clinic.backend.billing.PaymentWebhookEvent;
import com.clinic.backend.billing.PaymentWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Z4a — journal admin des webhooks Mobile Money. Vérifie le gating SUPER_ADMIN (la table
 * est globale, réservée au rôle transverse), l'affichage d'un évènement et le filtrage.
 * {@code @Transactional} : les évènements amorcés sont visibles par la requête du contrôleur
 * (même transaction) puis annulés — pas de pollution des autres tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminPaymentWebhooksTest {

    @Autowired MockMvc mvc;
    @Autowired PaymentWebhookEventRepository repo;

    private void seed(String txn, String status) {
        PaymentWebhookEvent ev = new PaymentWebhookEvent();
        ev.setProvider("ORANGE_MONEY");
        ev.setTransactionId(txn);
        ev.setInvoiceNumber("FAC-2026-09999");
        ev.setAmount(new BigDecimal("5000"));
        ev.setProviderStatus("SUCCESS");
        ev.setStatus(status);
        repo.save(ev);
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void super_admin_voit_le_journal_avec_ses_evenements() throws Exception {
        seed("TXN-Z4A-1", "PROCESSED");
        mvc.perform(get("/admin/payment-webhooks"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TXN-Z4A-1")))
                .andExpect(content().string(containsString("FAC-2026-09999")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void admin_de_clinique_refuse() throws Exception {
        mvc.perform(get("/admin/payment-webhooks"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void le_filtre_par_statut_exclut_les_autres() throws Exception {
        seed("TXN-Z4A-OK", "PROCESSED");
        seed("TXN-Z4A-KO", "REJECTED");
        mvc.perform(get("/admin/payment-webhooks").param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TXN-Z4A-KO")))
                .andExpect(content().string(not(containsString("TXN-Z4A-OK"))));
    }
}
