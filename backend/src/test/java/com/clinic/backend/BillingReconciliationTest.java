package com.clinic.backend;

import com.clinic.backend.billing.BillingService;
import com.clinic.backend.billing.Invoice;
import com.clinic.backend.dto.InvoiceItemDto;
import com.clinic.backend.dto.PaymentDto;
import com.clinic.backend.dto.ReconciliationReportDto;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Z4b — rapprochement manuel des paiements par QR (AmanaTa/MyNITA). Vérifie que la vue ne
 * liste que les modes QR, que le marquage bascule l'état + les compteurs, le filtre
 * « non-rapprochés seulement », et le gating (CAISSIER oui, SECRETAIRE non).
 *
 * NB tenant (P4.2, même patron que {@code BillingAccumulationTest}) : tenant figé en
 * {@code @BeforeTransaction} sur CENTRALE ; caissier courant via {@code @WithUserDetails}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BillingReconciliationTest {

    private static final String UDS = "userDetailsServiceImpl";
    private static final long P1 = 1L; // patient seedé (CENTRALE)

    @Autowired BillingService billingService;
    @Autowired ClinicRepository clinicRepository;
    @Autowired MockMvc mvc;

    @BeforeTransaction
    void setTenant() {
        TenantContext.set(clinicRepository.findByCodeIgnoreCase("CENTRALE").orElseThrow().getId());
    }

    @AfterTransaction
    void clearTenant() {
        TenantContext.clear();
    }

    private static InvoiceItemDto line(String desc, String price) {
        InvoiceItemDto i = new InvoiceItemDto();
        i.setDescription(desc);
        i.setQuantity(1);
        i.setUnitPrice(new BigDecimal(price));
        return i;
    }

    private Long payQr(String method, String amount, String reference) {
        Invoice inv = billingService.addCharge(P1, "CONSULTATION",
                System.nanoTime(), List.of(line("Consultation", "10000")));
        PaymentDto pay = new PaymentDto();
        pay.setAmount(new BigDecimal(amount));
        pay.setMethod(method);
        pay.setReference(reference);
        billingService.recordPayment(inv.getId(), pay);
        return inv.getId();
    }

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = UDS)
    void rapprochement_liste_marque_et_compte_les_paiements_qr() {
        payQr("AMANATA", "2000", null);   // QR sans référence → pending + missing ref

        ReconciliationReportDto before = billingService.reconciliationReport(LocalDate.now(), false);
        assertThat(before.getTotal()).isEqualTo(1);
        assertThat(before.getMissingReferenceCount()).isEqualTo(1);
        PaymentDto qr = before.getPayments().get(0);
        assertThat(qr.getMethod()).isEqualTo("AMANATA");
        assertThat(qr.isReconciled()).isFalse();
        assertThat(before.getPendingCount()).isEqualTo(1);

        // Marquage rapproché → bascule état + compteurs
        billingService.toggleReconciled(qr.getId());
        ReconciliationReportDto after = billingService.reconciliationReport(LocalDate.now(), false);
        PaymentDto qr2 = after.getPayments().stream()
                .filter(p -> p.getId().equals(qr.getId())).findFirst().orElseThrow();
        assertThat(qr2.isReconciled()).isTrue();
        assertThat(qr2.getReconciledByName()).isNotBlank();
        assertThat(after.getPendingCount()).isZero();
        assertThat(after.getReconciledCount()).isEqualTo(1);

        // Filtre « non-rapprochés seulement » exclut le rapproché (mais garde le compteur global)
        ReconciliationReportDto pendingView = billingService.reconciliationReport(LocalDate.now(), true);
        assertThat(pendingView.getTotal()).isEqualTo(1);
        assertThat(pendingView.getPayments()).isEmpty();
    }

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = UDS)
    void seuls_les_modes_qr_apparaissent() {
        payQr("ESPECES", "1000", null); // non-QR → ne doit PAS apparaître
        ReconciliationReportDto report = billingService.reconciliationReport(LocalDate.now(), false);
        assertThat(report.getTotal()).isZero();
        assertThat(report.getPayments()).isEmpty();
    }

    @Test
    @WithMockUser(username = "cais", roles = "CAISSIER")
    void caissier_voit_la_page() throws Exception {
        mvc.perform(get("/billing/reconciliation")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "sec", roles = "SECRETAIRE")
    void secretaire_refuse() throws Exception {
        mvc.perform(get("/billing/reconciliation")).andExpect(status().isForbidden());
    }
}
