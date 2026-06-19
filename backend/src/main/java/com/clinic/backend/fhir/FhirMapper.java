package com.clinic.backend.fhir;

import com.clinic.backend.catalog.LabTestCatalog;
import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.Prescription;
import com.clinic.backend.consultation.PrescriptionItem;
import com.clinic.backend.lab.LabRequest;
import com.clinic.backend.lab.LabRequestItem;
import com.clinic.backend.lab.LabResult;
import com.clinic.backend.model.User;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Projette les entités cliniques internes vers les ressources FHIR R4 (P2.1).
 * Aucune logique d'accès aux données ici — les entités doivent arriver avec leurs
 * associations déjà résolues (l'appelant {@code FhirService} mappe dans sa transaction).
 */
@Component
public class FhirMapper {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private static final String SYS_RECORD       = "urn:clinicapp:record-number";
    private static final String SYS_NATIONAL_ID  = "urn:clinicapp:national-id";
    private static final String SYS_LAB_TEST     = "urn:clinicapp:lab-test";
    private static final String SYS_OBS_CATEGORY = "http://terminology.hl7.org/CodeSystem/observation-category";
    private static final String SYS_INTERP       = "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";
    private static final String SYS_ACTCODE      = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    private static final String SYS_LOINC        = "http://loinc.org";
    private static final String SYS_UCUM         = "http://unitsofmeasure.org";

    /** Constantes vitales projetées (ordre d'apparition dans les bundles). */
    static final List<String> VITAL_KEYS =
            List.of("weight", "height", "temperature", "bp", "pulse", "spo2", "resprate");

    // ── Patient ───────────────────────────────────────────────────────────────

    public Patient toPatient(com.clinic.backend.patient.Patient p) {
        Patient fp = new Patient();
        fp.setId(String.valueOf(p.getId()));
        fp.addIdentifier().setSystem(SYS_RECORD).setValue(p.getRecordNumber());
        if (StringUtils.hasText(p.getNationalId())) {
            fp.addIdentifier().setSystem(SYS_NATIONAL_ID).setValue(p.getNationalId());
        }
        fp.setActive(p.getDeletedAt() == null);
        fp.addName().setFamily(p.getLastName()).addGiven(p.getFirstName());
        fp.setGender(mapGender(p.getGender()));
        if (p.getBirthDate() != null) {
            fp.setBirthDateElement(new DateType(p.getBirthDate().toString()));
        }
        if (StringUtils.hasText(p.getPhone())) {
            fp.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(p.getPhone()).setUse(ContactPoint.ContactPointUse.MOBILE);
        }
        if (StringUtils.hasText(p.getPhoneAlt())) {
            fp.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue(p.getPhoneAlt());
        }
        if (StringUtils.hasText(p.getEmail())) {
            fp.addTelecom().setSystem(ContactPoint.ContactPointSystem.EMAIL).setValue(p.getEmail());
        }
        if (StringUtils.hasText(p.getAddress()) || StringUtils.hasText(p.getCity())) {
            Address a = fp.addAddress();
            if (StringUtils.hasText(p.getAddress())) a.addLine(p.getAddress());
            if (StringUtils.hasText(p.getCity())) a.setCity(p.getCity());
        }
        return fp;
    }

    private Enumerations.AdministrativeGender mapGender(String g) {
        if (g == null) return Enumerations.AdministrativeGender.UNKNOWN;
        return switch (g.trim().toUpperCase()) {
            case "M" -> Enumerations.AdministrativeGender.MALE;
            case "F" -> Enumerations.AdministrativeGender.FEMALE;
            case "AUTRE" -> Enumerations.AdministrativeGender.OTHER;
            default -> Enumerations.AdministrativeGender.UNKNOWN;
        };
    }

    // ── Encounter (depuis une consultation) ─────────────────────────────────────

    public Encounter toEncounter(Consultation c) {
        Encounter e = new Encounter();
        e.setId(String.valueOf(c.getId()));
        e.setStatus(mapEncounterStatus(c.getStatus()));
        e.setClass_(new Coding(SYS_ACTCODE, "AMB", "ambulatory"));
        e.setSubject(patientRef(c.getPatient()));
        if (c.getDoctor() != null) {
            e.addParticipant().setIndividual(practitionerRef(c.getDoctor()));
        }
        if (c.getConsultationDate() != null) {
            e.setPeriod(new Period().setStartElement(new DateTimeType(toDate(c.getConsultationDate()))));
        }
        String reason = StringUtils.hasText(c.getDiagnosis()) ? c.getDiagnosis() : c.getChiefComplaint();
        if (StringUtils.hasText(reason)) {
            e.addReasonCode().setText(reason);
        }
        if (c.getDepartment() != null) {
            e.setServiceType(new CodeableConcept().setText(c.getDepartment().getName()));
        }
        return e;
    }

    private Encounter.EncounterStatus mapEncounterStatus(String s) {
        if (s == null) return Encounter.EncounterStatus.UNKNOWN;
        return switch (s) {
            case "TERMINE" -> Encounter.EncounterStatus.FINISHED;
            case "EN_COURS" -> Encounter.EncounterStatus.INPROGRESS;
            case "ANNULE" -> Encounter.EncounterStatus.CANCELLED;
            default -> Encounter.EncounterStatus.UNKNOWN;
        };
    }

    // ── Observation : résultat de laboratoire ───────────────────────────────────

    public Observation toLabObservation(LabResult r) {
        LabRequestItem item = r.getRequestItem();
        LabTestCatalog test = item.getTest();
        LabRequest req = item.getLabRequest();

        Observation o = new Observation();
        o.setId("lab-" + r.getId());
        o.setStatus(r.getValidatedAt() != null
                ? Observation.ObservationStatus.FINAL
                : Observation.ObservationStatus.PRELIMINARY);
        o.addCategory().addCoding().setSystem(SYS_OBS_CATEGORY)
                .setCode("laboratory").setDisplay("Laboratory");
        o.getCode().addCoding().setSystem(SYS_LAB_TEST).setCode(test.getCode()).setDisplay(test.getName());
        o.getCode().setText(test.getName());
        o.setSubject(patientRef(req.getPatient()));
        applyValue(o, r.getResultValue(), r.getUnit());
        if (StringUtils.hasText(r.getReferenceRange())) {
            o.addReferenceRange().setText(r.getReferenceRange());
        }
        if (r.isAbnormal()) {
            o.addInterpretation().addCoding().setSystem(SYS_INTERP).setCode("A").setDisplay("Abnormal");
        }
        LocalDateTime eff = r.getValidatedAt() != null ? r.getValidatedAt() : r.getCreatedAt();
        if (eff != null) o.setEffective(new DateTimeType(toDate(eff)));
        return o;
    }

    private void applyValue(Observation o, String value, String unit) {
        if (!StringUtils.hasText(value)) return;
        String norm = value.trim().replace(',', '.');
        if (norm.matches("-?\\d+(\\.\\d+)?")) {
            Quantity q = new Quantity().setValue(new BigDecimal(norm));
            if (StringUtils.hasText(unit)) q.setUnit(unit);
            o.setValue(q);
        } else {
            o.setValue(new StringType(value));
        }
    }

    // ── Observation : constantes vitales d'une consultation ─────────────────────

    /** Toutes les constantes renseignées de la consultation, dans l'ordre {@link #VITAL_KEYS}. */
    public List<Observation> vitalsToObservations(Consultation c) {
        List<Observation> list = new ArrayList<>();
        for (String key : VITAL_KEYS) {
            Observation o = vitalObservation(c, key);
            if (o != null) list.add(o);
        }
        return list;
    }

    /** Une constante précise, ou {@code null} si elle n'est pas renseignée sur cette consultation. */
    public Observation vitalObservation(Consultation c, String key) {
        return switch (key) {
            case "weight" -> c.getWeightKg() == null ? null
                    : simpleVital(c, key, "29463-7", "Poids", c.getWeightKg(), "kg");
            case "height" -> c.getHeightCm() == null ? null
                    : simpleVital(c, key, "8302-2", "Taille", c.getHeightCm(), "cm");
            case "temperature" -> c.getTemperatureC() == null ? null
                    : simpleVital(c, key, "8310-5", "Température", c.getTemperatureC(), "Cel");
            case "pulse" -> c.getPulseBpm() == null ? null
                    : simpleVital(c, key, "8867-4", "Fréquence cardiaque", BigDecimal.valueOf(c.getPulseBpm()), "/min");
            case "spo2" -> c.getSpo2Percent() == null ? null
                    : simpleVital(c, key, "59408-5", "Saturation en oxygène (SpO2)", c.getSpo2Percent(), "%");
            case "resprate" -> c.getRespiratoryRate() == null ? null
                    : simpleVital(c, key, "9279-1", "Fréquence respiratoire", BigDecimal.valueOf(c.getRespiratoryRate()), "/min");
            case "bp" -> bpObservation(c);
            default -> null;
        };
    }

    private Observation newVital(Consultation c, String key) {
        Observation o = new Observation();
        o.setId("vital-" + c.getId() + "-" + key);
        o.setStatus(Observation.ObservationStatus.FINAL);
        o.addCategory().addCoding().setSystem(SYS_OBS_CATEGORY)
                .setCode("vital-signs").setDisplay("Vital Signs");
        o.setSubject(patientRef(c.getPatient()));
        o.setEncounter(new Reference("Encounter/" + c.getId()));
        if (c.getConsultationDate() != null) {
            o.setEffective(new DateTimeType(toDate(c.getConsultationDate())));
        }
        return o;
    }

    private Observation simpleVital(Consultation c, String key, String loinc,
                                    String display, BigDecimal value, String unit) {
        Observation o = newVital(c, key);
        o.getCode().addCoding().setSystem(SYS_LOINC).setCode(loinc).setDisplay(display);
        o.getCode().setText(display);
        o.setValue(new Quantity().setValue(value).setUnit(unit).setSystem(SYS_UCUM).setCode(unit));
        return o;
    }

    private Observation bpObservation(Consultation c) {
        if (c.getBpSystolic() == null && c.getBpDiastolic() == null) return null;
        Observation o = newVital(c, "bp");
        o.getCode().addCoding().setSystem(SYS_LOINC).setCode("85354-9").setDisplay("Blood pressure panel");
        o.getCode().setText("Tension artérielle");
        if (c.getBpSystolic() != null) {
            Observation.ObservationComponentComponent comp = o.addComponent();
            comp.getCode().addCoding().setSystem(SYS_LOINC).setCode("8480-6").setDisplay("Systolic blood pressure");
            comp.setValue(new Quantity().setValue(c.getBpSystolic()).setUnit("mmHg").setSystem(SYS_UCUM).setCode("mm[Hg]"));
        }
        if (c.getBpDiastolic() != null) {
            Observation.ObservationComponentComponent comp = o.addComponent();
            comp.getCode().addCoding().setSystem(SYS_LOINC).setCode("8462-4").setDisplay("Diastolic blood pressure");
            comp.setValue(new Quantity().setValue(c.getBpDiastolic()).setUnit("mmHg").setSystem(SYS_UCUM).setCode("mm[Hg]"));
        }
        return o;
    }

    // ── MedicationRequest (depuis une ligne d'ordonnance) ───────────────────────

    public MedicationRequest toMedicationRequest(PrescriptionItem item) {
        Prescription presc = item.getPrescription();
        MedicationRequest m = new MedicationRequest();
        m.setId(String.valueOf(item.getId()));
        m.setStatus(presc.isDispensed()
                ? MedicationRequest.MedicationRequestStatus.COMPLETED
                : MedicationRequest.MedicationRequestStatus.ACTIVE);
        m.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        m.setMedication(new CodeableConcept().setText(item.getDrugName()));
        m.setSubject(patientRef(presc.getPatient()));
        if (presc.getIssueDate() != null) {
            m.setAuthoredOnElement(new DateTimeType(toDate(presc.getIssueDate())));
        }
        if (presc.getDoctor() != null) {
            m.setRequester(practitionerRef(presc.getDoctor()));
        }
        Dosage d = m.addDosageInstruction();
        String text = buildDosageText(item);
        if (StringUtils.hasText(text)) d.setText(text);
        if (StringUtils.hasText(item.getInstructions())) d.setPatientInstruction(item.getInstructions());
        if (item.getQuantity() != null) {
            m.getDispenseRequest().setQuantity(new Quantity().setValue(item.getQuantity()).setUnit("unité(s)"));
        }
        return m;
    }

    private String buildDosageText(PrescriptionItem item) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(item.getDosage())) parts.add(item.getDosage());
        if (StringUtils.hasText(item.getFrequency())) parts.add(item.getFrequency());
        if (StringUtils.hasText(item.getDuration())) parts.add(item.getDuration());
        return String.join(", ", parts);
    }

    // ── Helpers communs ─────────────────────────────────────────────────────────

    private Reference patientRef(com.clinic.backend.patient.Patient p) {
        return new Reference("Patient/" + p.getId()).setDisplay(p.getFullName());
    }

    private Reference practitionerRef(User u) {
        if (u == null) return null;
        return new Reference("Practitioner/" + u.getId()).setDisplay(u.getFullName());
    }

    private Date toDate(LocalDateTime ldt) {
        return ldt == null ? null : Date.from(ldt.atZone(ZONE).toInstant());
    }

    private Date toDate(LocalDate d) {
        return d == null ? null : Date.from(d.atStartOfDay(ZONE).toInstant());
    }
}
