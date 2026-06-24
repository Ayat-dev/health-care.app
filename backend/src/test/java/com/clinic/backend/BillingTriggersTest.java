package com.clinic.backend;

import com.clinic.backend.billing.Invoice;
import com.clinic.backend.billing.InvoiceItem;
import com.clinic.backend.billing.InvoiceRepository;
import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.ConsultationService;
import com.clinic.backend.dto.HospitalizationDto;
import com.clinic.backend.dto.LabRequestDto;
import com.clinic.backend.hospitalization.HospitalizationService;
import com.clinic.backend.lab.LabService;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-facturation (P5.1 Lot B) — les 5 déclencheurs synchrones in-tx : clôturer un acte amont
 * (consultation, labo, imagerie, séjour, dispensation) alimente la facture <b>ouverte</b> du
 * patient avec une ligne taguée {@code sourceType}. Couvre ici 3 chemins représentatifs :
 * la méthode utilitaire mono-ligne ({@code chargeConsultation}, {@code chargeHospitalization})
 * et la construction de lignes par item côté service ({@code LabService.validate} → 1 ligne/test).
 * Radiologie/dispensation partagent le même {@code addCharge} (couvert par {@link BillingAccumulationTest}).
 *
 * NB tenant (P4.2) : figé en {@code @BeforeTransaction} sur la clinique seedée (CENTRALE).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BillingTriggersTest {

    private static final String UDS = "userDetailsServiceImpl";

    @Autowired ConsultationService consultationService;
    @Autowired LabService labService;
    @Autowired HospitalizationService hospitalizationService;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired ClinicRepository clinicRepository;

    @BeforeTransaction
    void setTenant() {
        TenantContext.set(clinicRepository.findByCodeIgnoreCase("CENTRALE").orElseThrow().getId());
    }

    @AfterTransaction
    void clearTenant() {
        TenantContext.clear();
    }

    private Invoice openInvoiceOf(Long patientId) {
        return invoiceRepository.findOpenByPatient(patientId).stream().findFirst().orElse(null);
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = UDS)
    void cloturer_une_consultation_alimente_la_facture_ouverte() {
        Consultation enCours = consultationService.search(null, null, null, null, "EN_COURS")
                .stream().findFirst().orElseThrow();
        Long patientId = enCours.getPatient().getId();
        assertThat(openInvoiceOf(patientId)).isNull(); // aucune facture ouverte au départ

        enCours.setDiagnosis("Diagnostic test"); // obligatoire pour clôturer
        consultationService.complete(enCours.getId());

        Invoice inv = openInvoiceOf(patientId);
        assertThat(inv).isNotNull();
        assertThat(inv.isOpen()).isTrue();
        assertThat(inv.getItems()).anyMatch(it -> "CONSULTATION".equals(it.getSourceType())
                && enCours.getId().equals(it.getSourceId()));
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = UDS)
    void valider_un_labo_facture_une_ligne_par_analyse() {
        // lr1 (p1) est seedée VALIDE avec 2 résultats (NFS + glycémie) ; la re-valider déclenche la facturation.
        LabRequestDto lr = labService.findForPatient(1L).stream().findFirst().orElseThrow();
        int testCount = lr.getItems().size();

        labService.validate(lr.getId());

        Invoice inv = openInvoiceOf(1L);
        assertThat(inv).isNotNull();
        List<InvoiceItem> labLines = inv.getItems().stream()
                .filter(it -> "LAB".equals(it.getSourceType()) && lr.getId().equals(it.getSourceId()))
                .toList();
        assertThat(labLines).hasSize(testCount); // 1 ligne par analyse
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = UDS)
    void la_sortie_d_hospitalisation_facture_le_sejour() {
        HospitalizationDto stay = hospitalizationService.searchDto("ADMIS")
                .stream().findFirst().orElseThrow();
        Long patientId = stay.getPatientId();

        hospitalizationService.discharge(stay.getId(), "SORTI", "Guérison");

        Invoice inv = openInvoiceOf(patientId);
        assertThat(inv).isNotNull();
        assertThat(inv.getItems()).anyMatch(it -> "HOSPITALIZATION".equals(it.getSourceType())
                && stay.getId().equals(it.getSourceId()));
    }
}
