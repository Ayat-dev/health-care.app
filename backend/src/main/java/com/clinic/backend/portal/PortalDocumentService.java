package com.clinic.backend.portal;

import com.clinic.backend.billing.BillingService;
import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.consultation.PrescriptionService;
import com.clinic.backend.dto.InvoiceDto;
import com.clinic.backend.dto.LabRequestDto;
import com.clinic.backend.dto.PrescriptionDto;
import com.clinic.backend.dto.RadiologyRequestDto;
import com.clinic.backend.export.PdfExportService;
import com.clinic.backend.lab.LabService;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.radiology.RadiologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

/**
 * Génère les documents PDF téléchargeables par le patient depuis son portail (D4b) :
 * bulletins de laboratoire, comptes-rendus d'imagerie, ordonnances et reçus.
 * <p>
 * <b>Cloisonnement</b> : chaque document est vérifié comme appartenant au dossier du
 * patient connecté ({@link PortalService#currentPatient()}) avant rendu — sinon
 * {@link AccessDeniedException} (403). Les résultats labo/imagerie ne sont
 * téléchargeables qu'une fois <b>validés</b> (VALIDE/LIVRE), comme dans le dossier web.
 */
@Service
@RequiredArgsConstructor
public class PortalDocumentService {

    private final PortalService portalService;
    private final PatientService patientService;
    private final ClinicConfigService clinicConfigService;
    private final PdfExportService pdfExportService;
    private final LabService labService;
    private final RadiologyService radiologyService;
    private final PrescriptionService prescriptionService;
    private final BillingService billingService;

    /** Bulletin de laboratoire (résultats validés). */
    public byte[] labBulletinPdf(Long requestId) {
        LabRequestDto request = labService.getDtoById(requestId);
        requireOwnership(request.getPatientId());
        requireValidated(request.getStatus());
        return render("lab/bulletin", "request", request, request.getPatientId());
    }

    /** Compte-rendu d'imagerie (images embarquées en base64 via D4a). */
    public byte[] radiologyBulletinPdf(Long requestId) {
        RadiologyRequestDto request = radiologyService.getBulletinDto(requestId);
        requireOwnership(request.getPatientId());
        requireValidated(request.getStatus());
        return render("radiology/bulletin", "request", request, request.getPatientId());
    }

    /** Ordonnance. */
    public byte[] prescriptionPdf(Long prescriptionId) {
        PrescriptionDto prescription = prescriptionService.getDtoById(prescriptionId);
        requireOwnership(prescription.getPatientId());
        return render("prescriptions/print", "prescription", prescription, prescription.getPatientId());
    }

    /** Reçu de facture. */
    public byte[] receiptPdf(Long invoiceId) {
        InvoiceDto invoice = billingService.getDtoById(invoiceId);
        requireOwnership(invoice.getPatientId());
        return render("billing/invoices/receipt", "invoice", invoice, invoice.getPatientId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private byte[] render(String template, String var, Object document, Long patientId) {
        Map<String, Object> model = new HashMap<>();
        model.put(var, document);
        model.put("config", clinicConfigService.getConfig());
        model.put("patientAge", ageOf(patientId));
        return pdfExportService.renderTemplate(template, model);
    }

    private void requireOwnership(Long documentPatientId) {
        Long mine = portalService.currentPatient().getId();
        if (documentPatientId == null || !documentPatientId.equals(mine)) {
            throw new AccessDeniedException("Ce document n'appartient pas à votre dossier.");
        }
    }

    private void requireValidated(String status) {
        if (!"VALIDE".equals(status) && !"LIVRE".equals(status)) {
            throw new AccessDeniedException("Ce document n'est pas encore disponible.");
        }
    }

    private Integer ageOf(Long patientId) {
        if (patientId == null) return null;
        Patient patient = patientService.getById(patientId);
        return patient.getBirthDate() != null
                ? Period.between(patient.getBirthDate(), LocalDate.now()).getYears()
                : null;
    }
}
