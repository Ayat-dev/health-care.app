package com.clinic.backend.patient;

import com.clinic.backend.audit.Audited;
import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.dto.PatientDto;
import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.Year;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    // ── Recherche paginée ──────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<Patient> search(String q, int page, int size) {
        return patientRepository.search(q, PageRequest.of(page, size));
    }

    // ── Détail ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Patient getById(Long id) {
        return patientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    /** Dossier view: patient with assignedDoctor initialized (OSIV is off). */
    @Transactional(readOnly = true)
    public Patient getByIdWithDoctor(Long id) {
        return patientRepository.findWithDoctorById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    // ── Création ──────────────────────────────────────────────────────────
    @Audited(action = "CREATE", entity = "Patient")
    public Patient create(PatientDto dto) {
        Patient p = new Patient();
        mapDtoToEntity(dto, p);
        p.setRecordNumber(generateRecordNumber());
        return patientRepository.save(p);
    }

    // ── Modification ──────────────────────────────────────────────────────
    @Audited(action = "UPDATE", entity = "Patient")
    public Patient update(Long id, PatientDto dto) {
        Patient p = getById(id);
        mapDtoToEntity(dto, p);
        return patientRepository.save(p);
    }

    // ── Upload photo ──────────────────────────────────────────────────────
    public Patient uploadPhoto(Long id, MultipartFile file) {
        Patient p = getById(id);
        String url = fileStorageService.storeImage(file, "patients/" + id);
        p.setPhotoUrl(url);
        return patientRepository.save(p);
    }

    /**
     * Charge la photo du patient (octets + type MIME) pour la servir via l'API JWT
     * — les fichiers {@code /uploads/**} ne sont sinon accessibles que sur la chaîne
     * web/session. Renvoie {@code null} si le patient n'a pas de photo (ou fichier absent).
     */
    @Transactional(readOnly = true)
    public FileStorageService.StoredFile loadPhoto(Long id) {
        Patient p = getById(id);
        if (p.getPhotoUrl() == null || p.getPhotoUrl().isBlank()) return null;
        return fileStorageService.load(p.getPhotoUrl());
    }

    // ── Suppression logique ───────────────────────────────────────────────
    public void delete(Long id) {
        Patient p = getById(id);
        p.setDeletedAt(LocalDateTime.now());
        patientRepository.save(p);
    }

    // ── Numérotation PAT-YYYY-NNNNN ───────────────────────────────────────
    private String generateRecordNumber() {
        String prefix = "PAT-" + Year.now().getValue() + "-";
        int next = patientRepository.findMaxSequence(prefix) + 1;
        return prefix + String.format("%05d", next);
    }

    // ── Mapping DTO → entité ──────────────────────────────────────────────
    private void mapDtoToEntity(PatientDto dto, Patient p) {
        p.setFirstName(dto.getFirstName());
        p.setLastName(dto.getLastName());
        p.setBirthDate(dto.getBirthDate());
        p.setBirthPlace(dto.getBirthPlace());
        p.setGender(dto.getGender());
        p.setNationality(dto.getNationality());
        p.setNationalId(dto.getNationalId());
        p.setPhone(dto.getPhone());
        p.setPhoneAlt(dto.getPhoneAlt());
        p.setEmail(dto.getEmail());
        p.setAddress(dto.getAddress());
        p.setCity(dto.getCity());
        p.setEmergencyContactName(dto.getEmergencyContactName());
        p.setEmergencyContactPhone(dto.getEmergencyContactPhone());
        p.setBloodType(dto.getBloodType());
        p.setAllergies(dto.getAllergies());
        p.setChronicConditions(dto.getChronicConditions());
        p.setMedicalHistory(dto.getMedicalHistory());
        p.setInsuranceNumber(dto.getInsuranceNumber());
        p.setNotes(dto.getNotes());
        if (dto.getAssignedDoctorId() != null) {
            userRepository.findById(dto.getAssignedDoctorId())
                    .ifPresent(p::setAssignedDoctor);
        }
    }

    // ── DTO depuis entité ─────────────────────────────────────────────────
    public PatientDto toDto(Patient p) {
        PatientDto dto = new PatientDto();
        dto.setId(p.getId());
        dto.setRecordNumber(p.getRecordNumber());
        dto.setFirstName(p.getFirstName());
        dto.setLastName(p.getLastName());
        dto.setBirthDate(p.getBirthDate());
        dto.setBirthPlace(p.getBirthPlace());
        dto.setGender(p.getGender());
        dto.setNationality(p.getNationality());
        dto.setNationalId(p.getNationalId());
        dto.setPhone(p.getPhone());
        dto.setPhoneAlt(p.getPhoneAlt());
        dto.setEmail(p.getEmail());
        dto.setAddress(p.getAddress());
        dto.setCity(p.getCity());
        dto.setEmergencyContactName(p.getEmergencyContactName());
        dto.setEmergencyContactPhone(p.getEmergencyContactPhone());
        dto.setBloodType(p.getBloodType());
        dto.setAllergies(p.getAllergies());
        dto.setChronicConditions(p.getChronicConditions());
        dto.setMedicalHistory(p.getMedicalHistory());
        dto.setInsuranceNumber(p.getInsuranceNumber());
        dto.setNotes(p.getNotes());
        if (p.getAssignedDoctor() != null)
            dto.setAssignedDoctorId(p.getAssignedDoctor().getId());
        return dto;
    }
}
