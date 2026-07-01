package com.clinic.backend.certificate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicalCertificateRepository extends JpaRepository<MedicalCertificate, Long> {

    /** Détail avec associations chargées (OSIV off → JOIN FETCH). */
    @Query("""
            SELECT c FROM MedicalCertificate c
            LEFT JOIN FETCH c.patient
            LEFT JOIN FETCH c.doctor
            LEFT JOIN FETCH c.consultation
            WHERE c.id = :id
            """)
    Optional<MedicalCertificate> findWithRefsById(@Param("id") Long id);

    /** Certificats d'un patient (récents d'abord), mappés en transaction. */
    @Query("""
            SELECT c FROM MedicalCertificate c
            LEFT JOIN FETCH c.patient
            LEFT JOIN FETCH c.doctor
            WHERE c.patient.id = :patientId
            ORDER BY c.issueDate DESC, c.id DESC
            """)
    List<MedicalCertificate> findByPatient(@Param("patientId") Long patientId);

    /** Derniers certificats émis (liste de suivi médecin). */
    @Query("""
            SELECT c FROM MedicalCertificate c
            LEFT JOIN FETCH c.patient
            LEFT JOIN FETCH c.doctor
            ORDER BY c.issueDate DESC, c.id DESC
            """)
    List<MedicalCertificate> findRecent(org.springframework.data.domain.Pageable pageable);

    /**
     * Plus grand numéro de séquence pour un préfixe « CERT-YYYY- » (10 car.) ; 0 si aucun.
     * Native + GLOBAL (non filtré par {@code @TenantId}, P4.2, comme les ordonnances) : les numéros
     * de certificat restent uniques entre cliniques (la colonne est {@code unique} globalement).
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTR(certificate_number, 11) AS INTEGER)), 0) "
                 + "FROM medical_certificates WHERE certificate_number LIKE CONCAT(:prefix, '%')",
           nativeQuery = true)
    int findMaxSequence(@Param("prefix") String prefix);
}
