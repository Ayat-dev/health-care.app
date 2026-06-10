# ClinicApp — Context for Claude Code

> Read this file first at the start of every session. It gives you the full context needed to work on this project without asking repetitive questions.

---

## What this project is

A **complete clinic management system** (SaaS-ready) built for modern African clinics and any private/public medical facility. Two interfaces share one backend:
- **Web app** (Thymeleaf, any browser) — for secretaries, admins, and web access
- **Desktop client** (JavaFX) — for doctors and nurses on local workstations

---

## Tech stack

| Layer | Tech |
|---|---|
| Backend | Spring Boot 3.2, Java 17 |
| Database | PostgreSQL (prod) / H2 (dev) |
| DB migrations | Flyway |
| Security | Spring Security + JWT (API) + Session (web) |
| Web UI | Thymeleaf + HTML/CSS/JS vanilla |
| Desktop | JavaFX 20 |
| Container | Docker + Docker Compose |
| PDF | iText / JasperReports |
| SMS | Africa's Talking |

---

## Project structure

```
medical-app/
├── CLAUDE.md              ← YOU ARE HERE
├── docs/
│   ├── OVERVIEW.md        ← Full architecture, roadmap, conventions
│   ├── DATABASE.md        ← Complete SQL schema (source of truth)
│   └── modules/           ← One spec file per module (01 to 15)
├── backend/               ← Spring Boot app
│   └── src/main/
│       ├── java/com/clinic/backend/
│       │   ├── config/         SecurityConfig, DataInitializer
│       │   ├── controller/
│       │   │   ├── api/        REST controllers (/api/**)
│       │   │   └── web/        Thymeleaf controllers
│       │   ├── dto/            Data Transfer Objects
│       │   ├── entity/         JPA entities
│       │   ├── repository/     Spring Data repositories
│       │   ├── security/       JWT, UserDetailsService
│       │   └── service/        Business logic
│       └── resources/
│           ├── templates/      Thymeleaf HTML views
│           │   └── layouts/base.html  ← shared layout
│           └── static/
│               ├── css/app.css ← design system (do NOT rewrite, only extend)
│               └── js/app.js
├── desktop/               ← JavaFX client
└── docker-compose.yml
```

---

## Naming conventions (STRICT — always follow these)

- **Packages**: `com.clinic.backend.{module}` e.g. `com.clinic.backend.pharmacy`
- **Entities**: PascalCase singular — `StockItem`, `Prescription`
- **DTOs**: suffix `Dto` — `PatientDto`, `StockItemDto`
- **REST controllers**: suffix `ApiController` in `controller/api/`
- **Web controllers**: suffix `WebController` in `controller/web/`
- **Services**: suffix `Service` — `PatientService`, `PharmacyService`
- **Flyway migrations**: `V{N}__{snake_case_description}.sql`
- **Thymeleaf templates**: `templates/{module}/{view}.html`

---

## REST API conventions

```
GET    /api/{module}                → list (with query params for filtering)
GET    /api/{module}/{id}           → single resource
POST   /api/{module}                → create
PUT    /api/{module}/{id}           → full update
PATCH  /api/{module}/{id}/{action}  → partial action (confirm, cancel, complete…)
DELETE /api/{module}/{id}           → soft delete (ADMIN only)
```

All error responses use this format:
```json
{ "timestamp": "…", "status": 404, "error": "Not Found", "message": "…", "path": "…" }
```

---

## Security model

Two filter chains in `SecurityConfig`:
1. `/api/**` — stateless, JWT in `Authorization: Bearer {token}` header
2. Everything else — stateful session, form login at `/login`

Roles: `ADMIN`, `MEDECIN`, `INFIRMIER`, `SECRETAIRE`, `PHARMACIEN`, `LABORANTIN`, `CAISSIER`, `PATIENT`

Use `@PreAuthorize("hasRole('ADMIN')")` on controller methods for role checks.

---

## Database rules

- **Never modify an existing Flyway migration file** — always create a new one
- All tables have `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
- Deletions are always soft (`deleted_at`) unless noted otherwise
- Money amounts use `NUMERIC(12,2)` — never `FLOAT`
- Full schema in `docs/DATABASE.md`

---

## Design system

CSS is in `backend/src/main/resources/static/css/app.css`.  
Style: clean medical, dark sidebar (`#0f172a`), white surfaces, blue/green accents.

**Key CSS classes to use in templates:**
- Layout: `.layout`, `.sidebar`, `.main`, `.topbar`, `.page-content`
- Navigation: `.nav-item`, `.nav-item.active`, `.nav-section`
- Stats: `.stats-grid`, `.stat-card`, `.stat-card-value`
- Data: `.panel`, `.panel-header`, `.table`, `.table-wrap`
- Forms: `.form-card`, `.form-group`, `.form-label`, `.form-control`, `.form-grid-2`
- Badges: `.badge`, `.badge-green`, `.badge-red`, `.badge-yellow`, `.badge-CONFIRME`, etc.
- Buttons: `.btn`, `.btn-primary`, `.btn-ghost`, `.btn-sm`, `.btn-icon`
- Feedback: `.alert`, `.alert-info`, `.alert-error`, `.empty-state`

**Always use the shared layout** for all web pages:
```html
<html th:replace="~{layouts/base :: layout('Page Title', ~{::content})}">
<body><th:block th:fragment="content">
  <!-- your content here -->
</th:block></body>
</html>
```

---

## Development mode

```bash
cd backend && mvnd spring-boot:run
```

> **Build tool:** this environment has the Maven Daemon (`mvnd`) on PATH, not plain `mvn`. Use `mvnd` for all Maven commands.

- H2 in-memory database (auto-created, wiped on restart)
- Test data seeded by `DataInitializer.java`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:clinicdb`)
- Test accounts: `admin/admin123`, `dr.martin/medecin123`, `secretaire/secretaire123`

---

## How to implement a new module

1. Read the spec in `docs/modules/NN-MODULE.md` first
2. Create package `com.clinic.backend.{module}/`
3. Add entities → run `mvnd compile` to check
4. Add repositories, DTOs, service
5. Add REST controller in `controller/api/`
6. Add web controller in `controller/web/`
7. Create Thymeleaf templates in `templates/{module}/`
8. Create Flyway migration `VN__{module}_tables.sql`
9. Update `docs/OVERVIEW.md` module status table

---

## Current implementation status & BUILD ORDER (the ledger)

> This is the single source of truth for "where are we?". Build modules **strictly in this order** (derived from FK dependencies, NOT from spec numbering). Check off sub-tasks as they land. `[ ]` = todo, `[~]` = in progress, `[x]` = done.
> **Workflow rule (token discipline):** one module per session, implement inline (no sub-agents, no multi-agent GSD skills), `mvnd compile` to verify, tick boxes here, then `/clear` before the next module.
> **👉 NEXT UP:** Wave 2 — module 10 Hospitalization & Beds (08): rooms + beds + hospitalizations (admission → sortie), FK→departments + consultations. New shape (no request→result analog to factor) — model `Room`/`Bed` (occupancy state) + `Hospitalization` (patient, admitting doctor, bed, admit/discharge timestamps, daily rate for later billing). Likely wants a bed-availability board (free vs occupied per department) and a status flow (EN_COURS→SORTI/TRANSFERE) that flips bed occupancy. `clinic_config.module_hospitalization` flag already exists (V4). Patient dossier can gain a "Séjours" tab (mirror how the Imagerie/Laboratoire tabs were wired in `PatientWebController`).

### 🧱 Wave 0 — Foundations
- [x] **0. Flyway baseline** — `V1__baseline_auth_patients.sql` formalizes the existing `users` + `patients` schema; Flyway added to pom, `ddl-auto` flipped to `validate`. Verified: Flyway migrates + Hibernate validate passes on H2. (NB: no Appointments table existed yet — that schema lands with module 5.)
- [~] **1. Auth & Roles** (01) — 🟢 entities + JWT done · admin user-management UI + role assignment done (`AdminUserWebController`, `UserService`, `UserDto`, `admin/users/{list,form}.html`; `V2__auth_user_admin_fields.sql` adds `active`/`created_at`/`deleted_at`; `Role` enum now holds all 8 roles). · TODO (deferred, lower prio): `/profile` page + self-service change-password, account lockout
- [x] **2. Departments** (11, pulled up) — reference module: `departments` table (code/name/description/color/is_active), soft-disable via `is_active` (no `deleted_at` per schema). `Department` entity + repo + `DepartmentService` (code uniqueness, toggle) + `DepartmentDto`; `DepartmentApiController` (`/api/departments`, ADMIN-only writes) + `DepartmentWebController` (`/admin/departments`, ADMIN); `admin/departments/{list,form}.html`; nav link added; `V3__departments_tables.sql` (DDL + 10 seeded depts). Verified: Flyway migrates to v3 + Hibernate validate passes + app boots on H2.
- [x] **3. Config & Catalogs** (14, pulled up — socle) — four reference tables via `V4__config_catalogs_tables.sql` (DDL + seeds): `clinic_config` (singleton: identity, feature flags, payments, numbering prefixes), `insurance_providers`, `act_catalog` (FK→departments), `lab_test_catalog`. Entities/repos/services/DTOs in packages `clinicconfig`, `insurance`, `catalog`. Web (ADMIN): `/admin/config` (`AdminConfigWebController` + `admin/config/form.html`), `/admin/insurance`, `/admin/acts`, `/admin/lab-tests` (list+form each). REST (ADMIN-only writes): `/api/insurance-providers`, `/api/acts`, `/api/lab-tests`. Nav links added. **NB:** act-catalog DTO mapping is done inside the service tx (`listAllAsDto`/`getDtoById`) to reach the lazy `department` assoc. Verified end-to-end on H2: Flyway→v4, Hibernate validate passes, all admin pages render 200, create/save POST flows persist + checkbox feature-flags bind correctly. **Bonus fix:** `layouts/base.html` used the removed Thymeleaf 3.1 `#httpServletRequest` object (broke *every* web page over HTTP) — replaced with a `currentUri` model attr exposed by new `config/GlobalModelAdvice.java` (`@ControllerAdvice`).

### 👥 Wave 1 — Core entities
- [x] **4. Patients** (02) — CRUD done · **photo upload done**: new pkg `storage` (`FileStorageService` validates JPEG/PNG/WebP, 5 Mo cap, stores under `app.storage.upload-dir`/patients/{id}/); `config/WebConfig` serves files at `/uploads/**` (kept behind web auth — medical privacy); `PatientService.uploadPhoto`; `POST /api/patients/{id}/photo` (multipart) + `POST /patients/{id}/photo` (web form auto-submits on file pick → flash msg); `detail.html` avatar gains a photo column + flash alerts. Verified: `mvnd compile` BUILD SUCCESS. · **Full history deferred** (blocked, not codeable yet): the dossier tabs depend on consultations/labs/invoices entities from Waves 2–3 — `detail.html` already shows empty-state placeholders; wire real aggregation (`getFullHistory` + `GET /api/patients/{id}/history`) once those modules land.
- [x] **5. Appointments** (03) — built from scratch (no prior code existed despite earlier "CRUD done" note). New pkg `appointment`: `Appointment` entity (FK→patients/users/departments, status PLANIFIE→CONFIRME→EN_COURS→TERMINE + ANNULE/ABSENT), `AppointmentRepository` (filtered `search` with `LEFT JOIN FETCH` on patient/doctor/department, `findActiveForDoctorBetween` for slots, `countOverlaps` for conflicts, `findWithRefsById`), `AppointmentService` (CRUD + `hasConflict` + `getAvailableSlots` 30-min 07:00–20:00 + status transitions; no double-booking; no past RDV unless ADMIN via `SecurityContextHolder`), `AppointmentDto`. REST `AppointmentApiController` (`/api/appointments` list/get/create/update + PATCH confirm/start/complete/cancel + `GET /slots`). Web `AppointmentWebController` (`/appointments` day, `/appointments/week` 7-day×30-min grid, new/edit forms, status POST actions). Templates `appointments/{list,week,form}.html`; `.week-grid`/`.week-appt` styles appended to `app.css`. `V5__appointments_tables.sql` (the deferred appointments table + indexes). 4 seed RDV in `DataInitializer`. **NB:** OSIV is OFF in this project — read paths that map detached entities to DTO must fetch refs (`getDtoById`/`findWithRefsById`) or hit `LazyInitializationException`; `toDto` assumes initialized associations. Verified end-to-end on H2: Flyway→v5, Hibernate validate passes, day/week/new/edit pages render 200, conflict rejected, valid+admin-past create OK, `/api/appointments` list + `/slots` (correctly excludes booked 09:00/10:30/16:00) work via JWT.
- [x] **6. Consultations & Prescriptions** (04) — clinical pivot. New pkg `consultation`: `Consultation` (FK→appointments/patients/users/departments; vitals as `BigDecimal`/`Integer`; status EN_COURS→TERMINE/ANNULE), `Prescription` + `PrescriptionItem` (cascade-all/orphan-removal item list, `addItem` keeps both sides in sync). Repos with `findWithRefsById` JOIN FETCH (OSIV is OFF). `ConsultationService` (CRUD + `prefillFromAppointment` + `complete` enforces non-blank diagnosis + closed-not-editable-except-ADMIN) and `PrescriptionService` (ORD-YYYY-NNNNN numbering off `clinic_config.prescription_prefix`; `replaceItems` skips blank rows, rejects empty ordonnance). DTOs `ConsultationDto`/`PrescriptionDto`/`PrescriptionItemDto`. REST: `ConsultationApiController` (`/api/consultations` list/get/create/update + PATCH complete/cancel + GET/POST `/{id}/prescription`), `PrescriptionApiController` (`/api/prescriptions/{id}`). Web: `ConsultationWebController` (`/consultations` list/new/detail/edit + complete/cancel + prescription form/save), `PrescriptionWebController` (`/prescriptions/{id}/print` standalone). Templates `consultations/{list,form,detail}.html`, `prescriptions/{form,print}.html`. `V6__consultations_prescriptions_tables.sql`. 2 seed consultations (1 TERMINE+ordonnance, 1 EN_COURS) in `DataInitializer`. Patient dossier **Consultations tab now wired** to real history (`PatientWebController` injects `ConsultationService.findForPatient`). **Two NBs:** (1) `diagnosis` is NULLABLE in V6 (deviates from DATABASE.md NOT NULL) — the spec rule is "diagnostic obligatoire pour CLÔTURER", enforced in-service on EN_COURS→TERMINE, so it can stay empty while in progress. (2) Web `savePrescription` must NOT trust `dto.getId()` — the `/{id}/prescription` path var (consultation id) gets bound into the DTO's same-named field; it branches on `findDtoForConsultation` instead. **Bonus fix:** `patients/detail.html` Médical tab hit `LazyInitializationException` on `assignedDoctor.fullName` (OSIV off, latent since module 4) — added `PatientRepository.findWithDoctorById` + `PatientService.getByIdWithDoctor`. **PDF deferred:** ordonnance is a print-optimized standalone HTML (browser → Print → PDF); the binary `/{id}/pdf` endpoints need a PDF lib (none in pom) — add with Pharmacy or later. Verified end-to-end on H2: Flyway→v6, Hibernate validate passes, list/detail/new/edit render 200, consultation create + diagnosis-gated clôture + prescription create/edit-in-place (no dupes, ORD increments 00001→00002) + print page + dossier history + `/api/consultations` via JWT all work.

### 🏥 Wave 2 — Downstream clinical
- [x] **7. Pharmacy & Stock** (05) — new pkg `pharmacy`: `Drug` (catalogue: nom/DCI/forme/dosage/unité, `requires_prescription`, `is_active`), `StockItem` (lots: `batch_number`/`expiry_date`/`quantity`/`quantity_alert`/`selling_price`/`received_by`), `Dispensation` + `DispensationItem` (cascade-all/orphan-removal, `addItem` sync). Repos: `DrugRepository` (`search` q+category, categories), `StockItemRepository` (`findAvailableForDrug` FIFO = expiry ASC then received ASC + qty>0 + non-périmé, `findLowStock`, `findExpiringBetween`, `totalStockValue`), `DispensationRepository` (`findWithRefsById`, `findAllWithRefs`, `findTopDispensedSince` → `Object[]`). `PharmacyService` (drugs CRUD+toggle, `receiveStock`, **`dispense` FIFO** allocates earliest-expiring batches first, rejects insuffisant/périmé, marks ordonnance dispensée, `prefillFromPrescription`, dashboard aggregate) + `StockAlertService` (`@Scheduled` cron `0 0 8 * * *`; `@EnableScheduling` added to `ClinicApplication`). DTOs `DrugDto`/`StockItemDto`(+flags low/expired/expiringSoon)/`DispensationDto`/`DispensationItemDto`/`PharmacyDashboardDto`. REST `PharmacyApiController` (`/api/pharmacy/{drugs,stock,stock/receive,stock/low,stock/expiring,dispensations}`; writes `hasAnyRole('PHARMACIEN','ADMIN')`). Web `PharmacyWebController` (`/pharmacy` dashboard, `/pharmacy/drugs` {list,new,edit,toggle}, `/pharmacy/stock` + `/stock/receive`, `/pharmacy/dispensations` {list,new,detail}). Templates under `pharmacy/`. `V7__pharmacy_tables.sql` (+ formalizes the dangling `prescription_items.drug_id` FK→drugs from V6). Seed: 4 drugs + 4 stock lots (1 low, 1 expiring-soon) in `DataInitializer`, rx items now carry `drug_id`. **NBs:** (1) Thymeleaf `#numbers.formatDecimal` thousands keyword is **`WHITESPACE`**, not `SPACE` (latter → `TemplateProcessingException`/500). (2) dispense input lines are `{drugId, quantity}`; the service may emit *several* `DispensationItem`s per line when FIFO spans batches. (3) `/movements` unified feed deferred (no `stock_movements` table in DATABASE.md — traceability = stock receptions + dispensation_items). (4) API still returns bare 500 on `IllegalArgumentException`/`IllegalStateException` (no global `@RestControllerAdvice` exists project-wide — pre-existing; web flow catches + shows messages). Verified end-to-end on H2: Flyway→v7, Hibernate validate passes, all pharmacy web pages render 200 (session login), **FIFO proven** (qty 15 drained a +30d batch fully then the +2yr batch: 10+5), stock decrements, dispense-once rejected, insufficient rejected (tx rollback, history unchanged), web dispense POST binds `items[i]` + skips blank lines, dashboard counters/alerts correct via JWT.
- [x] **8. Lab** (09) — new pkg `lab`: `LabRequest` (FK→consultations/patients/users; `request_number` LAB-YYYY-NNNNN; priority NORMAL/URGENT; status EN_ATTENTE→EN_COURS→VALIDE→LIVRE (+ANNULE)), `LabRequestItem` (FK→`lab_test_catalog`; status EN_ATTENTE/SAISI; `@OneToOne` result, cascade-all/orphan-removal via `setResultValueObject` sync helper), `LabResult` (result_value/unit/reference_range/is_abnormal + laborantin + validated_at/by). `LabRequestRepository`: `findWithRefsById` (JOIN FETCH items→test→result→users — OSIV off), `search`, **`findWorklist`** (status IN (EN_ATTENTE,EN_COURS), URGENT-first via `ORDER BY CASE`), `findByPatient`, `findMaxSequence`. `LabService` (create-from-consultation, `enterResults` upserts result per item + auto-abnormal + →EN_COURS, `validate` stamps validated_at/by on all results + →VALIDE, `deliver`, `cancel`; numbering off constant prefix `LAB` — **no `lab_prefix` in clinic_config**) + `ResultAbnormalityChecker` (`@Component`: numeric interval "low - high" outside-range, qualitative "Négatif" expected vs positive value; accent-insensitive; never throws). DTOs `LabRequestDto` (carries both `testIds` for create-form checkboxes AND mapped `items`) + `LabRequestItemDto` (test info + result fields flattened). REST `LabApiController` (`/api/lab/{catalog,requests,...}`; create+validate `hasAnyRole('MEDECIN','ADMIN')`, results `hasAnyRole('LABORANTIN','ADMIN')`). Web `LabWebController` (`/lab` worklist, `/lab/requests` list, `/requests/new` create, `/requests/{id}` detail, `/requests/{id}/results` entry, `/validate`+`/deliver`+`/cancel`, `/requests/{id}/bulletin` standalone print). Templates `lab/{worklist,list,form,detail,result-entry,bulletin}.html`. CSS: `badge-VALIDE/LIVRE/SAISI` + `.row-abnormal` appended to app.css. `V8__lab_tables.sql` (lab_requests/lab_request_items/lab_results; `lab_test_catalog` already in V4). Seed: LABORANTIN user (`laborantin/laborantin123`) + 2 requests (1 VALIDE w/ abnormal glycémie for p1, 1 EN_ATTENTE URGENT for p2). Patient dossier **Laboratoire tab wired** (`PatientWebController` injects `labService.findForPatient`); consultation detail gains a "+ Demander des analyses" shortcut. **NBs:** (1) **Thymeleaf layout-title apostrophe gotcha:** `layout('Demande d''analyses', …)` with doubled `''` → `TemplateProcessingException` (could-not-parse) at request time — use a typographic apostrophe `'` instead (compiles fine, 500s only when the page is hit). (2) result-entry form binds `items[__${stat.index}__].field` against the pre-populated DTO item list (indices must exist → GET loads `getDtoById`). (3) abnormality auto-flag is `dto.abnormal || checker.isAbnormal(...)` so the laborantin can also force-tick. (4) same bare-500-on-IllegalState API gap as pharmacy (no global `@RestControllerAdvice`). Verified end-to-end on H2: Flyway→v8, Hibernate validate passes, app boots + seeds load, all 6 lab pages + dossier tab render 200 (session login), full create→enterResults→validate round-trip persists (302s), **abnormality checker proven** (creatinine 18 > [6-12] AND "Positif" vs "Négatif" both auto-flagged → 2 ⚠ badges, status VALIDE).
- [x] **9. Radiology** (10) — new pkg `radiology`, factored from Lab's request→result shape but the "result" is a request-level narrative **report** + image attachments (not per-item numeric values). Entities: `RadiologyExamCatalog` (seeded reference: code/name/type RADIOGRAPHIE|ECHOGRAPHIE|SCANNER|IRM|MAMMOGRAPHIE/region/price), `RadiologyRequest` (FK→consultations/patients/users; `request_number` RAD-YYYY-NNNNN; priority NORMAL/URGENT; status EN_ATTENTE→EN_COURS→VALIDE→LIVRE (+ANNULE)), `RadiologyRequestItem` (FK→catalog), `RadiologyReport` (`@OneToOne` request: findings/conclusion + radiologist + validated_at/by — analog of `LabResult` but one per request), `RadiologyImage` (many per request: file_path/caption, stored via `storage.FileStorageService` under `radiology/{id}`, served at `/uploads/**`). `RadiologyRequestRepository`: `findWithRefsById` (JOIN FETCH patient/doctor/consultation + items→exam + report→users; **images NOT fetched here** — they lazy-load inside the service tx, keeping `items` the only collection join to dodge MultipleBagFetchException), `search`, `findWorklist` (EN_ATTENTE/EN_COURS, URGENT-first), `findByPatient`, `findMaxSequence`. `RadiologyService` (create-from-consultation, `saveReport`→EN_COURS, `addImage`/`deleteImage`, `validate` requires non-blank findings + stamps validated_at/by, `deliver`, `cancel`; numbering off constant prefix `RAD`). REST `RadiologyApiController` (`/api/radiology/{catalog,requests,.../report,.../images,.../validate,.../deliver,.../cancel}`; all writes `hasAnyRole('MEDECIN','ADMIN')` — **no RADIOLOGUE role exists**, radiologist = a MEDECIN). Web `RadiologyWebController` (`/radiology` worklist, `/requests` list, `/requests/new`, `/requests/{id}` detail, `/requests/{id}/report` report+image mgmt, `/validate`+`/deliver`+`/cancel`, `/requests/{id}/bulletin` standalone print). Templates `radiology/{worklist,list,form,detail,report-form,bulletin}.html`; `.radio-gallery`/`.radio-thumb` appended to app.css. `V9__radiology_tables.sql` (5 tables, 8 seeded exams). Seed: `radiologue/radiologue123` user (MEDECIN) + 2 requests (1 VALIDE w/ écho report for p1, 1 EN_ATTENTE URGENT for p2). Patient dossier **Imagerie tab wired** (`PatientWebController` injects `radiologyService.findForPatient`); consultation detail gains a "+ Demander une imagerie" shortcut. **NBs:** (1) **API list maps to DTO *inside* the tx** via `searchDto()` — unlike Lab's `/api/lab` list which maps entities post-tx (works for lab only because its list.html/toDto touch just patient+doctor; radiology's toDto walks items+images+report so post-tx mapping would `LazyInitializationException` with OSIV off). (2) report+images editable only while status ∉ {VALIDE,LIVRE,ANNULE}; images served behind web auth (medical privacy, same as patient photos). (3) PDF still deferred (bulletin is print-optimized HTML → browser Print, same as Lab/ordonnance). (4) same bare-500-on-IllegalState API gap as pharmacy/lab (no global `@RestControllerAdvice`). Verified end-to-end on H2: Flyway→v9, Hibernate validate passes, app boots + seeds load, all 6 radiology pages + dossier Imagerie tab render 200 (session login), full create→saveReport→validate round-trip persists (302s, RAD numbering 00001/00002 seed → 00003 new), multipart image upload stored+served (200 image/png), **validate-without-report guard proven** (stays EN_ATTENTE + "compte-rendu doit être saisi" flash).
- [ ] **10. Hospitalization & Beds** (08) — rooms, hospitalizations → departments + consultations
- [ ] **11. Maternity** (06) — maternity_records → patients + consultations

### 💰 Wave 3 — Aggregation & cross-cutting
- [ ] **12. Billing & Payments** (07) — ⚪ Invoice entity exists · invoices aggregate consultations/hospit/acts/insurance — must come AFTER billable modules
- [ ] **13. Notifications** (12) — SMS/email: appointment reminders, lab-ready, stock alerts
- [ ] **14. Reports & Statistics** (13) — reads all tables; only meaningful once data exists

### 🚀 Wave 4 — Continuous
- [x] **15. Deployment** (15) — docker-compose.yml done · TODO (ongoing): harden Flyway, backups, .env, audit trail

---

## What NOT to do

- Don't use `float` or `double` for money — use `BigDecimal`
- Don't hard-delete medical records — always soft delete
- Don't put business logic in controllers — only in services
- Don't create new CSS files — extend `app.css`
- Don't modify existing Flyway migrations
- Don't add `@Transactional` to controllers — only services
- Don't use `System.out.println` — use `@Slf4j` + `log.info()`

---

*Last updated: 2024 — keep this file in sync when making architectural changes.*
