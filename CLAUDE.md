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
cd backend && mvn spring-boot:run
```

- H2 in-memory database (auto-created, wiped on restart)
- Test data seeded by `DataInitializer.java`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:clinicdb`)
- Test accounts: `admin/admin123`, `dr.martin/medecin123`, `secretaire/secretaire123`

---

## How to implement a new module

1. Read the spec in `docs/modules/NN-MODULE.md` first
2. Create package `com.clinic.backend.{module}/`
3. Add entities → run `mvn compile` to check
4. Add repositories, DTOs, service
5. Add REST controller in `controller/api/`
6. Add web controller in `controller/web/`
7. Create Thymeleaf templates in `templates/{module}/`
8. Create Flyway migration `VN__{module}_tables.sql`
9. Update `docs/OVERVIEW.md` module status table

---

## Current implementation status

| Module | Status |
|---|---|
| Auth & Roles | 🟡 Partial — entities + JWT done, admin UI missing |
| Patients | 🟡 Partial — CRUD done, photo upload + full history missing |
| Appointments | 🟡 Partial — CRUD done, week view + slot check missing |
| Consultations | ⚪ Not started |
| Pharmacy | ⚪ Not started |
| Maternity | ⚪ Not started |
| Billing | ⚪ Not started (Invoice entity exists) |
| Hospitalization | ⚪ Not started |
| Lab | ⚪ Not started |
| Radiology | ⚪ Not started |
| Departments | ⚪ Not started |
| Notifications | ⚪ Not started |
| Reports | ⚪ Not started |
| Config | ⚪ Not started |
| Deployment | ✅ docker-compose.yml done |

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
