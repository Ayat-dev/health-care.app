package com.clinic.backend.lab;

import com.clinic.backend.catalog.LabTestCatalog;
import com.clinic.backend.catalog.LabTestCatalogRepository;
import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.ConsultationRepository;
import com.clinic.backend.dto.LabRequestDto;
import com.clinic.backend.dto.LabRequestItemDto;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LabService {

    private static final String PREFIX = "LAB";

    private final LabRequestRepository labRequestRepository;
    private final ConsultationRepository consultationRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final LabTestCatalogRepository labTestCatalogRepository;
    private final ResultAbnormalityChecker abnormalityChecker;

    // ── Listes / recherche ────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<LabRequest> search(LocalDate from, LocalDate to, Long patientId,
                                   Long doctorId, String status, String priority) {
        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        LocalDateTime toTs   = to   != null ? to.plusDays(1).atStartOfDay() : null;
        return labRequestRepository.search(fromTs, toTs, patientId, doctorId, status, priority);
    }

    @Transactional(readOnly = true)
    public List<LabRequestDto> worklist() {
        return labRequestRepository.findWorklist().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<LabRequestDto> findForPatient(Long patientId) {
        return labRequestRepository.findByPatient(patientId).stream().map(this::toDto).toList();
    }

    // ── Détail ────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public LabRequest getById(Long id) {
        return labRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'analyses introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public LabRequestDto getDtoById(Long id) {
        LabRequest r = labRequestRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'analyses introuvable : " + id));
        return toDto(r);
    }

    /** Prefill a new request from a consultation (patient/doctor). */
    @Transactional(readOnly = true)
    public LabRequestDto prefillFromConsultation(Long consultationId, Long patientId) {
        LabRequestDto dto = new LabRequestDto();
        dto.setPriority("NORMAL");
        if (consultationId != null) {
            Consultation c = consultationRepository.findWithRefsById(consultationId)
                    .orElseThrow(() -> new IllegalArgumentException("Consultation introuvable : " + consultationId));
            dto.setConsultationId(c.getId());
            dto.setPatientId(c.getPatient() != null ? c.getPatient().getId() : null);
            dto.setDoctorId(c.getDoctor() != null ? c.getDoctor().getId() : null);
        }
        if (patientId != null && dto.getPatientId() == null) {
            dto.setPatientId(patientId);
        }
        return dto;
    }

    // ── Création ────────────────────────────────────────────────────────────────
    public LabRequest create(LabRequestDto dto) {
        if (dto.getPatientId() == null) {
            throw new IllegalArgumentException("Le patient est obligatoire");
        }
        if (dto.getDoctorId() == null) {
            throw new IllegalArgumentException("Le médecin prescripteur est obligatoire");
        }
        List<Long> testIds = dto.getTestIds();
        if (testIds == null || testIds.stream().filter(java.util.Objects::nonNull).toList().isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez au moins une analyse");
        }

        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(dto.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable : " + dto.getPatientId()));
        User doctor = userRepository.findById(dto.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Médecin introuvable : " + dto.getDoctorId()));

        LabRequest r = new LabRequest();
        r.setPatient(patient);
        r.setDoctor(doctor);
        if (dto.getConsultationId() != null) {
            Consultation c = consultationRepository.findById(dto.getConsultationId())
                    .orElseThrow(() -> new IllegalArgumentException("Consultation introuvable : " + dto.getConsultationId()));
            r.setConsultation(c);
        }
        r.setRequestedAt(LocalDateTime.now());
        r.setPriority("URGENT".equals(dto.getPriority()) ? "URGENT" : "NORMAL");
        r.setNotes(dto.getNotes());
        r.setStatus("EN_ATTENTE");
        r.setRequestNumber(nextNumber());

        for (Long testId : testIds) {
            if (testId == null) continue;
            LabTestCatalog test = labTestCatalogRepository.findById(testId)
                    .orElseThrow(() -> new IllegalArgumentException("Analyse introuvable : " + testId));
            LabRequestItem item = new LabRequestItem();
            item.setTest(test);
            item.setStatus("EN_ATTENTE");
            r.addItem(item);
        }
        return labRequestRepository.save(r);
    }

    // ── Saisie des résultats (laborantin) ────────────────────────────────────────
    /** Enter/update result values for the items of a request. Moves the order to EN_COURS. */
    public LabRequest enterResults(Long requestId, List<LabRequestItemDto> itemInputs) {
        LabRequest r = labRequestRepository.findWithRefsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'analyses introuvable : " + requestId));
        if ("VALIDE".equals(r.getStatus()) || "LIVRE".equals(r.getStatus())) {
            throw new IllegalStateException("Cette demande est déjà validée et ne peut plus être modifiée");
        }
        User laborantin = currentUser();

        for (LabRequestItem item : r.getItems()) {
            LabRequestItemDto in = findInput(itemInputs, item.getId());
            if (in == null) continue;
            String value = in.getResultValue();
            if (value == null || value.isBlank()) {
                continue; // pas encore saisi — on laisse en attente
            }
            LabResult res = item.getResult();
            if (res == null) {
                res = new LabResult();
                item.setResultValueObject(res);
            }
            res.setLaborantin(laborantin);
            res.setResultValue(value.trim());
            res.setUnit(in.getUnit());
            // Référence : valeur saisie sinon repli sur le catalogue
            String ref = (in.getReferenceRange() != null && !in.getReferenceRange().isBlank())
                    ? in.getReferenceRange()
                    : item.getTest().getReferenceRange();
            res.setReferenceRange(ref);
            res.setAbnormal(in.isAbnormal() || abnormalityChecker.isAbnormal(value, ref));
            res.setNotes(in.getResultNotes());
            item.setStatus("SAISI");
        }
        r.setStatus("EN_COURS");
        return labRequestRepository.save(r);
    }

    // ── Validation (médecin / biologiste) ────────────────────────────────────────
    public LabRequest validate(Long requestId) {
        LabRequest r = labRequestRepository.findWithRefsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'analyses introuvable : " + requestId));
        if (r.getItems().stream().anyMatch(i -> i.getResult() == null)) {
            throw new IllegalStateException("Toutes les analyses doivent avoir un résultat avant validation");
        }
        User validator = currentUser();
        LocalDateTime now = LocalDateTime.now();
        for (LabRequestItem item : r.getItems()) {
            LabResult res = item.getResult();
            if (res != null) {
                res.setValidatedBy(validator);
                res.setValidatedAt(now);
            }
        }
        r.setStatus("VALIDE");
        return labRequestRepository.save(r);
    }

    /** VALIDE → LIVRE : le bulletin a été remis au médecin/patient. */
    public LabRequest deliver(Long requestId) {
        LabRequest r = getById(requestId);
        if (!"VALIDE".equals(r.getStatus())) {
            throw new IllegalStateException("Seule une demande validée peut être marquée comme livrée");
        }
        r.setStatus("LIVRE");
        return labRequestRepository.save(r);
    }

    public LabRequest cancel(Long requestId) {
        LabRequest r = getById(requestId);
        if ("VALIDE".equals(r.getStatus()) || "LIVRE".equals(r.getStatus())) {
            throw new IllegalStateException("Une demande validée ne peut pas être annulée");
        }
        r.setStatus("ANNULE");
        return labRequestRepository.save(r);
    }

    // ── Numérotation LAB-YYYY-NNNNN ──────────────────────────────────────────────
    private String nextNumber() {
        String prefix = PREFIX + "-" + Year.now().getValue() + "-";
        int next = labRequestRepository.findMaxSequence(prefix) + 1;
        return prefix + String.format("%05d", next);
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────────
    private LabRequestItemDto findInput(List<LabRequestItemDto> inputs, Long itemId) {
        if (inputs == null || itemId == null) return null;
        return inputs.stream()
                .filter(i -> i != null && itemId.equals(i.getId()))
                .findFirst().orElse(null);
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    // ── DTO depuis entité (associations initialisées requises) ────────────────────
    public LabRequestDto toDto(LabRequest r) {
        LabRequestDto dto = new LabRequestDto();
        dto.setId(r.getId());
        dto.setRequestNumber(r.getRequestNumber());
        dto.setConsultationId(r.getConsultation() != null ? r.getConsultation().getId() : null);
        dto.setPatientId(r.getPatient() != null ? r.getPatient().getId() : null);
        dto.setDoctorId(r.getDoctor() != null ? r.getDoctor().getId() : null);
        dto.setPriority(r.getPriority());
        dto.setStatus(r.getStatus());
        dto.setNotes(r.getNotes());
        dto.setRequestedAt(r.getRequestedAt());
        if (r.getPatient() != null) {
            dto.setPatientName(r.getPatient().getFullName());
            dto.setPatientRecordNumber(r.getPatient().getRecordNumber());
        }
        dto.setDoctorName(r.getDoctor() != null ? r.getDoctor().getFullName() : null);

        long abnormal = 0;
        boolean allEntered = !r.getItems().isEmpty();
        for (LabRequestItem item : r.getItems()) {
            LabRequestItemDto idto = new LabRequestItemDto();
            idto.setId(item.getId());
            idto.setStatus(item.getStatus());
            LabTestCatalog test = item.getTest();
            if (test != null) {
                idto.setTestId(test.getId());
                idto.setTestCode(test.getCode());
                idto.setTestName(test.getName());
                idto.setCategory(test.getCategory());
                idto.setCatalogReferenceRange(test.getReferenceRange());
            }
            LabResult res = item.getResult();
            if (res != null) {
                idto.setResultId(res.getId());
                idto.setResultValue(res.getResultValue());
                idto.setUnit(res.getUnit());
                idto.setReferenceRange(res.getReferenceRange());
                idto.setAbnormal(res.isAbnormal());
                idto.setResultNotes(res.getNotes());
                idto.setLaborantinName(res.getLaborantin() != null ? res.getLaborantin().getFullName() : null);
                idto.setValidatedByName(res.getValidatedBy() != null ? res.getValidatedBy().getFullName() : null);
                idto.setValidatedAt(res.getValidatedAt());
                if (res.isAbnormal()) abnormal++;
            } else {
                allEntered = false;
            }
            dto.getItems().add(idto);
            if (test != null) dto.getTestIds().add(test.getId());
        }
        dto.setAbnormalCount(abnormal);
        dto.setAllResultsEntered(allEntered);
        return dto;
    }
}
