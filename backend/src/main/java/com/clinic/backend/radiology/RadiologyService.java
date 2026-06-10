package com.clinic.backend.radiology;

import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.ConsultationRepository;
import com.clinic.backend.dto.RadiologyImageDto;
import com.clinic.backend.dto.RadiologyRequestDto;
import com.clinic.backend.dto.RadiologyRequestItemDto;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class RadiologyService {

    private static final String PREFIX = "RAD";

    private final RadiologyRequestRepository requestRepository;
    private final RadiologyExamCatalogRepository examCatalogRepository;
    private final ConsultationRepository consultationRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    // ── Catalogue ────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<RadiologyExamCatalog> listCatalog() {
        return examCatalogRepository.findByActiveTrueOrderByTypeAscNameAsc();
    }

    // ── Listes / recherche ─────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<RadiologyRequest> search(LocalDate from, LocalDate to, Long patientId,
                                         Long doctorId, String status, String priority) {
        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        LocalDateTime toTs   = to   != null ? to.plusDays(1).atStartOfDay() : null;
        return requestRepository.search(fromTs, toTs, patientId, doctorId, status, priority);
    }

    /** Same filter as {@link #search} but mapped to DTOs inside the transaction (for the API). */
    @Transactional(readOnly = true)
    public List<RadiologyRequestDto> searchDto(LocalDate from, LocalDate to, Long patientId,
                                               Long doctorId, String status, String priority) {
        return search(from, to, patientId, doctorId, status, priority).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<RadiologyRequestDto> worklist() {
        return requestRepository.findWorklist().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<RadiologyRequestDto> findForPatient(Long patientId) {
        return requestRepository.findByPatient(patientId).stream().map(this::toDto).toList();
    }

    // ── Détail ───────────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public RadiologyRequest getById(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'imagerie introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public RadiologyRequestDto getDtoById(Long id) {
        RadiologyRequest r = requestRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'imagerie introuvable : " + id));
        return toDto(r);
    }

    /** Prefill a new request from a consultation (patient/doctor). */
    @Transactional(readOnly = true)
    public RadiologyRequestDto prefillFromConsultation(Long consultationId, Long patientId) {
        RadiologyRequestDto dto = new RadiologyRequestDto();
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

    // ── Création ───────────────────────────────────────────────────────────────────────
    public RadiologyRequest create(RadiologyRequestDto dto) {
        if (dto.getPatientId() == null) {
            throw new IllegalArgumentException("Le patient est obligatoire");
        }
        if (dto.getDoctorId() == null) {
            throw new IllegalArgumentException("Le médecin prescripteur est obligatoire");
        }
        List<Long> examIds = dto.getExamIds();
        if (examIds == null || examIds.stream().filter(Objects::nonNull).toList().isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez au moins un examen");
        }

        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(dto.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable : " + dto.getPatientId()));
        User doctor = userRepository.findById(dto.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Médecin introuvable : " + dto.getDoctorId()));

        RadiologyRequest r = new RadiologyRequest();
        r.setPatient(patient);
        r.setDoctor(doctor);
        if (dto.getConsultationId() != null) {
            Consultation c = consultationRepository.findById(dto.getConsultationId())
                    .orElseThrow(() -> new IllegalArgumentException("Consultation introuvable : " + dto.getConsultationId()));
            r.setConsultation(c);
        }
        r.setRequestedAt(LocalDateTime.now());
        r.setPriority("URGENT".equals(dto.getPriority()) ? "URGENT" : "NORMAL");
        r.setClinicalInfo(dto.getClinicalInfo());
        r.setStatus("EN_ATTENTE");
        r.setRequestNumber(nextNumber());

        for (Long examId : examIds) {
            if (examId == null) continue;
            RadiologyExamCatalog exam = examCatalogRepository.findById(examId)
                    .orElseThrow(() -> new IllegalArgumentException("Examen introuvable : " + examId));
            RadiologyRequestItem item = new RadiologyRequestItem();
            item.setExam(exam);
            r.addItem(item);
        }
        return requestRepository.save(r);
    }

    // ── Compte-rendu (radiologue) ───────────────────────────────────────────────────────
    /** Save/update the radiology report. Moves a pending order to EN_COURS. */
    public RadiologyRequest saveReport(Long requestId, String findings, String conclusion) {
        RadiologyRequest r = requestRepository.findWithRefsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'imagerie introuvable : " + requestId));
        if ("VALIDE".equals(r.getStatus()) || "LIVRE".equals(r.getStatus())) {
            throw new IllegalStateException("Ce compte-rendu est déjà validé et ne peut plus être modifié");
        }
        if ("ANNULE".equals(r.getStatus())) {
            throw new IllegalStateException("Cette demande est annulée");
        }
        RadiologyReport rep = r.getReport();
        if (rep == null) {
            rep = new RadiologyReport();
            r.setReportObject(rep);
        }
        rep.setRadiologist(currentUser());
        rep.setFindings(findings != null ? findings.trim() : null);
        rep.setConclusion(conclusion != null ? conclusion.trim() : null);
        r.setStatus("EN_COURS");
        return requestRepository.save(r);
    }

    // ── Images ──────────────────────────────────────────────────────────────────────────
    public RadiologyRequest addImage(Long requestId, MultipartFile file, String caption) {
        RadiologyRequest r = requestRepository.findWithRefsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'imagerie introuvable : " + requestId));
        if ("LIVRE".equals(r.getStatus()) || "ANNULE".equals(r.getStatus())) {
            throw new IllegalStateException("Impossible d'ajouter une image à une demande livrée ou annulée");
        }
        String path = fileStorageService.storeImage(file, "radiology/" + requestId);
        RadiologyImage img = new RadiologyImage();
        img.setFilePath(path);
        img.setCaption(caption != null && !caption.isBlank() ? caption.trim() : null);
        img.setUploadedBy(currentUser());
        r.addImage(img);
        return requestRepository.save(r);
    }

    public RadiologyRequest deleteImage(Long requestId, Long imageId) {
        RadiologyRequest r = requestRepository.findWithRefsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'imagerie introuvable : " + requestId));
        if ("VALIDE".equals(r.getStatus()) || "LIVRE".equals(r.getStatus())) {
            throw new IllegalStateException("Les images d'un compte-rendu validé ne peuvent pas être supprimées");
        }
        r.getImages().removeIf(img -> img.getId() != null && img.getId().equals(imageId));
        return requestRepository.save(r);
    }

    // ── Validation (radiologue) ───────────────────────────────────────────────────────────
    public RadiologyRequest validate(Long requestId) {
        RadiologyRequest r = requestRepository.findWithRefsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'imagerie introuvable : " + requestId));
        RadiologyReport rep = r.getReport();
        if (rep == null || rep.getFindings() == null || rep.getFindings().isBlank()) {
            throw new IllegalStateException("Le compte-rendu doit être saisi avant validation");
        }
        rep.setValidatedBy(currentUser());
        rep.setValidatedAt(LocalDateTime.now());
        r.setStatus("VALIDE");
        return requestRepository.save(r);
    }

    /** VALIDE → LIVRE : le compte-rendu a été remis au médecin/patient. */
    public RadiologyRequest deliver(Long requestId) {
        RadiologyRequest r = getById(requestId);
        if (!"VALIDE".equals(r.getStatus())) {
            throw new IllegalStateException("Seule une demande validée peut être marquée comme livrée");
        }
        r.setStatus("LIVRE");
        return requestRepository.save(r);
    }

    public RadiologyRequest cancel(Long requestId) {
        RadiologyRequest r = getById(requestId);
        if ("VALIDE".equals(r.getStatus()) || "LIVRE".equals(r.getStatus())) {
            throw new IllegalStateException("Une demande validée ne peut pas être annulée");
        }
        r.setStatus("ANNULE");
        return requestRepository.save(r);
    }

    // ── Numérotation RAD-YYYY-NNNNN ─────────────────────────────────────────────────────────
    private String nextNumber() {
        String prefix = PREFIX + "-" + Year.now().getValue() + "-";
        int next = requestRepository.findMaxSequence(prefix) + 1;
        return prefix + String.format("%05d", next);
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    // ── DTO depuis entité (associations initialisées requises) ───────────────────────────────
    public RadiologyRequestDto toDto(RadiologyRequest r) {
        RadiologyRequestDto dto = new RadiologyRequestDto();
        dto.setId(r.getId());
        dto.setRequestNumber(r.getRequestNumber());
        dto.setConsultationId(r.getConsultation() != null ? r.getConsultation().getId() : null);
        dto.setPatientId(r.getPatient() != null ? r.getPatient().getId() : null);
        dto.setDoctorId(r.getDoctor() != null ? r.getDoctor().getId() : null);
        dto.setPriority(r.getPriority());
        dto.setStatus(r.getStatus());
        dto.setClinicalInfo(r.getClinicalInfo());
        dto.setRequestedAt(r.getRequestedAt());
        if (r.getPatient() != null) {
            dto.setPatientName(r.getPatient().getFullName());
            dto.setPatientRecordNumber(r.getPatient().getRecordNumber());
        }
        dto.setDoctorName(r.getDoctor() != null ? r.getDoctor().getFullName() : null);

        for (RadiologyRequestItem item : r.getItems()) {
            RadiologyRequestItemDto idto = new RadiologyRequestItemDto();
            idto.setId(item.getId());
            RadiologyExamCatalog exam = item.getExam();
            if (exam != null) {
                idto.setExamId(exam.getId());
                idto.setExamCode(exam.getCode());
                idto.setExamName(exam.getName());
                idto.setExamType(exam.getType());
                idto.setExamRegion(exam.getRegion());
                dto.getExamIds().add(exam.getId());
            }
            dto.getItems().add(idto);
        }

        RadiologyReport rep = r.getReport();
        if (rep != null) {
            dto.setReportId(rep.getId());
            dto.setFindings(rep.getFindings());
            dto.setConclusion(rep.getConclusion());
            dto.setRadiologistName(rep.getRadiologist() != null ? rep.getRadiologist().getFullName() : null);
            dto.setValidatedByName(rep.getValidatedBy() != null ? rep.getValidatedBy().getFullName() : null);
            dto.setValidatedAt(rep.getValidatedAt());
            dto.setHasReport(rep.getFindings() != null && !rep.getFindings().isBlank());
        }

        // Images : chargées paresseusement, mais l'accès se fait dans la transaction du service.
        for (RadiologyImage img : r.getImages()) {
            RadiologyImageDto img2 = new RadiologyImageDto();
            img2.setId(img.getId());
            img2.setUrl(img.getFilePath());
            img2.setCaption(img.getCaption());
            img2.setUploadedAt(img.getCreatedAt());
            dto.getImages().add(img2);
        }
        dto.setImageCount(dto.getImages().size());
        return dto;
    }
}
