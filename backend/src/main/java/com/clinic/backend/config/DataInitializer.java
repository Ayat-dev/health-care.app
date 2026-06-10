package com.clinic.backend.config;

import com.clinic.backend.appointment.Appointment;
import com.clinic.backend.appointment.AppointmentRepository;
import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.ConsultationRepository;
import com.clinic.backend.consultation.Prescription;
import com.clinic.backend.consultation.PrescriptionItem;
import com.clinic.backend.consultation.PrescriptionRepository;
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
        };
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
