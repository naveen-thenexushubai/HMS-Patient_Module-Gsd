---
phase: 03-operational-enhancements
plan: "02"
subsystem: api
tags: [quick-registration, patient-registration, duplicate-detection, event-sourced, spring-boot]

# Dependency graph
requires:
  - phase: 03-01
    provides: V007 migration (is_registration_complete column), Patient entity base, FileStorageService, CacheConfig
  - phase: 01-patient-registration-search
    provides: DuplicateDetectionService, PatientService.registerPatient(), PatientController patterns
  - phase: 02-patient-updates-status-management
    provides: UpdatePatientRequest, PatientService.updatePatient(), event-sourced version insert pattern
provides:
  - POST /api/v1/patients/quick endpoint for walk-in patient registration with 5 required fields
  - QuickRegisterRequest DTO (no photoIdVerified constraint)
  - QuickRegistrationService with DuplicateDetectionService integration
  - QuickRegistrationController with RECEPTIONIST/ADMIN authorization
  - isRegistrationComplete Boolean field on Patient entity (DB column is_registration_complete)
  - isRegistrationComplete in PatientDetailResponse (visible in all GET /patients responses)
  - isRegistrationComplete in UpdatePatientRequest (allows completion via PUT)
  - PatientService.toDetailResponse() extended to include isRegistrationComplete
  - PatientService.changePatientStatus() preserves isRegistrationComplete across status changes
affects: [03-03-photo-upload, 03-04-smart-forms, 03-05-smart-form-api, 04-reporting]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dual-controller same base path: QuickRegistrationController and PatientController both map to /api/v1/patients; Spring MVC disambiguates by sub-path (/quick)"
    - "Quick registration service delegates to PatientService.getPatientByBusinessId() for response building to avoid duplicating toDetailResponse() logic"
    - "isRegistrationComplete preserved across event-sourced version insertions (updatePatient, changePatientStatus) with null-safe default true for pre-Phase-3 rows"

key-files:
  created:
    - src/main/java/com/hospital/patient/api/dto/QuickRegisterRequest.java
    - src/main/java/com/hospital/patient/application/QuickRegistrationService.java
    - src/main/java/com/hospital/patient/api/QuickRegistrationController.java
  modified:
    - src/main/java/com/hospital/patient/domain/Patient.java
    - src/main/java/com/hospital/patient/api/dto/PatientDetailResponse.java
    - src/main/java/com/hospital/patient/api/dto/UpdatePatientRequest.java
    - src/main/java/com/hospital/patient/application/PatientService.java

key-decisions:
  - "QuickRegistrationService delegates to PatientService.getPatientByBusinessId() after save — avoids duplicating toDetailResponse() logic and ensures consistent response structure including insurance and registeredAt"
  - "Boolean (wrapper) not boolean (primitive) for isRegistrationComplete in Patient entity — allows null for pre-Phase-3 rows loaded before column existed; null-safe default true in @PrePersist and update methods"
  - "QuickRegistrationController uses same /api/v1/patients base path as PatientController — Spring MVC disambiguates via /quick sub-path; both controllers coexist without conflict"
  - "PatientService.changePatientStatus() and updatePatient() both null-guard isRegistrationComplete with default true for backward compatibility with pre-Phase-3 patient rows"

patterns-established:
  - "Quick registration pattern: minimal DTO + dedicated service + dedicated controller; full registration service pattern unchanged"
  - "isRegistrationComplete preservation: any builder-based event-sourced insert must explicitly carry this field (set to true or preserve from current)"

requirements-completed: []

# Metrics
duration: 8min
completed: 2026-02-20
---

# Phase 3 Plan 02: Quick Registration Summary

**POST /api/v1/patients/quick endpoint with QuickRegisterRequest DTO (5 required fields, no photo ID constraint), QuickRegistrationService running duplicate detection, and isRegistrationComplete field propagated through Patient entity, PatientDetailResponse, and UpdatePatientRequest**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-20T08:24:24Z
- **Completed:** 2026-02-20T08:32:00Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- Walk-in patient quick registration via POST /api/v1/patients/quick with only 5 required fields (firstName, lastName, dateOfBirth, gender, phoneNumber)
- QuickRegistrationService runs full DuplicateDetectionService check — same 85%/90% thresholds as full registration
- Patient entity extended with isRegistrationComplete Boolean field (V007 migration column already existed from Plan 03-01)
- Full registrations (POST /api/v1/patients) default isRegistrationComplete=true; quick registrations set false
- PUT /api/v1/patients/{businessId} can now set isRegistrationComplete=true to complete a quick-registered patient
- GET /api/v1/patients/{businessId} response includes isRegistrationComplete field

## Task Commits

Each task was committed atomically:

1. **Task 1: Patient entity isRegistrationComplete field + PatientDetailResponse + UpdatePatientRequest extensions + QuickRegisterRequest DTO** - `1b5dc35` (feat)
2. **Task 2: QuickRegistrationService + QuickRegistrationController + PatientService updatePatient() extension** - `382e149` (feat)

## Files Created/Modified

- `src/main/java/com/hospital/patient/api/dto/QuickRegisterRequest.java` - Minimal registration DTO: firstName, lastName, dateOfBirth, gender, phoneNumber required; no @AssertTrue photoIdVerified
- `src/main/java/com/hospital/patient/application/QuickRegistrationService.java` - Service: builds Patient with isRegistrationComplete=false, runs DuplicateDetectionService, delegates response to PatientService.getPatientByBusinessId()
- `src/main/java/com/hospital/patient/api/QuickRegistrationController.java` - POST /api/v1/patients/quick controller with RECEPTIONIST/ADMIN auth and duplicate detection handling
- `src/main/java/com/hospital/patient/domain/Patient.java` - Added isRegistrationComplete Boolean field with @Builder.Default=true, @Column(is_registration_complete), null guard in @PrePersist
- `src/main/java/com/hospital/patient/api/dto/PatientDetailResponse.java` - Added Boolean isRegistrationComplete field
- `src/main/java/com/hospital/patient/api/dto/UpdatePatientRequest.java` - Added optional Boolean isRegistrationComplete (null=preserve current)
- `src/main/java/com/hospital/patient/application/PatientService.java` - Extended registerPatient() (set true), updatePatient() (carry from request or preserve), changePatientStatus() (preserve), toDetailResponse() (include in response)

## Decisions Made

- QuickRegistrationService delegates to PatientService.getPatientByBusinessId() after save to avoid duplicating toDetailResponse() logic. This reuses the same insurance lookup, emergency contact query, and registeredAt-from-version-1 logic.
- Boolean (wrapper) not boolean (primitive) for isRegistrationComplete — pre-Phase-3 rows have no value in this column; null-safe default true applied in @PrePersist and service-layer null guards.
- Both QuickRegistrationController and PatientController map to /api/v1/patients base path — Spring MVC disambiguates via the /quick sub-path. No conflict.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Both tasks compiled cleanly on first attempt.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Quick registration endpoint is complete and ready for use
- isRegistrationComplete field available in all patient responses for downstream systems
- Completion workflow: receptionist can PUT /api/v1/patients/{businessId} with isRegistrationComplete=true to mark complete (new version row inserted)
- Photo upload plan (03-03) can now associate photos with quick-registered patients via business_id
- V007 migration (is_registration_complete column) was already applied by Plan 03-01

---
*Phase: 03-operational-enhancements*
*Completed: 2026-02-20*
