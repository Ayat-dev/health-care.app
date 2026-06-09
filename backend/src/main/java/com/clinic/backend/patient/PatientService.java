package com.clinic.backend.patient;

import com.clinic.backend.dto.PatientDto;
import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    // ── Recherche paginée ──────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<Patient> search(String q, int page, int size) {
        return patientRepository.search(q, PageRequest.of(page, size));
    }

    // ── Détail ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Patient getById(Long id) {
        return patientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Patient introuvable : " + id));
    }

    // ── Création ──────────────────────────────────────────────────────────
    public Patient create(PatientDto dto) {
        Patient p = new Patient();
        mapDtoToEntity(dto, p);
        p.setRecordNumber(generateRecordNumber());
        return patientRepository.save(p);
    }

    // ── Modification ──────────────────────────────────────────────────────
    public Patient update(Long id, PatientDto dto) {
        Patient p = getById(id);
        mapDtoToEntity(dto, p);
        return patientRepository.save(p);
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
