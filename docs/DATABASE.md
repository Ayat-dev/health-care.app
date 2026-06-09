# ClinicApp — Database Schema

> Source de vérité pour le modèle de données. Toute entité JPA doit correspondre à ce schéma.  
> Moteur : **PostgreSQL 16** (H2 en dev)  
> Migrations : **Flyway** — fichiers `V{N}__{description}.sql`

---

## Conventions

- Toutes les tables au **pluriel snake_case** (ex: `stock_items`)
- Clé primaire toujours `id BIGSERIAL PRIMARY KEY`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` sur toutes les tables
- `updated_at TIMESTAMPTZ` mis à jour via trigger ou `@PreUpdate`
- Les suppressions sont **logiques** (`deleted_at`) sauf exceptions notées
- Les montants financiers en `NUMERIC(12,2)` — jamais FLOAT

---

## Vue d'ensemble — Diagramme des relations

```
users ──────────────────────────────────────────────────┐
  │                                                      │
  ├─< appointments >─ patients                          │
  │       │                │                            │
  │       └─< consultations│                            │
  │               │        │                            │
  │         prescriptions  ├─< lab_requests             │
  │               │        │       └─< lab_results      │
  │          prescription_ │                            │
  │            items       ├─< hospitalizations         │
  │                        │       └─ beds (rooms)      │
  │                        │                            │
  │                        ├─< invoices                 │
  │                        │       └─< invoice_items    │
  │                        │       └─< payments         │
  │                        │                            │
  │                        └─< maternity_records        │
  │                                └─< prenatal_visits  │
  │
  └── pharmacy
        ├─ drugs
        ├─ stock_items
        └─ dispensations
              └─< dispensation_items
```

---

## 1. Authentification & Utilisateurs

### `users`
```sql
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(120) UNIQUE,
    full_name     VARCHAR(100) NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(20)  NOT NULL,   -- ADMIN, MEDECIN, INFIRMIER, SECRETAIRE,
                                           --   PHARMACIEN, LABORANTIN, CAISSIER, PATIENT
    speciality    VARCHAR(80),             -- pour les médecins (Généraliste, Pédiatre…)
    department_id BIGINT REFERENCES departments(id),
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    first_login   BOOLEAN NOT NULL DEFAULT TRUE,
    avatar_url    VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ,
    deleted_at    TIMESTAMPTZ             -- soft delete
);
CREATE INDEX idx_users_role ON users(role);
```

### `user_sessions` (audit)
```sql
CREATE TABLE user_sessions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id),
    ip_address VARCHAR(45),
    user_agent TEXT,
    logged_in_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    logged_out_at TIMESTAMPTZ
);
```

### `audit_logs`
```sql
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    action      VARCHAR(50) NOT NULL,  -- CREATE, UPDATE, DELETE, VIEW
    entity_type VARCHAR(50) NOT NULL,  -- Patient, Consultation, Invoice…
    entity_id   BIGINT,
    old_value   JSONB,
    new_value   JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
```

---

## 2. Configuration clinique

### `clinic_config`
```sql
CREATE TABLE clinic_config (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    slogan             VARCHAR(255),
    address            TEXT,
    phone              VARCHAR(30),
    email              VARCHAR(120),
    website            VARCHAR(255),
    logo_url           VARCHAR(255),
    currency           VARCHAR(10) NOT NULL DEFAULT 'XOF',   -- XOF, MAD, DZD, EUR…
    timezone           VARCHAR(50) NOT NULL DEFAULT 'Africa/Dakar',
    default_language   VARCHAR(10) NOT NULL DEFAULT 'fr',
    -- Modules activés (feature flags)
    module_pharmacy    BOOLEAN NOT NULL DEFAULT TRUE,
    module_lab         BOOLEAN NOT NULL DEFAULT TRUE,
    module_maternity   BOOLEAN NOT NULL DEFAULT FALSE,
    module_dental      BOOLEAN NOT NULL DEFAULT FALSE,
    module_radiology   BOOLEAN NOT NULL DEFAULT FALSE,
    module_hospitalization BOOLEAN NOT NULL DEFAULT FALSE,
    -- Paiements
    mobile_money_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    mobile_money_provider VARCHAR(30),   -- ORANGE_MONEY, MTN_MOMO, WAVE
    insurance_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    -- Numérotation
    patient_record_prefix  VARCHAR(10) NOT NULL DEFAULT 'PAT',
    invoice_prefix         VARCHAR(10) NOT NULL DEFAULT 'FAC',
    prescription_prefix    VARCHAR(10) NOT NULL DEFAULT 'ORD',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ
);
```

### `departments`
```sql
CREATE TABLE departments (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,  -- MED_GEN, MATERNITE, DENTAIRE…
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    color       VARCHAR(7),                    -- hex pour l'UI (#2563eb)
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 3. Patients

### `patients`
```sql
CREATE TABLE patients (
    id              BIGSERIAL PRIMARY KEY,
    record_number   VARCHAR(25) NOT NULL UNIQUE,  -- PAT-2024-00001
    first_name      VARCHAR(60) NOT NULL,
    last_name       VARCHAR(60) NOT NULL,
    birth_date      DATE,
    birth_place     VARCHAR(100),
    gender          VARCHAR(10),   -- M, F, AUTRE
    nationality     VARCHAR(50),
    national_id     VARCHAR(30),   -- CNI / passeport
    phone           VARCHAR(20),
    phone_alt       VARCHAR(20),
    email           VARCHAR(120),
    address         TEXT,
    city            VARCHAR(80),
    emergency_contact_name  VARCHAR(100),
    emergency_contact_phone VARCHAR(20),
    blood_type      VARCHAR(5),    -- A+, B-, O+…
    allergies       TEXT,
    chronic_conditions TEXT,
    medical_history TEXT,
    assigned_doctor_id BIGINT REFERENCES users(id),
    insurance_id    BIGINT REFERENCES insurance_providers(id),
    insurance_number VARCHAR(50),
    photo_url       VARCHAR(255),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_patients_name ON patients(last_name, first_name);
CREATE INDEX idx_patients_record ON patients(record_number);
CREATE INDEX idx_patients_doctor ON patients(assigned_doctor_id);
```

---

## 4. Rendez-vous

### `appointments`
```sql
CREATE TABLE appointments (
    id              BIGSERIAL PRIMARY KEY,
    patient_id      BIGINT NOT NULL REFERENCES patients(id),
    doctor_id       BIGINT NOT NULL REFERENCES users(id),
    department_id   BIGINT REFERENCES departments(id),
    start_time      TIMESTAMPTZ NOT NULL,
    end_time        TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PLANIFIE',
                    -- PLANIFIE, CONFIRME, EN_COURS, TERMINE, ANNULE, ABSENT
    type            VARCHAR(30),  -- CONSULTATION, SUIVI, URGENCE, TELECONSULTATION
    reason          TEXT,
    notes           TEXT,
    reminder_sent   BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   TEXT
);
CREATE INDEX idx_appointments_patient ON appointments(patient_id);
CREATE INDEX idx_appointments_doctor  ON appointments(doctor_id);
CREATE INDEX idx_appointments_date    ON appointments(start_time);
```

---

## 5. Consultations & Ordonnances

### `consultations`
```sql
CREATE TABLE consultations (
    id              BIGSERIAL PRIMARY KEY,
    appointment_id  BIGINT REFERENCES appointments(id),
    patient_id      BIGINT NOT NULL REFERENCES patients(id),
    doctor_id       BIGINT NOT NULL REFERENCES users(id),
    department_id   BIGINT REFERENCES departments(id),
    consultation_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Constantes vitales
    weight_kg       NUMERIC(5,2),
    height_cm       NUMERIC(5,1),
    temperature_c   NUMERIC(4,1),
    bp_systolic     INT,        -- tension artérielle
    bp_diastolic    INT,
    pulse_bpm       INT,
    spo2_percent    NUMERIC(4,1),
    respiratory_rate INT,
    -- Clinique
    chief_complaint TEXT,       -- motif de consultation
    history         TEXT,       -- anamnèse
    physical_exam   TEXT,       -- examen physique
    diagnosis       TEXT NOT NULL,
    icd10_codes     VARCHAR(255), -- codes CIM-10 séparés par virgule
    treatment_plan  TEXT,
    follow_up_date  DATE,
    is_emergency    BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(20) NOT NULL DEFAULT 'EN_COURS',
                    -- EN_COURS, TERMINE, ANNULE
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX idx_consultations_patient ON consultations(patient_id);
CREATE INDEX idx_consultations_doctor  ON consultations(doctor_id);
CREATE INDEX idx_consultations_date    ON consultations(consultation_date);
```

### `prescriptions` (ordonnances)
```sql
CREATE TABLE prescriptions (
    id               BIGSERIAL PRIMARY KEY,
    prescription_number VARCHAR(25) NOT NULL UNIQUE,  -- ORD-2024-00001
    consultation_id  BIGINT REFERENCES consultations(id),
    patient_id       BIGINT NOT NULL REFERENCES patients(id),
    doctor_id        BIGINT NOT NULL REFERENCES users(id),
    issue_date       DATE NOT NULL DEFAULT CURRENT_DATE,
    validity_days    INT NOT NULL DEFAULT 30,
    notes            TEXT,
    is_dispensed     BOOLEAN NOT NULL DEFAULT FALSE,
    dispensed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### `prescription_items`
```sql
CREATE TABLE prescription_items (
    id              BIGSERIAL PRIMARY KEY,
    prescription_id BIGINT NOT NULL REFERENCES prescriptions(id) ON DELETE CASCADE,
    drug_id         BIGINT REFERENCES drugs(id),
    drug_name       VARCHAR(150) NOT NULL,  -- saisie libre si hors stock
    dosage          VARCHAR(100) NOT NULL,  -- ex: "500mg"
    frequency       VARCHAR(100) NOT NULL,  -- ex: "3x/jour"
    duration        VARCHAR(100),           -- ex: "7 jours"
    quantity        INT,
    instructions    TEXT,
    sort_order      INT NOT NULL DEFAULT 0
);
```

---

## 6. Pharmacie & Stock

### `drugs` (médicaments du catalogue)
```sql
CREATE TABLE drugs (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(30) UNIQUE,       -- code interne ou DCI
    name            VARCHAR(150) NOT NULL,    -- nom commercial
    generic_name    VARCHAR(150),             -- DCI
    category        VARCHAR(50),              -- ANTIBIOTIQUE, ANALGESIQUE…
    form            VARCHAR(30),              -- COMPRIME, SIROP, INJECTABLE…
    dosage_strength VARCHAR(50),              -- ex: 500mg, 250mg/5ml
    unit            VARCHAR(20) NOT NULL,     -- COMPRIME, ML, FLACON…
    requires_prescription BOOLEAN NOT NULL DEFAULT TRUE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_drugs_name ON drugs(name);
```

### `stock_items` (stock en temps réel)
```sql
CREATE TABLE stock_items (
    id              BIGSERIAL PRIMARY KEY,
    drug_id         BIGINT NOT NULL REFERENCES drugs(id),
    batch_number    VARCHAR(50),
    expiry_date     DATE NOT NULL,
    quantity        INT NOT NULL DEFAULT 0,
    quantity_alert  INT NOT NULL DEFAULT 10,  -- seuil d'alerte
    purchase_price  NUMERIC(12,2),
    selling_price   NUMERIC(12,2) NOT NULL,
    supplier        VARCHAR(150),
    received_at     DATE NOT NULL DEFAULT CURRENT_DATE,
    received_by     BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX idx_stock_drug    ON stock_items(drug_id);
CREATE INDEX idx_stock_expiry  ON stock_items(expiry_date);
```

### `dispensations` (sorties de stock)
```sql
CREATE TABLE dispensations (
    id                  BIGSERIAL PRIMARY KEY,
    prescription_id     BIGINT REFERENCES prescriptions(id),
    patient_id          BIGINT NOT NULL REFERENCES patients(id),
    pharmacist_id       BIGINT NOT NULL REFERENCES users(id),
    dispensed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    total_amount        NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes               TEXT
);
```

### `dispensation_items`
```sql
CREATE TABLE dispensation_items (
    id              BIGSERIAL PRIMARY KEY,
    dispensation_id BIGINT NOT NULL REFERENCES dispensations(id) ON DELETE CASCADE,
    stock_item_id   BIGINT NOT NULL REFERENCES stock_items(id),
    quantity        INT NOT NULL,
    unit_price      NUMERIC(12,2) NOT NULL,
    total_price     NUMERIC(12,2) NOT NULL
);
```

---

## 7. Maternité & Obstétrique

### `maternity_records`
```sql
CREATE TABLE maternity_records (
    id              BIGSERIAL PRIMARY KEY,
    patient_id      BIGINT NOT NULL REFERENCES patients(id) UNIQUE, -- une fiche par patiente
    doctor_id       BIGINT REFERENCES users(id),
    gravidity       INT,       -- nombre de grossesses
    parity          INT,       -- nombre d'accouchements
    last_period_date DATE,
    expected_due_date DATE,
    delivery_date   DATE,
    delivery_type   VARCHAR(20),  -- NATUREL, CESARIENNE, FORCEPS
    delivery_outcome VARCHAR(20), -- VIVANT, MORT_NE, AVORTEMENT
    newborn_weight_g INT,
    newborn_apgar1  INT,          -- score Apgar à 1 min
    newborn_apgar5  INT,          -- score Apgar à 5 min
    newborn_gender  VARCHAR(10),
    complications   TEXT,
    notes           TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'EN_COURS',
                    -- EN_COURS, ACCOUCHEE, CLOTURE
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);
```

### `prenatal_visits` (consultations prénatales — CPN)
```sql
CREATE TABLE prenatal_visits (
    id                  BIGSERIAL PRIMARY KEY,
    maternity_record_id BIGINT NOT NULL REFERENCES maternity_records(id),
    doctor_id           BIGINT NOT NULL REFERENCES users(id),
    visit_date          DATE NOT NULL,
    visit_number        INT NOT NULL,   -- CPN1, CPN2, CPN3, CPN4
    gestational_age_weeks INT,
    weight_kg           NUMERIC(5,2),
    bp_systolic         INT,
    bp_diastolic        INT,
    fetal_heart_rate    INT,
    uterine_height_cm   NUMERIC(4,1),
    presentation        VARCHAR(30),   -- CEPHALIQUE, SIEGE…
    edema               BOOLEAN,
    proteinuria         BOOLEAN,
    iron_supplemented   BOOLEAN,
    ttv_vaccine         BOOLEAN,       -- vaccin tétanos
    observations        TEXT,
    recommendations     TEXT,
    next_visit_date     DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 8. Hospitalisation

### `rooms` (chambres/lits)
```sql
CREATE TABLE rooms (
    id              BIGSERIAL PRIMARY KEY,
    room_number     VARCHAR(20) NOT NULL UNIQUE,
    department_id   BIGINT REFERENCES departments(id),
    type            VARCHAR(20) NOT NULL,  -- STANDARD, PRIVE, SOINS_INTENSIFS, MATERNITE
    capacity        INT NOT NULL DEFAULT 1,
    daily_rate      NUMERIC(12,2) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    notes           TEXT
);
```

### `hospitalizations`
```sql
CREATE TABLE hospitalizations (
    id              BIGSERIAL PRIMARY KEY,
    patient_id      BIGINT NOT NULL REFERENCES patients(id),
    room_id         BIGINT NOT NULL REFERENCES rooms(id),
    doctor_id       BIGINT NOT NULL REFERENCES users(id),
    admission_date  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    discharge_date  TIMESTAMPTZ,
    admission_reason TEXT NOT NULL,
    diagnosis_on_discharge TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ADMIS',
                    -- ADMIS, TRANSFERE, SORTI, DECEDE
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX idx_hosp_patient ON hospitalizations(patient_id);
CREATE INDEX idx_hosp_room    ON hospitalizations(room_id);
```

---

## 9. Laboratoire

### `lab_test_catalog`
```sql
CREATE TABLE lab_test_catalog (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30) NOT NULL UNIQUE,  -- NFS, GLYCEMIE, HIV…
    name        VARCHAR(150) NOT NULL,
    category    VARCHAR(50),    -- HEMATOLOGIE, BIOCHIMIE, SEROLOGIE, BACTERIO…
    price       NUMERIC(12,2) NOT NULL,
    turnaround_hours INT,       -- délai de résultat habituel
    reference_range TEXT,       -- valeurs normales (texte)
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);
```

### `lab_requests`
```sql
CREATE TABLE lab_requests (
    id              BIGSERIAL PRIMARY KEY,
    request_number  VARCHAR(25) NOT NULL UNIQUE,
    consultation_id BIGINT REFERENCES consultations(id),
    patient_id      BIGINT NOT NULL REFERENCES patients(id),
    doctor_id       BIGINT NOT NULL REFERENCES users(id),
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    priority        VARCHAR(10) NOT NULL DEFAULT 'NORMAL',  -- NORMAL, URGENT
    status          VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
                    -- EN_ATTENTE, EN_COURS, VALIDE, LIVRE
    notes           TEXT
);
```

### `lab_request_items`
```sql
CREATE TABLE lab_request_items (
    id              BIGSERIAL PRIMARY KEY,
    lab_request_id  BIGINT NOT NULL REFERENCES lab_requests(id) ON DELETE CASCADE,
    test_id         BIGINT NOT NULL REFERENCES lab_test_catalog(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE'
);
```

### `lab_results`
```sql
CREATE TABLE lab_results (
    id                  BIGSERIAL PRIMARY KEY,
    lab_request_item_id BIGINT NOT NULL REFERENCES lab_request_items(id),
    laborantin_id       BIGINT REFERENCES users(id),
    result_value        TEXT NOT NULL,
    unit                VARCHAR(30),
    reference_range     TEXT,
    is_abnormal         BOOLEAN NOT NULL DEFAULT FALSE,
    validated_at        TIMESTAMPTZ,
    validated_by        BIGINT REFERENCES users(id),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 10. Facturation & Paiements

### `invoice_items_catalog` (tarifs des actes)
```sql
CREATE TABLE act_catalog (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30) NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    department_id BIGINT REFERENCES departments(id),
    price       NUMERIC(12,2) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);
```

### `insurance_providers`
```sql
CREATE TABLE insurance_providers (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    code        VARCHAR(30) UNIQUE,
    type        VARCHAR(30),   -- PUBLIQUE, PRIVEE, MUTUELLE
    coverage_percent NUMERIC(5,2),  -- % pris en charge par défaut
    contact     VARCHAR(255),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);
```

### `invoices`
```sql
CREATE TABLE invoices (
    id              BIGSERIAL PRIMARY KEY,
    invoice_number  VARCHAR(25) NOT NULL UNIQUE,  -- FAC-2024-00001
    patient_id      BIGINT NOT NULL REFERENCES patients(id),
    consultation_id BIGINT REFERENCES consultations(id),
    hospitalization_id BIGINT REFERENCES hospitalizations(id),
    insurance_id    BIGINT REFERENCES insurance_providers(id),
    insurance_coverage_percent NUMERIC(5,2) DEFAULT 0,
    subtotal        NUMERIC(12,2) NOT NULL DEFAULT 0,
    insurance_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    patient_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,  -- à payer par le patient
    paid_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
                    -- EN_ATTENTE, PARTIEL, PAYE, ANNULE
    due_date        DATE,
    notes           TEXT,
    created_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX idx_invoices_patient ON invoices(patient_id);
CREATE INDEX idx_invoices_status  ON invoices(status);
```

### `invoice_items`
```sql
CREATE TABLE invoice_items (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    act_id      BIGINT REFERENCES act_catalog(id),
    description VARCHAR(255) NOT NULL,
    quantity    INT NOT NULL DEFAULT 1,
    unit_price  NUMERIC(12,2) NOT NULL,
    total_price NUMERIC(12,2) NOT NULL
);
```

### `payments`
```sql
CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    invoice_id      BIGINT NOT NULL REFERENCES invoices(id),
    amount          NUMERIC(12,2) NOT NULL,
    method          VARCHAR(30) NOT NULL,
                    -- ESPECES, CARTE, ORANGE_MONEY, MTN_MOMO, WAVE, VIREMENT, ASSURANCE
    reference       VARCHAR(100),   -- numéro de transaction mobile money
    paid_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cashier_id      BIGINT REFERENCES users(id),
    notes           TEXT
);
```

---

## 11. Notifications

### `notifications`
```sql
CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES users(id),   -- null = patient
    patient_id      BIGINT REFERENCES patients(id),
    type            VARCHAR(30) NOT NULL,
                    -- RAPPEL_RDV, RESULTAT_LABO, STOCK_ALERTE, SYSTEM
    channel         VARCHAR(10) NOT NULL,  -- SMS, EMAIL, IN_APP
    recipient       VARCHAR(150) NOT NULL, -- téléphone ou email
    subject         VARCHAR(255),
    body            TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
                    -- EN_ATTENTE, ENVOYE, ECHEC
    scheduled_at    TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notif_status ON notifications(status, scheduled_at);
```

---

## 12. Migrations Flyway (ordre)

```
V1__create_core_tables.sql           -- users, clinic_config, departments
V2__create_patient_tables.sql        -- patients, appointments
V3__create_consultation_tables.sql   -- consultations, prescriptions, prescription_items
V4__create_pharmacy_tables.sql       -- drugs, stock_items, dispensations
V5__create_maternity_tables.sql      -- maternity_records, prenatal_visits
V6__create_hospitalization_tables.sql -- rooms, hospitalizations
V7__create_lab_tables.sql            -- lab_test_catalog, lab_requests, lab_results
V8__create_billing_tables.sql        -- act_catalog, insurance_providers, invoices, payments
V9__create_notification_tables.sql   -- notifications
V10__create_audit_tables.sql         -- audit_logs, user_sessions
V11__seed_departments.sql            -- données initiales : départements
V12__seed_act_catalog.sql            -- tarifs des actes par défaut
V13__seed_lab_catalog.sql            -- catalogue des analyses
```

---

*Toute modification du schéma = nouveau fichier Flyway. Ne jamais modifier un fichier existant.*
