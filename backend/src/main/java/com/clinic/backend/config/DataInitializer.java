package com.clinic.backend.config;

import com.clinic.backend.appointment.Appointment;
import com.clinic.backend.appointment.AppointmentRepository;
import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.ConsultationRepository;
import com.clinic.backend.consultation.Prescription;
import com.clinic.backend.consultation.PrescriptionItem;
import com.clinic.backend.consultation.PrescriptionRepository;
import com.clinic.backend.catalog.LabTestCatalog;
import com.clinic.backend.catalog.LabTestCatalogRepository;
import com.clinic.backend.lab.LabRequest;
import com.clinic.backend.lab.LabRequestItem;
import com.clinic.backend.lab.LabRequestRepository;
import com.clinic.backend.lab.LabResult;
import com.clinic.backend.radiology.RadiologyExamCatalog;
import com.clinic.backend.radiology.RadiologyExamCatalogRepository;
import com.clinic.backend.radiology.RadiologyReport;
import com.clinic.backend.radiology.RadiologyRequest;
import com.clinic.backend.radiology.RadiologyRequestItem;
import com.clinic.backend.radiology.RadiologyRequestRepository;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.pharmacy.Drug;
import com.clinic.backend.pharmacy.DrugRepository;
import com.clinic.backend.pharmacy.StockItem;
import com.clinic.backend.pharmacy.StockItemRepository;
import com.clinic.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initData(UserRepository userRepository,
                               PatientRepository patientRepository,
                               AppointmentRepository appointmentRepository,
                               ConsultationRepository consultationRepository,
                               PrescriptionRepository prescriptionRepository,
                               DrugRepository drugRepository,
                               StockItemRepository stockItemRepository,
                               LabTestCatalogRepository labTestCatalogRepository,
                               LabRequestRepository labRequestRepository,
                               RadiologyExamCatalogRepository radiologyExamCatalogRepository,
                               RadiologyRequestRepository radiologyRequestRepository,
                               PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() > 0) return;

            // Utilisateurs
            User admin = userRepository.save(new User("admin",
                    passwordEncoder.encode("admin123"), "Administrateur", "ADMIN"));
            User doctor = userRepository.save(new User("dr.martin",
                    passwordEncoder.encode("medecin123"), "Dr. Martin", "MEDECIN"));
            userRepository.save(new User("secretaire",
                    passwordEncoder.encode("secretaire123"), "Secrétaire", "SECRETAIRE"));
            userRepository.save(new User("pharmacien",
                    passwordEncoder.encode("pharmacien123"), "Pharmacien", "PHARMACIEN"));
            User laborantin = userRepository.save(new User("laborantin",
                    passwordEncoder.encode("laborantin123"), "Laborantin", "LABORANTIN"));
            User radiologue = userRepository.save(new User("radiologue",
                    passwordEncoder.encode("radiologue123"), "Dr. Sow (Radiologie)", "MEDECIN"));

            // Patients de test
            Patient p1 = new Patient();
            p1.setRecordNumber("PAT-2026-00001");
            p1.setFirstName("Aminata"); p1.setLastName("Diallo");
            p1.setBirthDate(LocalDate.of(1990, 3, 15));
            p1.setGender("F"); p1.setPhone("+221 77 123 45 67");
            p1.setCity("Dakar"); p1.setBloodType("O+");
            p1.setAssignedDoctor(doctor);
            patientRepository.save(p1);

            Patient p2 = new Patient();
            p2.setRecordNumber("PAT-2026-00002");
            p2.setFirstName("Moussa"); p2.setLastName("Koné");
            p2.setBirthDate(LocalDate.of(1985, 7, 22));
            p2.setGender("M"); p2.setPhone("+221 76 987 65 43");
            p2.setCity("Abidjan"); p2.setBloodType("A+");
            p2.setAllergies("Pénicilline");
            p2.setAssignedDoctor(doctor);
            patientRepository.save(p2);

            Patient p3 = new Patient();
            p3.setRecordNumber("PAT-2026-00003");
            p3.setFirstName("Fatou"); p3.setLastName("Traoré");
            p3.setBirthDate(LocalDate.of(2001, 11, 5));
            p3.setGender("F"); p3.setPhone("+221 70 456 78 90");
            p3.setCity("Bamako");
            patientRepository.save(p3);

            // Rendez-vous de test (aujourd'hui + cette semaine, pour dr.martin)
            LocalDate today = LocalDate.now();
            seedAppointment(appointmentRepository, p1, doctor, admin,
                    today.atTime(9, 0), "CONSULTATION", "PLANIFIE", "Douleurs abdominales");
            seedAppointment(appointmentRepository, p2, doctor, admin,
                    today.atTime(10, 30), "SUIVI", "CONFIRME", "Contrôle tension");
            seedAppointment(appointmentRepository, p3, doctor, admin,
                    today.plusDays(1).atTime(14, 0), "CONSULTATION", "PLANIFIE", "Première visite");
            seedAppointment(appointmentRepository, p1, doctor, admin,
                    today.plusDays(2).atTime(8, 30), "URGENCE", "PLANIFIE", "Fièvre");

            // Catalogue pharmacie + stock de test
            Drug omeprazole = seedDrug(drugRepository, "OMEP20", "Oméprazole", "Oméprazole",
                    "ANTIULCEREUX", "COMPRIME", "20mg", "COMPRIME", true);
            Drug phloro = seedDrug(drugRepository, "PHLO80", "Phloroglucinol", "Phloroglucinol",
                    "ANTISPASMODIQUE", "COMPRIME", "80mg", "COMPRIME", false);
            Drug para = seedDrug(drugRepository, "PARA500", "Paracétamol", "Paracétamol",
                    "ANALGESIQUE", "COMPRIME", "500mg", "COMPRIME", false);
            Drug amox = seedDrug(drugRepository, "AMOX500", "Amoxicilline", "Amoxicilline",
                    "ANTIBIOTIQUE", "GELULE", "500mg", "GELULE", true);

            seedStock(stockItemRepository, omeprazole, "LOT-OMP-01", today.plusYears(2),
                    200, 30, new java.math.BigDecimal("150.00"), admin);
            seedStock(stockItemRepository, phloro, "LOT-PHL-07", today.plusYears(1),
                    8, 20, new java.math.BigDecimal("100.00"), admin);   // stock faible (8 ≤ 20)
            seedStock(stockItemRepository, para, "LOT-PAR-22", today.plusDays(18),
                    500, 50, new java.math.BigDecimal("50.00"), admin);  // périme bientôt (< 30 j)
            seedStock(stockItemRepository, amox, "LOT-AMX-03", today.plusMonths(14),
                    120, 30, new java.math.BigDecimal("200.00"), admin);

            // Consultation clôturée (avec ordonnance) — pour p1
            Consultation c1 = new Consultation();
            c1.setPatient(p1);
            c1.setDoctor(doctor);
            c1.setConsultationDate(today.minusDays(7).atTime(9, 30));
            c1.setChiefComplaint("Douleurs abdominales");
            c1.setHistory("Douleurs épigastriques depuis 3 jours, sans fièvre.");
            c1.setPhysicalExam("Abdomen souple, sensibilité épigastrique.");
            c1.setDiagnosis("Gastrite aiguë");
            c1.setIcd10Codes("K29.1");
            c1.setTreatmentPlan("IPP 4 semaines, régime, contrôle si persistance.");
            c1.setWeightKg(new java.math.BigDecimal("64.50"));
            c1.setTemperatureC(new java.math.BigDecimal("37.2"));
            c1.setBpSystolic(120);
            c1.setBpDiastolic(80);
            c1.setPulseBpm(72);
            c1.setStatus("TERMINE");
            consultationRepository.save(c1);

            Prescription rx = new Prescription();
            rx.setPrescriptionNumber("ORD-" + today.getYear() + "-00001");
            rx.setConsultation(c1);
            rx.setPatient(p1);
            rx.setDoctor(doctor);
            rx.setIssueDate(c1.getConsultationDate().toLocalDate());
            rx.setValidityDays(30);
            rx.setNotes("À prendre avant les repas.");
            rx.addItem(seedItem(omeprazole, "20mg", "1x/jour", "28 jours", 28, 0));
            rx.addItem(seedItem(phloro, "80mg", "3x/jour", "5 jours", 15, 1));
            prescriptionRepository.save(rx);

            // Consultation en cours — pour p2
            Consultation c2 = new Consultation();
            c2.setPatient(p2);
            c2.setDoctor(doctor);
            c2.setConsultationDate(today.atTime(10, 30));
            c2.setChiefComplaint("Contrôle tension");
            c2.setBpSystolic(145);
            c2.setBpDiastolic(95);
            c2.setPulseBpm(78);
            c2.setStatus("EN_COURS");
            consultationRepository.save(c2);

            // ── Laboratoire ───────────────────────────────────────────────────
            LabTestCatalog nfs   = labTestCatalogRepository.findByCodeIgnoreCase("NFS").orElse(null);
            LabTestCatalog glyc  = labTestCatalogRepository.findByCodeIgnoreCase("GLYCEMIE").orElse(null);
            LabTestCatalog creat = labTestCatalogRepository.findByCodeIgnoreCase("CREAT").orElse(null);
            LabTestCatalog hiv   = labTestCatalogRepository.findByCodeIgnoreCase("HIV").orElse(null);

            // Demande validée pour p1 (issue de c1) — glycémie anormale
            LabRequest lr1 = new LabRequest();
            lr1.setRequestNumber("LAB-" + today.getYear() + "-00001");
            lr1.setConsultation(c1);
            lr1.setPatient(p1);
            lr1.setDoctor(doctor);
            lr1.setRequestedAt(today.minusDays(7).atTime(9, 45));
            lr1.setPriority("NORMAL");
            lr1.setStatus("VALIDE");
            lr1.setNotes("Bilan douleurs abdominales.");
            if (nfs != null) lr1.addItem(seedLabResult(nfs, "5.2", "G/L", nfs.getReferenceRange(), false,
                    laborantin, doctor, today.minusDays(6).atTime(11, 0)));
            if (glyc != null) lr1.addItem(seedLabResult(glyc, "1.45", "g/L", glyc.getReferenceRange(), true,
                    laborantin, doctor, today.minusDays(6).atTime(11, 0)));
            labRequestRepository.save(lr1);

            // Demande en attente pour p2 — apparaît dans le travail du jour
            LabRequest lr2 = new LabRequest();
            lr2.setRequestNumber("LAB-" + today.getYear() + "-00002");
            lr2.setPatient(p2);
            lr2.setDoctor(doctor);
            lr2.setRequestedAt(today.atTime(10, 45));
            lr2.setPriority("URGENT");
            lr2.setStatus("EN_ATTENTE");
            lr2.setNotes("Contrôle fonction rénale + sérologie.");
            if (creat != null) lr2.addItem(seedLabItem(creat));
            if (hiv != null) lr2.addItem(seedLabItem(hiv));
            labRequestRepository.save(lr2);

            // ── Imagerie ──────────────────────────────────────────────────────
            RadiologyExamCatalog rxThorax = radiologyExamCatalogRepository.findByCodeIgnoreCase("RX_THORAX").orElse(null);
            RadiologyExamCatalog echoAbdo = radiologyExamCatalogRepository.findByCodeIgnoreCase("ECHO_ABDO").orElse(null);
            RadiologyExamCatalog scanCrane = radiologyExamCatalogRepository.findByCodeIgnoreCase("SCAN_CRANE").orElse(null);

            // Demande validée pour p1 (issue de c1) — compte-rendu rédigé + validé
            RadiologyRequest rr1 = new RadiologyRequest();
            rr1.setRequestNumber("RAD-" + today.getYear() + "-00001");
            rr1.setConsultation(c1);
            rr1.setPatient(p1);
            rr1.setDoctor(doctor);
            rr1.setRequestedAt(today.minusDays(7).atTime(10, 0));
            rr1.setPriority("NORMAL");
            rr1.setStatus("VALIDE");
            rr1.setClinicalInfo("Douleurs épigastriques — recherche d'épanchement.");
            if (echoAbdo != null) rr1.addItem(seedRadioItem(echoAbdo));
            RadiologyReport rep1 = new RadiologyReport();
            rep1.setRadiologist(radiologue);
            rep1.setFindings("Foie de taille et d'échostructure normales. Pas de dilatation des voies biliaires. "
                    + "Vésicule alithiasique. Reins en place, sans dilatation pyélocalicielle. "
                    + "Pas d'épanchement intra-péritonéal.");
            rep1.setConclusion("Échographie abdominale sans particularité.");
            rep1.setValidatedBy(radiologue);
            rep1.setValidatedAt(today.minusDays(6).atTime(9, 30));
            rr1.setReportObject(rep1);
            radiologyRequestRepository.save(rr1);

            // Demande en attente pour p2 — apparaît dans le travail du jour
            RadiologyRequest rr2 = new RadiologyRequest();
            rr2.setRequestNumber("RAD-" + today.getYear() + "-00002");
            rr2.setPatient(p2);
            rr2.setDoctor(doctor);
            rr2.setRequestedAt(today.atTime(11, 0));
            rr2.setPriority("URGENT");
            rr2.setStatus("EN_ATTENTE");
            rr2.setClinicalInfo("Céphalées + HTA — éliminer un processus expansif.");
            if (rxThorax != null) rr2.addItem(seedRadioItem(rxThorax));
            if (scanCrane != null) rr2.addItem(seedRadioItem(scanCrane));
            radiologyRequestRepository.save(rr2);
        };
    }

    private RadiologyRequestItem seedRadioItem(RadiologyExamCatalog exam) {
        RadiologyRequestItem item = new RadiologyRequestItem();
        item.setExam(exam);
        return item;
    }

    private LabRequestItem seedLabItem(LabTestCatalog test) {
        LabRequestItem item = new LabRequestItem();
        item.setTest(test);
        item.setStatus("EN_ATTENTE");
        return item;
    }

    private LabRequestItem seedLabResult(LabTestCatalog test, String value, String unit, String range,
                                         boolean abnormal, User laborantin, User validator,
                                         LocalDateTime validatedAt) {
        LabRequestItem item = new LabRequestItem();
        item.setTest(test);
        item.setStatus("SAISI");
        LabResult res = new LabResult();
        res.setResultValue(value);
        res.setUnit(unit);
        res.setReferenceRange(range);
        res.setAbnormal(abnormal);
        res.setLaborantin(laborantin);
        res.setValidatedBy(validator);
        res.setValidatedAt(validatedAt);
        item.setResultValueObject(res);
        return item;
    }

    private Drug seedDrug(DrugRepository repo, String code, String name, String generic,
                          String category, String form, String dosage, String unit, boolean requiresRx) {
        Drug d = new Drug();
        d.setCode(code);
        d.setName(name);
        d.setGenericName(generic);
        d.setCategory(category);
        d.setForm(form);
        d.setDosageStrength(dosage);
        d.setUnit(unit);
        d.setRequiresPrescription(requiresRx);
        return repo.save(d);
    }

    private void seedStock(StockItemRepository repo, Drug drug, String batch, LocalDate expiry,
                           int quantity, int alert, java.math.BigDecimal sellingPrice, User receivedBy) {
        StockItem s = new StockItem();
        s.setDrug(drug);
        s.setBatchNumber(batch);
        s.setExpiryDate(expiry);
        s.setQuantity(quantity);
        s.setQuantityAlert(alert);
        s.setSellingPrice(sellingPrice);
        s.setReceivedBy(receivedBy);
        s.setReceivedAt(LocalDate.now());
        repo.save(s);
    }

    private PrescriptionItem seedItem(Drug drug, String dosage, String freq,
                                      String duration, Integer qty, int order) {
        PrescriptionItem it = seedItem(drug.getName(), dosage, freq, duration, qty, order);
        it.setDrugId(drug.getId());
        return it;
    }

    private PrescriptionItem seedItem(String name, String dosage, String freq,
                                      String duration, Integer qty, int order) {
        PrescriptionItem it = new PrescriptionItem();
        it.setDrugName(name);
        it.setDosage(dosage);
        it.setFrequency(freq);
        it.setDuration(duration);
        it.setQuantity(qty);
        it.setSortOrder(order);
        return it;
    }

    private void seedAppointment(AppointmentRepository repo, Patient patient, User doctor,
                                 User createdBy, LocalDateTime start, String type,
                                 String status, String reason) {
        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setStartTime(start);
        a.setEndTime(start.plusMinutes(30));
        a.setType(type);
        a.setStatus(status);
        a.setReason(reason);
        a.setCreatedBy(createdBy);
        repo.save(a);
    }
}
