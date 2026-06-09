package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
public class PatientDto {
    private Long id;
    private String recordNumber;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String birthPlace;
    private String gender;
    private String nationality;
    private String nationalId;
    private String phone;
    private String phoneAlt;
    private String email;
    private String address;
    private String city;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String bloodType;
    private String allergies;
    private String chronicConditions;
    private String medicalHistory;
    private Long assignedDoctorId;
    private String insuranceNumber;
    private String notes;
}
