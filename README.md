# HMS — Hospital Management System · Patient Module

A production-grade **Spring Boot** REST API implementing the complete patient module of a Hospital Management System. Built with an event-sourced patient entity, JWT-based role security, phonetic search, FHIR R4 interoperability, and a full clinical EMR feature set covering vitals, SOAP notes, prescriptions, lab orders, and billing.

---

## Table of Contents

1. [Features](#features)
2. [Tech Stack](#tech-stack)
3. [Architecture](#architecture)
4. [Project Structure](#project-structure)
5. [Database Schema](#database-schema)
6. [Security & Roles](#security--roles)
7. [API Endpoints](#api-endpoints)
8. [Request & Response Flow](#request--response-flow)
9. [Getting Started](#getting-started)
10. [Environment Variables](#environment-variables)
11. [Running Tests](#running-tests)

---

## Features

### Phase 1 — Patient Registration & Core
- Full patient registration with demographics, emergency contacts, and medical history
- Quick registration (minimal fields for walk-in patients)
- Event-sourced `Patient` entity — every update creates an immutable version row
- Blood group, allergies, and chronic condition tracking
- Patient status lifecycle: `ACTIVE → INACTIVE → DECEASED`
- QR code generation per patient (Base64 PNG via ZXing)
- Patient photo upload/retrieval (Base64 storage)

### Phase 2 — Insurance, Contacts & Search
- Insurance records with provider, policy number, group number, and coverage type
- Emergency contact management (add/update/delete, primary flag)
- Paginated patient list with cursor-based slicing (`Slice<T>` — no count queries)
- Multi-field LIKE search (name, phone, email, patient ID)
- Filter by status, gender, and blood group
- Audit log on every API call — immutable PostgreSQL audit trail

### Phase 3 — Operational Enhancements
- Quick registration flow (minimal mandatory fields)
- Data quality dashboard: incomplete registrations, missing insurance, missing photos
- ZIP code auto-fill lookup
- Encrypted sensitive fields (BouncyCastle AES-GCM via `AttributeConverter`)

### Phase 4 — Advanced Search & Integrity
- **Phonetic search**: Soundex + Metaphone encoding (finds "Jonson" → "Johnson")
- **Fuzzy search**: Levenshtein distance second-pass on name-like queries
- **FHIR R4**: `/fhir/Patient` resource endpoint (read + search, HL7 FHIR compliant)
- **Duplicate detection**: similarity scoring on name + DOB + phone before registration
- **Patient merge**: preview + execute merge of duplicate patient records (ADMIN only)
- **Insurance verification**: scheduled batch job + on-demand trigger + paginated report

### Phase 5 — Appointments, Consents & Patient Portal
- Doctor availability configuration (day-of-week, start/end time, slot duration)
- Available slot calculation with conflict detection
- Appointment booking (book, view, update status, cancel with reason)
- Consent management (5 types: `TREATMENT`, `DATA_SHARING`, `RESEARCH`, `MARKETING`, `TELEMEDICINE`)
- Consent expiry tracking and alert system
- Consent document upload/download (file storage)
- Patient self-service portal (register, login, view/update own profile, sign consents)

### Phase 6 — Clinical EMR
- **Vital Signs**: temperature, BP (systolic/diastolic), heart rate, respiratory rate, SpO2, weight, height — BMI auto-calculated; critical threshold detection
- **Clinical Notes**: SOAP / PROGRESS / ADMISSION / DISCHARGE types; note finalization locks editing permanently
- **Prescriptions**: medication, dosage, frequency, duration, refill tracking (refill decrement + discontinue workflow)
- **Lab Orders**: order lifecycle `PENDING → COLLECTED → IN_LAB → COMPLETED`; per-order results with abnormal flags (H / L / HH / LL)
- **Billing**: invoice creation with line items, issue workflow, payment recording; auto-transitions to `PARTIALLY_PAID` / `PAID`

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.4.5 |
| Security | Spring Security 6 + JWT (jjwt 0.13) |
| Persistence | Spring Data JPA + Hibernate 6 |
| Database | PostgreSQL 16 |
| Migrations | Flyway 10 (21 versioned scripts) |
| Search | JPQL LIKE + Levenshtein (Apache Commons Text) + Soundex/Metaphone (Apache Commons Codec) |
| Encryption | BouncyCastle AES-GCM (sensitive field `AttributeConverter`) |
| FHIR | Custom FHIR R4 mapper (HL7 JSON structure) |
| QR Codes | Google ZXing 3.5 |
| Scheduling | Spring `@Scheduled` (insurance verification job) |
| Validation | Jakarta Bean Validation 3 (`@NotBlank`, `@Min`, `@DecimalMin`) |
| Connection Pool | HikariCP (max 10 connections) |
| Observability | Spring Boot Actuator (`/actuator/health`, `/actuator/info`) |
| Build | Maven 3 |
| Containerization | Docker + Docker Compose |
| Testing | JUnit 5 + Spring MockMvc + `@SpringBootTest` |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT (React UI / REST)                  │
└───────────────────────────────┬─────────────────────────────────┘
                                │ HTTPS
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Spring Boot Application                      │
│                                                                  │
│  ┌─────────────┐   ┌──────────────────────────────────────────┐ │
│  │  JWT Filter  │──▶│             Security Layer               │ │
│  │ (per-request)│   │  - Role-based: ADMIN/DOCTOR/NURSE/RECEPT │ │
│  └─────────────┘   │  - Object-level: PatientPermissionEval    │ │
│                    └───────────────────┬──────────────────────┘ │
│                                        │                         │
│  ┌─────────────────────────────────────▼──────────────────────┐ │
│  │                    Controller Layer (REST)                   │ │
│  │  PatientController · AppointmentController · LabController  │ │
│  │  BillingController · VitalSignsController · FhirController  │ │
│  │  ConsentController · PrescriptionController · Portal...     │ │
│  └─────────────────────────────────────┬──────────────────────┘ │
│                                        │ @Audited AOP            │
│  ┌─────────────────────────────────────▼──────────────────────┐ │
│  │                    Application / Service Layer               │ │
│  │  PatientService · AppointmentService · BillingService       │ │
│  │  VitalSignsService · ClinicalNoteService · LabService       │ │
│  │  PrescriptionService · InsuranceVerificationJob (scheduled) │ │
│  └─────────────────────────────────────┬──────────────────────┘ │
│                                        │                         │
│  ┌─────────────────────────────────────▼──────────────────────┐ │
│  │                   Infrastructure / Repository Layer          │ │
│  │  PatientRepository · PatientSearchRepository (JPQL+fuzzy)  │ │
│  │  VitalSignsRepository · InvoiceRepository · LabRepository  │ │
│  └─────────────────────────────────────┬──────────────────────┘ │
│                                        │                         │
│  ┌─────────────────────────────────────▼──────────────────────┐ │
│  │                       Domain Layer                           │ │
│  │  Patient (event-sourced, @Immutable) · VitalSigns           │ │
│  │  ClinicalNote · Prescription · LabOrder · Invoice           │ │
│  │  Appointment · ConsentRecord · PatientFamily                │ │
│  └─────────────────────────────────────┬──────────────────────┘ │
└────────────────────────────────────────┼────────────────────────┘
                                         │
                    ┌────────────────────▼────────────────────┐
                    │         PostgreSQL 16 Database            │
                    │  21 tables · partitioned audit_logs       │
                    │  invoice_number_seq · Flyway V001–V021   │
                    └─────────────────────────────────────────┘
```

### Key Design Decisions

| Pattern | Detail |
|---------|--------|
| **Event-Sourced Patient** | `patients` table stores all versions; each `PUT /patients/{id}` appends a new row. `PatientSearchRepository` always reads latest version via `max(patientId) GROUP BY businessId`. |
| **businessId as public key** | Internal PK is `patient_id` (formatted `P-YYYYMMDD-XXXX`). External API always uses `businessId` (UUID). Cross-entity FK is `patient_business_id UUID`. |
| **Immutable AuditLog** | `audit_logs` has a PostgreSQL trigger blocking any UPDATE/DELETE. The JPA entity carries `@org.hibernate.annotations.Immutable` to prevent Hibernate dirty-check loops. |
| **Object-Level Security** | `PatientPermissionEvaluator` implements `hasPermission(auth, patient, 'write')` — ADMIN always passes; RECEPTIONIST + DOCTOR pass for their own patients. |
| **Field Encryption** | Insurance `policyNumber` and other PII fields use a JPA `AttributeConverter` backed by AES-GCM (BouncyCastle) so data-at-rest is encrypted. |
| **Slice Pagination** | List endpoints return `Slice<T>` (no `COUNT(*)`) for O(1) next-page detection — just fetches `pageSize+1` rows. |

---

## Project Structure

```
src/main/java/com/hospital/
├── billing/
│   ├── api/                    # BillingController + DTOs
│   ├── application/            # BillingService
│   ├── domain/                 # Invoice, InvoiceLineItem, Payment, InvoiceStatus, PaymentMethod
│   └── infrastructure/         # InvoiceRepository, PaymentRepository
├── config/
│   └── SchedulingConfig.java   # Enables @Scheduled
├── fhir/
│   ├── FhirController.java     # /fhir/Patient endpoints
│   └── FhirPatientMapper.java  # Patient → FHIR R4 JSON
├── patient/
│   ├── api/                    # 15 REST controllers + all DTOs
│   ├── application/            # 12 service classes
│   ├── domain/                 # Patient, VitalSigns, ClinicalNote, Prescription,
│   │                           #   LabOrder, LabResult, Appointment, ConsentRecord,
│   │                           #   Insurance, PatientFamily, PatientRelationship + enums
│   └── infrastructure/         # 15 Spring Data repositories + PatientSearchRepository
├── portal/
│   ├── api/                    # PatientPortalAuthController, PatientPortalController
│   ├── application/            # PatientPortalService
│   ├── domain/                 # PatientCredential
│   └── infrastructure/         # PatientCredentialRepository
├── security/
│   ├── audit/                  # AuditLog entity, AuditInterceptor (AOP), AuditLogController
│   ├── auth/                   # AuthController, LoginRequest/Response
│   ├── authorization/          # PatientPermissionEvaluator, SecurityContextHelper
│   ├── config/                 # SecurityConfig (filter chain, in-memory users)
│   ├── encryption/             # SensitiveDataConverter (AES-GCM)
│   └── jwt/                    # JwtTokenProvider, JwtAuthenticationFilter
├── shared/
│   └── exception/              # GlobalExceptionHandler (RFC 7807 Problem Details)
└── storage/
    └── ConsentDocumentStorageService.java

src/main/resources/
├── application.yml
└── db/migration/               # V001–V021 Flyway SQL scripts
```

---

## Database Schema

### Core Tables

| Table | Description |
|-------|-------------|
| `patients` | Event-sourced — multiple rows per patient (one per version). Latest = `max(patient_id) GROUP BY business_id`. |
| `medical_histories` | Blood group, allergies, chronic conditions keyed by `patient_business_id`. |
| `emergency_contacts` | Multiple contacts per patient, `is_primary` flag. |
| `insurance` | One active insurance per patient (provider, policy, group, coverage type, encrypted). |
| `patient_photos` | Base64 photo + MIME type per patient. |
| `appointments` | Doctor appointments with status lifecycle and cancel reason. |
| `doctor_availability` | Day-of-week slots with duration per doctor. |
| `consent_records` | Per-consent-type records with signed date, expiry, and document path. |
| `patient_families` | Household groupings (UUID household ID). |
| `patient_relationships` | Named relationships between patient pairs (SPOUSE, PARENT, etc.). |
| `vital_signs` | Timestamped vitals per patient — temperature, BP, HR, SpO2, weight, height. |
| `clinical_notes` | SOAP/PROGRESS/ADMISSION/DISCHARGE notes with finalization lock. |
| `prescriptions` | Medications with dosage, refills remaining, status, expiry. |
| `lab_orders` | Lab order lifecycle (PENDING → COMPLETED) with priority (ROUTINE/URGENT/STAT). |
| `lab_results` | Per-test results with abnormal flags (H/L/HH/LL). |
| `invoices` | Invoices with status machine (DRAFT→ISSUED→PARTIALLY_PAID→PAID→OVERDUE). |
| `invoice_line_items` | Line items per invoice (description, service code, quantity, unit price). |
| `payments` | Payments recorded against invoices (CASH/CREDIT_CARD/INSURANCE/CHECK). |
| `patient_credentials` | Hashed password + salt for patient portal logins. |
| `audit_logs` | Partitioned by year, immutable — records every API action (user, action, resource, IP). |

### Flyway Migrations

| Version | Description |
|---------|-------------|
| V001 | Create audit_logs (partitioned table + 2026/2027 partitions) |
| V002 | Create patients schema |
| V003 | Create insurance schema |
| V004 | Add audit fields to emergency_contacts |
| V005 | Fix unique identity constraint |
| V006 | Add patient_photos table |
| V007 | Add `is_registration_complete` flag |
| V008 | Refresh `patients_latest` view |
| V009 | Add PostgreSQL immutability trigger on audit_logs |
| V010 | Create patient_families schema |
| V011 | Create patient_relationships schema |
| V012 | Add insurance verification status to insurance table |
| V013 | Create appointments + doctor_availability schema |
| V014 | Create patient_credentials (portal) schema |
| V015 | Create consent_records schema |
| V016 | Add consent document directory comment |
| V017 | Create vital_signs table |
| V018 | Create clinical_notes table |
| V019 | Create prescriptions table |
| V020 | Create lab_orders + lab_results tables |
| V021 | Create invoice_number_seq + invoices + invoice_line_items + payments |

---

## Security & Roles

Authentication is **stateless JWT** (HS512, configurable expiry). Every request passes through `JwtAuthenticationFilter`.

### Staff Roles (in-memory for development)

| Username | Password | Role | Permissions |
|----------|----------|------|-------------|
| `admin` | `admin123` | `ADMIN` | All endpoints |
| `doctor` | `pass123` | `DOCTOR` | Clinical + read patient |
| `nurse` | `pass123` | `NURSE` | Vitals, labs, prescriptions (read), appointments |
| `receptionist` | `pass123` | `RECEPTIONIST` | Registration, appointments, billing, consents |

### Patient Portal Role

Patients self-register at `/api/portal/auth/register` and authenticate at `/api/portal/auth/login`. Their JWT carries the `PATIENT` role and is scoped to their own `patient_business_id` only.

### Audit Trail

Every API call that modifies data is captured by `AuditInterceptor` (Spring AOP `@Around` advice on `@Audited`-annotated controller methods). Audit records are immutable — a PostgreSQL trigger raises an exception on any UPDATE or DELETE.

---

## API Endpoints

Base URL: `http://localhost:8080`

> **Auth header:** `Authorization: Bearer <token>` (required on all endpoints except login/portal-auth)

---

### Authentication

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/login` | None | Staff login → JWT |
| `POST` | `/api/portal/auth/register` | None | Patient portal self-register |
| `POST` | `/api/portal/auth/login` | None | Patient portal login → JWT |

---

### Patient Management

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/patients` | ADMIN, RECEPTIONIST | Register patient (full form) |
| `POST` | `/api/v1/patients/quick` | ADMIN, RECEPTIONIST | Quick register (minimal fields) |
| `GET` | `/api/v1/patients` | All staff | Search/list patients — params: `query`, `status`, `gender`, `bloodGroup`, `page`, `size` |
| `GET` | `/api/v1/patients/{businessId}` | All staff | Get patient detail (full, with upcoming appointments + consent alerts) |
| `PUT` | `/api/v1/patients/{businessId}` | Write permission | Update patient demographics |
| `PATCH` | `/api/v1/patients/{businessId}/status` | ADMIN | Change patient status |
| `GET` | `/api/v1/patients/{businessId}/history` | All staff | Get all versions of patient record |
| `GET` | `/api/v1/patients/{businessId}/qr-code` | All staff | Download patient QR code (PNG) |

---

### Emergency Contacts

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `GET` | `/api/v1/patients/{businessId}/emergency-contacts` | All staff | List emergency contacts |
| `POST` | `/api/v1/patients/{businessId}/emergency-contacts` | Write | Add emergency contact |
| `PUT` | `/api/v1/patients/{businessId}/emergency-contacts/{contactId}` | Write | Update contact |
| `DELETE` | `/api/v1/patients/{businessId}/emergency-contacts/{contactId}` | Write | Delete contact |

---

### Insurance

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `GET` | `/api/v1/patients/{businessId}/insurance` | All staff | Get insurance record |
| `POST` | `/api/v1/patients/{businessId}/insurance` | Write | Create insurance |
| `PUT` | `/api/v1/patients/{businessId}/insurance` | Write | Update insurance |

---

### Photos

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/patients/{businessId}/photo` | ADMIN, RECEPTIONIST | Upload photo (multipart/form-data) |
| `GET` | `/api/v1/patients/{businessId}/photo` | All staff | Download photo |

---

### Appointments

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/appointments` | ADMIN, RECEPTIONIST | Book appointment |
| `GET` | `/api/v1/appointments/{appointmentId}` | All staff | Get appointment |
| `PATCH` | `/api/v1/appointments/{appointmentId}/status` | All staff | Update status (CONFIRMED/COMPLETED/CANCELLED) |
| `GET` | `/api/v1/patients/{businessId}/appointments` | All staff | List patient appointments |
| `POST` | `/api/v1/doctors/{doctorId}/availability` | ADMIN | Set doctor availability |
| `GET` | `/api/v1/doctors/{doctorId}/availability` | All staff | Get doctor availability |
| `GET` | `/api/v1/doctors/{doctorId}/available-slots?date=YYYY-MM-DD` | ADMIN, RECEPTIONIST | List available slots for date |

---

### Consents

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `GET` | `/api/v1/patients/{businessId}/consents` | All staff | List all consents |
| `POST` | `/api/v1/patients/{businessId}/consents/{type}/sign` | ADMIN, RECEPTIONIST | Sign consent (`type`: TREATMENT, DATA_SHARING, RESEARCH, MARKETING, TELEMEDICINE) |
| `POST` | `/api/v1/patients/{businessId}/consents/{consentId}/document` | ADMIN, RECEPTIONIST | Upload consent document |
| `GET` | `/api/v1/patients/{businessId}/consents/{consentId}/document` | All staff | Download consent document |
| `DELETE` | `/api/v1/patients/{businessId}/consents/{consentId}` | ADMIN | Revoke consent |
| `GET` | `/api/v1/admin/consents/missing` | ADMIN | Patients with missing required consents |

---

### Vital Signs

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/patients/{businessId}/vitals` | All staff | Record vitals (temp, BP, HR, SpO2, weight, height) |
| `GET` | `/api/v1/patients/{businessId}/vitals` | All staff | Paginated vitals history |
| `GET` | `/api/v1/patients/{businessId}/vitals/latest` | All staff | Most recent vitals reading |

---

### Clinical Notes

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/patients/{businessId}/notes` | ADMIN, DOCTOR, NURSE | Create note (`noteType`: SOAP, PROGRESS, ADMISSION, DISCHARGE) |
| `GET` | `/api/v1/patients/{businessId}/notes` | All staff | List notes |
| `PUT` | `/api/v1/patients/{businessId}/notes/{noteId}` | ADMIN, DOCTOR, NURSE | Update note (blocked if finalized) |
| `POST` | `/api/v1/patients/{businessId}/notes/{noteId}/finalize` | ADMIN, DOCTOR | Finalize note (permanently locks it) |

---

### Prescriptions

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/patients/{businessId}/prescriptions` | ADMIN, DOCTOR | Prescribe medication |
| `GET` | `/api/v1/patients/{businessId}/prescriptions` | All staff | List prescriptions |
| `PATCH` | `/api/v1/patients/{businessId}/prescriptions/{prescriptionId}/discontinue` | ADMIN, DOCTOR | Discontinue with reason |
| `POST` | `/api/v1/patients/{businessId}/prescriptions/{prescriptionId}/refill` | All staff | Request refill (decrements `refillsRemaining`) |

---

### Lab Orders & Results

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/patients/{businessId}/lab-orders` | ADMIN, DOCTOR | Create lab order (priority: ROUTINE/URGENT/STAT) |
| `GET` | `/api/v1/patients/{businessId}/lab-orders` | All staff | List orders with nested results |
| `PATCH` | `/api/v1/lab-orders/{orderId}/status` | ADMIN, DOCTOR, NURSE | Update order status |
| `POST` | `/api/v1/lab-orders/{orderId}/results` | ADMIN, DOCTOR, NURSE | Add result to order |
| `GET` | `/api/v1/patients/{businessId}/lab-orders/abnormal` | All staff | All abnormal results for patient |

---

### Billing & Invoices

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/patients/{businessId}/invoices` | ADMIN, RECEPTIONIST | Create invoice with line items |
| `GET` | `/api/v1/patients/{businessId}/invoices` | All staff | List patient invoices (with line items + payments) |
| `GET` | `/api/v1/invoices/{invoiceId}` | All staff | Get invoice detail |
| `POST` | `/api/v1/invoices/{invoiceId}/issue` | ADMIN | Issue invoice (DRAFT → ISSUED) |
| `POST` | `/api/v1/invoices/{invoiceId}/payments` | ADMIN, RECEPTIONIST | Record payment; auto-transitions to PARTIALLY_PAID / PAID |

---

### Patient Merge & Deduplication

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `GET` | `/api/v1/admin/patients/merge-preview?sourceId=&targetId=` | ADMIN | Preview merge result |
| `POST` | `/api/v1/admin/patients/merge` | ADMIN | Execute merge (source record marked INACTIVE) |

---

### Family & Relationships

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/patients/{businessId}/family` | Write | Link to household |
| `GET` | `/api/v1/patients/{businessId}/family` | All staff | Get family/household |
| `DELETE` | `/api/v1/patients/{businessId}/family` | Write | Unlink from household |
| `GET` | `/api/v1/families/{householdId}/members` | All staff | List all household members |
| `POST` | `/api/v1/patients/{businessId}/relationships` | Write | Add named relationship to another patient |
| `GET` | `/api/v1/patients/{businessId}/relationships` | All staff | List relationships |
| `DELETE` | `/api/v1/patients/{businessId}/relationships/{relationshipId}` | Write | Remove relationship |

---

### Insurance Verification (Admin)

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/admin/insurance/verify-all` | ADMIN | Trigger batch verification job |
| `GET` | `/api/v1/admin/insurance/verification-report` | ADMIN | Paginated verification status report |

---

### FHIR R4

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `GET` | `/fhir/Patient/{businessId}` | Authenticated | HL7 FHIR R4 Patient resource |
| `GET` | `/fhir/Patient?name=&birthdate=` | Authenticated | FHIR Patient search |

---

### Data Quality & Audit

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `GET` | `/api/v1/admin/data-quality` | ADMIN | Report: incomplete registrations, missing insurance/photo |
| `GET` | `/api/v1/admin/audit-logs` | ADMIN | Global audit log (paginated, filterable) |
| `GET` | `/api/v1/patients/{businessId}/audit-logs` | All staff | Patient-scoped audit log |

---

### Patient Portal

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/portal/v1/me` | PATIENT | View own profile |
| `PUT` | `/api/portal/v1/me/contact` | PATIENT | Update contact info |
| `PUT` | `/api/portal/v1/me/insurance` | PATIENT | Update own insurance |
| `GET` | `/api/portal/v1/me/appointments` | PATIENT | View own appointments |
| `PUT` | `/api/portal/v1/me/pre-registration` | PATIENT | Complete pre-registration |
| `GET` | `/api/portal/v1/me/consents` | PATIENT | View own consents |
| `POST` | `/api/portal/v1/me/consents/{type}/sign` | PATIENT | Self-sign consent |

---

### Health

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/actuator/health` | None | DB + disk health check |
| `GET` | `/actuator/info` | None | App info |

---

## Request & Response Flow

### Patient Registration Flow

```
Client
  │
  ├─ POST /api/auth/login  ──────────────────────────────▶ AuthController
  │                                                              │ authenticates → issues JWT
  │◀── { token, role, username } ────────────────────────────────┘
  │
  ├─ POST /api/v1/patients  (Bearer token) ──────────────▶ PatientController
  │    { firstName, lastName, DOB, gender,                       │
  │      phoneNumber, email, address,                      JwtFilter validates token
  │      emergencyContacts[], medicalHistory }                    │
  │                                                        Duplicate check (similarity score)
  │                                                              │ if score >= threshold → 409
  │                                                        PatientService.registerPatient()
  │                                                              │ generates patientId (P-YYYYMMDD-XXXX)
  │                                                              │ generates businessId (UUID)
  │                                                              │ saves Patient (event row #1)
  │                                                              │ saves EmergencyContacts
  │                                                              │ saves MedicalHistory
  │                                                        AuditInterceptor logs: CREATE/PATIENT
  │◀── 201 { patientId, businessId, ... } ────────────────────────┘
```

### Clinical Note Finalization Flow

```
POST /api/v1/patients/{id}/notes        → creates ClinicalNote (isFinalized=false)
PUT  /api/v1/patients/{id}/notes/{nid}  → edits note (allowed while !isFinalized)
POST /api/v1/patients/{id}/notes/{nid}/finalize
    → ClinicalNoteService.finalizeNote()
    → sets isFinalized=true, finalizedAt=now(), finalizedBy=currentUser
    → subsequent PUT returns 409 Conflict ("Note is finalized and cannot be edited")
```

### Invoice Payment Flow

```
POST /api/v1/patients/{id}/invoices        → Invoice status: DRAFT
POST /api/v1/invoices/{id}/issue           → Invoice status: ISSUED
POST /api/v1/invoices/{id}/payments        → BillingService.recordPayment()
    → paidAmount += payment.amount
    → if paidAmount >= patientDue  → status: PAID
    → if paidAmount < patientDue   → status: PARTIALLY_PAID
    → if status.isTerminal()       → 400 Bad Request
```

### Phonetic Search Flow

```
GET /api/v1/patients?query=Jonson
    → PatientSearchRepository.searchPatients("Jonson", ...)
    │
    ├─ Pass 1: JPQL LIKE  →  LOWER(firstName) LIKE '%jonson%'
    │                          (misses "Johnson" — different spelling)
    │
    └─ Pass 2: Fuzzy (if query looks like a name — no digits, no @)
         └─ Levenshteinisance("jonson", firstName) ≤ 2  → matches "Johnson" ✓

GET /api/v1/patients?query=Jonson  (phonetic endpoint)
    → PatientSearchRepository.phoneticSearch("Jonson", ...)
    │
    ├─ Soundex("Jonson") = "J525"  ==  Soundex("Johnson") = "J525"  ✓
    └─ Metaphone("Jonson") = "JNSN"  ==  Metaphone("Johnson") = "JNSN"  ✓
```

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker + Docker Compose
- PostgreSQL 16 (via Docker)

### 1. Clone & Configure

```bash
git clone https://github.com/naveen-thenexushubai/HMS-Patient_Module-Gsd.git
cd HMS-Patient_Module-Gsd
cp .env.example .env   # edit with your values
```

### 2. Start PostgreSQL

```bash
docker-compose up -d postgres
```

### 3. Start the Backend

```bash
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run
```

Flyway runs all 21 migrations automatically on startup.

### 4. Verify

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

### 5. Login

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -m json.tool
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5435` | PostgreSQL port |
| `DB_NAME` | `hospital_db` | Database name |
| `DB_USERNAME` | `hospital_app` | DB user |
| `DB_PASSWORD` | *(required)* | DB password |
| `JWT_SECRET` | *(required)* | HS512 signing secret (min 64 chars) |
| `JWT_EXPIRATION_MS` | `86400000` | JWT lifetime in ms (default 24h) |
| `ADMIN_USERNAME` | `admin` | Admin staff account username |
| `ADMIN_PASSWORD` | `admin123` | Admin staff account password |
| `ENCRYPTION_KEY` | *(required)* | AES-GCM key for field encryption (Base64) |

---

## Running Tests

```bash
# All tests
mvn test

# Phase-specific verification tests
mvn test -Dtest=Phase06ClinicalVerificationTest
mvn test -Dtest=Phase05AppointmentVerificationTest
mvn test -Dtest=Phase05ConsentVerificationTest
mvn test -Dtest=Phase05PortalVerificationTest
```

Test classes use `@SpringBootTest + @AutoConfigureMockMvc` against an in-memory H2 (test profile) or real PostgreSQL. Each test class covers one phase's success criteria end-to-end.

| Test Class | Coverage |
|-----------|---------|
| `Phase06ClinicalVerificationTest` | Vitals append, note finalize lock, prescription refill/discontinue, lab abnormal endpoint, invoice lifecycle |
| `Phase05AppointmentVerificationTest` | Slot generation, booking, conflict detection, status transitions |
| `Phase05ConsentVerificationTest` | Sign, expiry, revoke, missing consents report |
| `Phase05PortalVerificationTest` | Self-register, portal login, own-record access, consent self-sign |

---

## License

This project is for educational and portfolio purposes.
