# Roadmap: Hospital Management System - Patient Module

## Overview

This roadmap delivers a HIPAA-compliant patient management system serving as the foundation for 40+ hospital modules. Starting with security infrastructure (Phase 0), we build core registration and search capabilities (Phase 1), add patient updates and status management (Phase 2), enhance with operational efficiency features (Phase 3), and finish with advanced capabilities like family linking and FHIR integration (Phase 4). Every phase prioritizes security, audit compliance, and data accuracy critical to healthcare operations.

## Phases

**Phase Numbering:**
- Integer phases (0, 1, 2, 3, 4): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [▶] **Phase 0: Security & Compliance Foundation** - HIPAA infrastructure and audit logging before any patient data (1/5 complete)
- [x] **Phase 1: Patient Registration & Search** - Core demographic registration with duplicate detection and search (completed 2026-02-20)
- [ ] **Phase 2: Patient Updates & Status Management** - Profile updates, status changes, insurance, emergency contacts
- [ ] **Phase 3: Operational Enhancements** - Quick registration, photo capture, data quality dashboard
- [ ] **Phase 4: Advanced Features & Integration** - Family linking, QR codes, phonetic search, FHIR adapter

## Phase Details

### Phase 0: Security & Compliance Foundation
**Goal**: HIPAA-compliant infrastructure ready for patient data with audit logging, encryption, and access control
**Depends on**: Nothing (first phase)
**Requirements**: SEC-01, SEC-02, SEC-03, SEC-04, SEC-05, SEC-06, SEC-07, SEC-08, SEC-09
**Success Criteria** (what must be TRUE):
  1. System has completed and documented HIPAA Security Risk Assessment covering all PHI storage and transmission paths
  2. All API endpoints validate JWT tokens and enforce role-based access control (Receptionist, Doctor, Nurse, Admin)
  3. Patient data is encrypted at rest (PostgreSQL encryption) and in transit (TLS 1.3)
  4. All patient data access is logged to append-only storage with user ID, timestamp, action, resource, and IP address
  5. No secrets are hardcoded in code; all credentials stored in environment variables or secrets manager
**Plans**: 5 plans in 4 waves

Plans:
- [x] 00-01-PLAN.md — Spring Boot 3.4.5+ project foundation with HIPAA Security Risk Assessment (✓ 2/2 tasks, 7 min)
- [x] 00-02-PLAN.md — JWT authentication with role-based access control and field-level encryption (✓ 2/2 tasks, 9 min)
- [x] 00-03-PLAN.md — PostgreSQL with pgAudit extension and audit logging infrastructure (✓ 2/2 tasks, 12 min)
- [x] 00-04-PLAN.md — Object-level authorization with PermissionEvaluator and TLS 1.3 configuration (✓ 2/2 tasks, 6 min)
- [x] 00-05-PLAN.md — Security verification checkpoint (✓ 1/1 verification, gaps identified)

### Phase 1: Patient Registration & Search
**Goal**: Staff can register new patients with complete demographics, search existing patients efficiently, and view patient profiles with duplicate prevention
**Depends on**: Phase 0
**Requirements**: REG-01, REG-02, REG-03, REG-04, REG-05, REG-06, REG-07, REG-08, REG-09, REG-10, REG-11, REG-12, SRCH-01, SRCH-02, SRCH-03, SRCH-04, SRCH-05, SRCH-06, SRCH-07, SRCH-08, SRCH-09, SRCH-10, PROF-01, PROF-02, PROF-03, PROF-04, PROF-05, PROF-06, PROF-07, PROF-08, PROF-09
**Success Criteria** (what must be TRUE):
  1. Receptionist can register new patient with all required fields and system generates unique Patient ID (format: P2026001)
  2. System warns about potential duplicate patients (fuzzy matching on name, DOB, phone) but allows registration to proceed
  3. Staff can search patients by ID, name, phone, or email and see results within 2 seconds for 10,000 patient records
  4. Staff can filter patient list by status (Active/Inactive), gender, and blood group with paginated results (20 per page)
  5. Staff can view complete patient profile including demographics, emergency contacts, medical info, and registration audit trail
  6. Edit Patient button appears only for Receptionist/Admin users (hidden from Doctor/Nurse)
**Plans**: 5 plans in 4 waves

Plans:
- [ ] 01-01-PLAN.md — Database schema with event-sourced Patient entity, custom P2026XXX ID generator, and normalized emergency contacts/medical history
- [ ] 01-02-PLAN.md — Patient registration API with multi-field fuzzy duplicate detection (Levenshtein + Soundex) and phone/email validation
- [ ] 01-03-PLAN.md — Hibernate Search with Lucene backend for full-text patient search with fuzzy matching and Slice-based pagination
- [ ] 01-04-PLAN.md — Patient profile view with refined PatientPermissionEvaluator (role-based edit permissions) and related data repositories
- [ ] 01-05-PLAN.md — RFC 7807 Problem Details error handling and Phase 1 verification tests (all 6 success criteria)

### Phase 2: Patient Updates & Status Management
**Goal**: Staff can update patient information, manage patient status (active/inactive), and maintain insurance and emergency contact records with full audit trail
**Depends on**: Phase 1
**Requirements**: UPD-01, UPD-02, UPD-03, UPD-04, UPD-05, UPD-06, UPD-07, UPD-08, UPD-09, UPD-10, STAT-01, STAT-02, STAT-03, STAT-04, STAT-05, STAT-06, STAT-07, STAT-08, INS-01, INS-02, INS-03, INS-04, INS-05, EMR-01, EMR-02, EMR-03, EMR-04
**Success Criteria** (what must be TRUE):
  1. Receptionist/Admin can update patient demographics via pre-populated form with same validation as registration
  2. Patient ID and registration date remain read-only; all updates recorded with timestamp and user who made change
  3. Admin can activate/deactivate patients with confirmation dialog; status changes visible in filtered views
  4. Receptionist can capture and update insurance information (provider, policy number, group number, coverage type)
  5. Receptionist can add, view, update, and remove multiple emergency contacts with phone validation and audit trail
  6. System publishes PatientUpdated event to message broker after successful update for downstream module consumption
**Plans**: TBD

Plans:
- [ ] 02-01: TBD during planning

### Phase 3: Operational Enhancements
**Goal**: Improve registration efficiency with quick registration, patient photo capture, data quality dashboard, and smart forms
**Depends on**: Phase 1
**Requirements**: None (v2 features brought forward based on operational value)
**Success Criteria** (what must be TRUE):
  1. Receptionist can register walk-in patients with minimal required fields and "complete later" workflow flag
  2. Receptionist can capture patient photo at registration desk via webcam integration
  3. Receptionist can view data quality dashboard showing incomplete records, missing insurance, and pending ID verification
  4. Registration forms auto-complete ZIP code to city/state and suggest insurance plans to reduce data entry time
**Plans**: TBD

Plans:
- [ ] 03-01: TBD during planning

### Phase 4: Advanced Features & Integration
**Goal**: Enable family linking, QR code patient lookup, phonetic search, relationship management, and optional FHIR integration for external EHR systems
**Depends on**: Phase 1, Phase 2, Phase 3
**Requirements**: None (v2 features brought forward based on competitive value)
**Success Criteria** (what must be TRUE):
  1. Staff can link patients into families/households with shared demographic data
  2. Staff can generate patient QR codes and scan QR codes for instant patient lookup
  3. Staff can search patients using phonetic matching (Soundex/Metaphone) to handle spelling variations
  4. Staff can define patient relationships (parent-child, spouse, guardian) and guarantor information
  5. System exposes FHIR R4 Patient resource endpoint for external EHR integration (if required)
**Plans**: TBD

Plans:
- [ ] 04-01: TBD during planning

## Progress

**Execution Order:**
Phases execute in numeric order: 0 → 1 → 2 → 3 → 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 0. Security & Compliance Foundation | 6/6 | Complete   | 2026-02-19 |
| 1. Patient Registration & Search | 5/5 | Complete   | 2026-02-20 |
| 2. Patient Updates & Status Management | 0/? | Not started | - |
| 3. Operational Enhancements | 0/? | Not started | - |
| 4. Advanced Features & Integration | 0/? | Not started | - |
