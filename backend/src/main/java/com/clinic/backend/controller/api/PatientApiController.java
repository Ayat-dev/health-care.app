package com.clinic.backend.controller.api;

import com.clinic.backend.dto.PatientDto;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * API REST patients.
 *
 * Lecture  : tous les rôles cliniques (hors PATIENT)
 * Écriture : ADMIN, MEDECIN, SECRETAIRE, INFIRMIER
 * Suppression : ADMIN uniquement
 */
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientApiController {

    private final PatientService patientService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','SECRETAIRE','PHARMACIEN','LABORANTIN','CAISSIER')")
    public Page<PatientDto> list(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return patientService.search(q, page, size).map(patientService::toDto);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','SECRETAIRE','PHARMACIEN','LABORANTIN','CAISSIER')")
    public PatientDto get(@PathVariable Long id) {
        return patientService.toDto(patientService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public ResponseEntity<PatientDto> create(@RequestBody PatientDto dto) {
        Patient created = patientService.create(dto);
        return ResponseEntity.ok(patientService.toDto(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public PatientDto update(@PathVariable Long id, @RequestBody PatientDto dto) {
        return patientService.toDto(patientService.update(id, dto));
    }

    @PostMapping(value = "/{id}/photo", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public PatientDto uploadPhoto(@PathVariable Long id,
                                  @RequestParam("file") MultipartFile file) {
        return patientService.toDto(patientService.uploadPhoto(id, file));
    }

    /**
     * Sert la photo du patient (octets) pour le client lourd (JWT) — les fichiers
     * {@code /uploads/**} ne sont sinon accessibles que sur la chaîne web/session.
     * 404 si le patient n'a pas de photo.
     */
    @GetMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','SECRETAIRE','PHARMACIEN','LABORANTIN','CAISSIER')")
    public ResponseEntity<byte[]> getPhoto(@PathVariable Long id) {
        FileStorageService.StoredFile photo = patientService.loadPhoto(id);
        if (photo == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .body(photo.content());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MEDECIN')") // suppression de dossier (PHI) → clinique, plus ADMIN (P6)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patientService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
