package com.clinic.backend.fhir;

import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.ConsultationRepository;
import com.clinic.backend.consultation.Prescription;
import com.clinic.backend.consultation.PrescriptionItem;
import com.clinic.backend.consultation.PrescriptionItemRepository;
import com.clinic.backend.consultation.PrescriptionRepository;
import com.clinic.backend.lab.LabRequest;
import com.clinic.backend.lab.LabRequestItem;
import com.clinic.backend.lab.LabRequestRepository;
import com.clinic.backend.lab.LabResult;
import com.clinic.backend.lab.LabResultRepository;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Couche de projection FHIR R4 (P2.1) : charge les entités cliniques et les mappe
 * vers des ressources FHIR <b>à l'intérieur de la transaction en lecture seule</b>.
 * Comme OSIV est désactivé, ce point est essentiel : les associations lazy se
 * résolvent ici (session ouverte côté service) et non dans la couche de présentation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FhirService {

    private final PatientRepository patientRepository;
    private final ConsultationRepository consultationRepository;
    private final LabRequestRepository labRequestRepository;
    private final LabResultRepository labResultRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final FhirMapper mapper;

    // ── Patient ───────────────────────────────────────────────────────────────

    public org.hl7.fhir.r4.model.Patient getPatient(Long id) {
        Patient p = patientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
        return mapper.toPatient(p);
    }

    public Bundle searchPatients(String name) {
        List<Patient> patients = patientRepository
                .search(name, PageRequest.of(0, 50)).getContent();
        List<Resource> resources = new ArrayList<>();
        for (Patient p : patients) resources.add(mapper.toPatient(p));
        return bundle(resources);
    }

    // ── Encounter ───────────────────────────────────────────────────────────────

    public Encounter getEncounter(Long id) {
        Consultation c = consultationRepository.findWithRefsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id));
        return mapper.toEncounter(c);
    }

    public Bundle searchEncounters(Long patientId) {
        List<Resource> resources = new ArrayList<>();
        for (Consultation c : consultationRepository.findByPatient(patientId)) {
            resources.add(mapper.toEncounter(c));
        }
        return bundle(resources);
    }

    // ── Observation (labo + constantes vitales) ─────────────────────────────────

    /** L'id FHIR encode la source : {@code lab-{id}} (résultat labo) ou {@code vital-{consultId}-{clé}}. */
    public Observation getObservation(String fhirId) {
        if (fhirId == null) throw new ResourceNotFoundException("Observation", fhirId);

        if (fhirId.startsWith("lab-")) {
            Long resultId = parseId(fhirId.substring("lab-".length()), fhirId);
            LabResult r = labResultRepository.findWithRefsById(resultId)
                    .orElseThrow(() -> new ResourceNotFoundException("Observation", fhirId));
            return mapper.toLabObservation(r);
        }

        if (fhirId.startsWith("vital-")) {
            String rest = fhirId.substring("vital-".length());
            int dash = rest.lastIndexOf('-');
            if (dash <= 0) throw new ResourceNotFoundException("Observation", fhirId);
            Long consultId = parseId(rest.substring(0, dash), fhirId);
            String key = rest.substring(dash + 1);
            Consultation c = consultationRepository.findWithRefsById(consultId)
                    .orElseThrow(() -> new ResourceNotFoundException("Observation", fhirId));
            Observation o = mapper.vitalObservation(c, key);
            if (o == null) throw new ResourceNotFoundException("Observation", fhirId);
            return o;
        }

        throw new ResourceNotFoundException("Observation", fhirId);
    }

    public Bundle searchObservations(Long patientId) {
        List<Resource> resources = new ArrayList<>();
        // Constantes vitales des consultations
        for (Consultation c : consultationRepository.findByPatient(patientId)) {
            resources.addAll(mapper.vitalsToObservations(c));
        }
        // Résultats de laboratoire validés/saisis
        for (LabRequest req : labRequestRepository.findByPatient(patientId)) {
            for (LabRequestItem item : req.getItems()) {
                LabResult res = item.getResult();
                if (res != null) resources.add(mapper.toLabObservation(res));
            }
        }
        return bundle(resources);
    }

    // ── MedicationRequest (lignes d'ordonnance) ─────────────────────────────────

    public MedicationRequest getMedicationRequest(Long itemId) {
        PrescriptionItem item = prescriptionItemRepository.findWithRefsById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("MedicationRequest", itemId));
        return mapper.toMedicationRequest(item);
    }

    public Bundle searchMedicationRequests(Long patientId) {
        List<Resource> resources = new ArrayList<>();
        for (Prescription presc : prescriptionRepository.findByPatientWithItems(patientId)) {
            for (PrescriptionItem item : presc.getItems()) {
                resources.add(mapper.toMedicationRequest(item));
            }
        }
        return bundle(resources);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Bundle bundle(List<Resource> resources) {
        Bundle b = new Bundle();
        b.setType(Bundle.BundleType.SEARCHSET);
        b.setTotal(resources.size());
        for (Resource res : resources) {
            b.addEntry()
                    .setFullUrl(res.fhirType() + "/" + res.getIdElement().getIdPart())
                    .setResource(res);
        }
        return b;
    }

    private Long parseId(String raw, String fhirId) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new ResourceNotFoundException("Observation", fhirId);
        }
    }
}
