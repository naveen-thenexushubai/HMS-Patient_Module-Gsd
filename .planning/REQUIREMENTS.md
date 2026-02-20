# Requirements: Hospital Management System - Patient Module

**Defined:** 2026-02-19
**Core Value:** Centralized, secure, and efficient patient information management that serves as the foundation for all other hospital modules

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Security & Compliance (HIPAA Foundation)

- [x] **SEC-01**: System completes HIPAA Security Risk Assessment documenting all PHI storage and transmission paths
- [x] **SEC-02**: System implements audit logging to append-only storage with 6-year retention for all PHI access
- [x] **SEC-03**: System encrypts patient data at rest using PostgreSQL encryption or disk-level encryption
- [x] **SEC-04**: System encrypts patient data in transit using TLS 1.3 for all API communications
- [x] **SEC-05**: System implements field-level encryption for sensitive PHI (SSN, insurance data) using Spring Crypto
- [x] **SEC-06**: System validates JWT tokens and enforces role-based access control on all API endpoints
- [x] **SEC-07**: System implements object-level authorization checking "can this user access THIS patient" on every data access
- [x] **SEC-08**: System stores secrets in environment variables or secrets manager (no hardcoded credentials)
- [x] **SEC-09**: System logs all patient data access with user ID, timestamp, action, resource, and device/IP address

### Patient Registration

- [x] **REG-01**: Receptionist can register new patient with mandatory fields (first name, last name, date of birth, gender, phone number)
- [x] **REG-02**: Receptionist can register new patient with optional fields (email, address, city, state, zip, emergency contact, blood group, allergies, chronic conditions)
- [x] **REG-03**: System automatically calculates and displays patient age from date of birth
- [x] **REG-04**: System validates phone number format (+1-XXX-XXX-XXXX or (XXX) XXX-XXXX or XXX-XXX-XXXX)
- [x] **REG-05**: System validates email address format
- [x] **REG-06**: System generates unique Patient ID in format "P" + year + sequential number (e.g., P2026001)
- [x] **REG-07**: System sets patient status to "ACTIVE" by default on successful registration
- [x] **REG-08**: System displays specific validation error messages for each invalid field
- [x] **REG-09**: System records registration timestamp and user who registered the patient
- [x] **REG-10**: System implements duplicate detection with fuzzy matching on name, DOB, phone before registration
- [x] **REG-11**: System warns receptionist about potential duplicates but allows registration to proceed
- [x] **REG-12**: System requires photo ID verification (scan or upload) during registration

### Patient Search & Discovery

- [x] **SRCH-01**: Staff can search patients by multiple criteria (Patient ID, first name, last name, phone number, email)
- [x] **SRCH-02**: System displays search results in real-time as user types or on Enter key press
- [x] **SRCH-03**: System returns search results within 2 seconds for up to 10,000 patient records
- [x] **SRCH-04**: System implements fuzzy matching on patient names to handle spelling variations
- [x] **SRCH-05**: Staff can filter patient list by status (All, Active, Inactive)
- [x] **SRCH-06**: Staff can filter patient list by gender (All, Male, Female, Other)
- [x] **SRCH-07**: Staff can filter patient list by blood group
- [x] **SRCH-08**: System displays "No patients found" message when no matches exist
- [x] **SRCH-09**: System paginates patient list with 20 patients per page using Slice-based pagination
- [x] **SRCH-10**: System displays patient summary (Patient ID, full name, age, gender, phone, status) in list view

### Patient Profile Management

- [x] **PROF-01**: Staff can view complete patient demographics (Patient ID, name, DOB, age, gender, phone, email, address)
- [x] **PROF-02**: Staff can view patient emergency contact information (name, phone, relationship)
- [x] **PROF-03**: Staff can view patient medical information (blood group, known allergies, chronic conditions)
- [x] **PROF-04**: System displays patient status with color coding (green=active, red=inactive)
- [x] **PROF-05**: System displays patient registration date and registered-by user
- [x] **PROF-06**: System displays patient last-updated date and updated-by user
- [x] **PROF-07**: System shows "Edit Patient" button only to users with edit permissions (Receptionist, Admin)
- [x] **PROF-08**: System hides "Edit Patient" button from read-only users (Doctor, Nurse)
- [x] **PROF-09**: Staff can navigate from patient profile back to patient list

### Patient Information Update

- [x] **UPD-01**: Receptionist/Admin can update patient demographic information via editable form
- [x] **UPD-02**: System pre-populates edit form with current patient data
- [x] **UPD-03**: System makes Patient ID read-only (cannot be edited)
- [x] **UPD-04**: System makes registration date read-only (cannot be edited)
- [x] **UPD-05**: System applies same validation rules as registration to updates
- [x] **UPD-06**: System saves patient updates and displays success message
- [x] **UPD-07**: System records update timestamp and user who made the update
- [x] **UPD-08**: System discards changes on Cancel and returns to profile view
- [x] **UPD-09**: System displays specific validation error messages for invalid fields on update
- [x] **UPD-10**: System publishes PatientUpdated event to message broker after successful update

### Patient Status Management

- [x] **STAT-01**: Admin can deactivate active patient with confirmation dialog
- [x] **STAT-02**: Admin can activate inactive patient without confirmation
- [x] **STAT-03**: System changes patient status to "INACTIVE" on deactivation
- [x] **STAT-04**: System changes patient status to "ACTIVE" on activation
- [x] **STAT-05**: System records status change timestamp and user who made the change
- [x] **STAT-06**: System displays success message after status change
- [x] **STAT-07**: System excludes inactive patients from "Active" filter view
- [x] **STAT-08**: System includes both active and inactive patients in "All" filter view with status indicators

### Insurance Management

- [x] **INS-01**: Receptionist can capture insurance information (provider, policy number, group number, coverage type)
- [x] **INS-02**: System validates insurance policy number format
- [x] **INS-03**: System stores insurance as part of patient record
- [ ] **INS-04**: Staff can view insurance information on patient profile
- [ ] **INS-05**: Receptionist/Admin can update insurance information with audit trail

### Emergency Contacts

- [x] **EMR-01**: Receptionist can add multiple emergency contacts for patient (name, phone, relationship)
- [x] **EMR-02**: System validates emergency contact phone number format
- [x] **EMR-03**: Staff can view all emergency contacts on patient profile
- [x] **EMR-04**: Receptionist/Admin can update or remove emergency contacts with audit trail

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Operational Enhancements

- **OPS-01**: Quick registration for walk-ins with minimal fields and "complete later" workflow
- **OPS-02**: Patient photo capture at registration desk via webcam
- **OPS-03**: Data quality dashboard showing incomplete records and missing information
- **OPS-04**: Smart forms with auto-complete for ZIP codes and insurance plans
- **OPS-05**: Bulk insurance eligibility verification via overnight batch job

### Advanced Features

- **ADV-01**: Family/household linking with shared demographic data
- **ADV-02**: Patient QR code generation for instant lookup
- **ADV-03**: Advanced search with phonetic matching (Soundex/Metaphone)
- **ADV-04**: Relationship and guarantor management (parent-child, spouse, guardian)
- **ADV-05**: FHIR R4 adapter for external EHR integration

### Self-Service & Intelligence

- **SELF-01**: Patient self-service portal for pre-registration and updates
- **SELF-02**: Intelligent duplicate merge workflow with probabilistic matching
- **SELF-03**: Multi-language support (5-10 languages)
- **SELF-04**: Biometric patient identification (if hardware available)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Authentication and user login | Separate Auth Module handles user authentication and role management |
| Appointment scheduling | Separate Appointment Module handles scheduling workflow |
| Medical records and clinical notes | Belongs in EMR Module - patient module is demographics only |
| Prescriptions and medications | Belongs in EMR/Pharmacy Module |
| Lab results and imaging | Belongs in Laboratory/Radiology Module |
| Billing and claims | Separate Billing Module handles financial operations |
| Patient portal | Separate Patient Portal Module handles patient-facing features |
| Document management | Separate Document Module handles file uploads |
| Notifications and reminders | Separate Notification Module handles communications |
| Reporting and analytics | Separate Reporting Module handles business intelligence |
| H2 database for testing | Research shows H2 doesn't match PostgreSQL behavior - use Testcontainers |
| JWT in LocalStorage | XSS vulnerability - use HttpOnly cookies per research |
| Microservices architecture for v1 | Research recommends modular monolith for 50K patients, 100 concurrent users |
| Field-level encryption on searchable fields | Breaks indexing and kills search performance per research |
| Page-based pagination | Research shows Slice-based pagination required to avoid expensive COUNT queries |
| Inline editing without review | Causes data integrity issues per research anti-patterns |
| Anonymous patient registration | Impossible to bill, cannot coordinate care, creates legal liability |
| Real-time insurance verification on every search | API rate limits, slows search 2-5 seconds unnecessarily |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| SEC-01 | Phase 0 | Complete |
| SEC-02 | Phase 0 | Complete |
| SEC-03 | Phase 0 | Complete |
| SEC-04 | Phase 0 | Complete |
| SEC-05 | Phase 0 | Complete |
| SEC-06 | Phase 0 | Complete |
| SEC-07 | Phase 0 | Complete |
| SEC-08 | Phase 0 | Complete |
| SEC-09 | Phase 0 | Complete |
| REG-01 | Phase 1 | Complete |
| REG-02 | Phase 1 | Complete |
| REG-03 | Phase 1 | Complete |
| REG-04 | Phase 1 | Complete |
| REG-05 | Phase 1 | Complete |
| REG-06 | Phase 1 | Complete |
| REG-07 | Phase 1 | Complete |
| REG-08 | Phase 1 | Complete |
| REG-09 | Phase 1 | Complete |
| REG-10 | Phase 1 | Complete |
| REG-11 | Phase 1 | Complete |
| REG-12 | Phase 1 | Complete |
| SRCH-01 | Phase 1 | Complete |
| SRCH-02 | Phase 1 | Complete |
| SRCH-03 | Phase 1 | Complete |
| SRCH-04 | Phase 1 | Complete |
| SRCH-05 | Phase 1 | Complete |
| SRCH-06 | Phase 1 | Complete |
| SRCH-07 | Phase 1 | Complete |
| SRCH-08 | Phase 1 | Complete |
| SRCH-09 | Phase 1 | Complete |
| SRCH-10 | Phase 1 | Complete |
| PROF-01 | Phase 1 | Complete |
| PROF-02 | Phase 1 | Complete |
| PROF-03 | Phase 1 | Complete |
| PROF-04 | Phase 1 | Complete |
| PROF-05 | Phase 1 | Complete |
| PROF-06 | Phase 1 | Complete |
| PROF-07 | Phase 1 | Complete |
| PROF-08 | Phase 1 | Complete |
| PROF-09 | Phase 1 | Complete |
| UPD-01 | Phase 2 | Complete |
| UPD-02 | Phase 2 | Complete |
| UPD-03 | Phase 2 | Complete |
| UPD-04 | Phase 2 | Complete |
| UPD-05 | Phase 2 | Complete |
| UPD-06 | Phase 2 | Complete |
| UPD-07 | Phase 2 | Complete |
| UPD-08 | Phase 2 | Complete |
| UPD-09 | Phase 2 | Complete |
| UPD-10 | Phase 2 | Complete |
| STAT-01 | Phase 2 | Complete |
| STAT-02 | Phase 2 | Complete |
| STAT-03 | Phase 2 | Complete |
| STAT-04 | Phase 2 | Complete |
| STAT-05 | Phase 2 | Complete |
| STAT-06 | Phase 2 | Complete |
| STAT-07 | Phase 2 | Complete |
| STAT-08 | Phase 2 | Complete |
| INS-01 | Phase 2 | Complete |
| INS-02 | Phase 2 | Complete |
| INS-03 | Phase 2 | Complete |
| INS-04 | Phase 2 | Pending |
| INS-05 | Phase 2 | Pending |
| EMR-01 | Phase 2 | Complete |
| EMR-02 | Phase 2 | Complete |
| EMR-03 | Phase 2 | Complete |
| EMR-04 | Phase 2 | Complete |

**Coverage:**
- v1 requirements: 50 total
- Mapped to phases: 50
- Unmapped: 0

**Note:** Phases 3 and 4 deliver v2 requirements (OPS-01 through OPS-04, ADV-01 through ADV-05) brought forward based on operational and competitive value identified during research.

---
*Requirements defined: 2026-02-19*
*Last updated: 2026-02-19 after roadmap creation with complete phase mappings*
