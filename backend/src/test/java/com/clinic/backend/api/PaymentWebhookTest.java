package com.clinic.backend.api;

import com.clinic.backend.billing.Invoice;
import com.clinic.backend.billing.InvoiceRepository;
import com.clinic.backend.patient.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webhook Mobile Money (P3.3) : signature HMAC, encaissement automatique,
 * idempotence (anti-rejeu) et rejets métier journalisés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentWebhookTest {

    // Doit correspondre à app.webhook.secret de application-test.properties.
    private static final String SECRET = "clinicapp-test-webhook-secret-not-for-production";

    @Autowired MockMvc mvc;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired PatientRepository patientRepository;

    @Test
    void paiement_valide_encaisse_la_facture() throws Exception {
        Invoice inv = freshInvoice("FAC-WH-OK", "2000");
        String body = json("TXN-OK-1", "FAC-WH-OK", "2000", "SUCCESS");

        mvc.perform(post("/api/payments/webhook/orange")
                        .header("X-Webhook-Signature", sign(body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        Invoice reloaded = invoiceRepository.findById(inv.getId()).orElseThrow();
        assertThat(reloaded.getPaidAmount()).isEqualByComparingTo("2000");
        assertThat(reloaded.getStatus()).isEqualTo("PAYE");
    }

    @Test
    void signature_invalide_401() throws Exception {
        String body = json("TXN-BAD", "FAC-WH-OK", "2000", "SUCCESS");
        mvc.perform(post("/api/payments/webhook/orange")
                        .header("X-Webhook-Signature", "deadbeef")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fournisseur_inconnu_400() throws Exception {
        String body = json("TXN-X", "FAC-WH-OK", "2000", "SUCCESS");
        mvc.perform(post("/api/payments/webhook/paypal")
                        .header("X-Webhook-Signature", sign(body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void facture_introuvable_rejetee_mais_acquittee() throws Exception {
        String body = json("TXN-NOPE", "FAC-INEXISTANTE", "1000", "SUCCESS");
        mvc.perform(post("/api/payments/webhook/wave")
                        .header("X-Webhook-Signature", sign(body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void rejeu_est_idempotent() throws Exception {
        freshInvoice("FAC-WH-DUP", "3000");
        String body = json("TXN-DUP-1", "FAC-WH-DUP", "3000", "SUCCESS");

        // 1er envoi → encaissé
        mvc.perform(post("/api/payments/webhook/mtn")
                        .header("X-Webhook-Signature", sign(body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Rejeu identique → DUPLICATE, pas de double encaissement
        mvc.perform(post("/api/payments/webhook/mtn")
                        .header("X-Webhook-Signature", sign(body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        Invoice reloaded = invoiceRepository.findByInvoiceNumber("FAC-WH-DUP").orElseThrow();
        assertThat(reloaded.getPaidAmount()).isEqualByComparingTo("3000"); // pas 6000
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Invoice freshInvoice(String number, String patientAmount) {
        Invoice inv = new Invoice();
        inv.setInvoiceNumber(number);
        inv.setPatient(patientRepository.findById(1L).orElseThrow());
        inv.setSubtotal(new BigDecimal(patientAmount));
        inv.setPatientAmount(new BigDecimal(patientAmount));
        inv.setStatus("EN_ATTENTE");
        return invoiceRepository.save(inv);
    }

    private static String json(String txn, String invoiceNumber, String amount, String status) {
        return "{\"transactionId\":\"" + txn + "\",\"invoiceNumber\":\"" + invoiceNumber
                + "\",\"amount\":" + amount + ",\"status\":\"" + status + "\"}";
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] d = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                            .append(Character.forDigit(b & 0xF, 16));
        return sb.toString();
    }
}
