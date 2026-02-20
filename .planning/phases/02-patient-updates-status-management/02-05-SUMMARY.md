---
phase: 02-patient-updates-status-management
plan: 05
subsystem: testing
tags: [spring-boot, mockMvc, integration-test, event-sourcing, patient, security, insurance, emergency-contacts]

# Dependency graph
requires:
  - phase: 02-patient-updates-status-management
    provides: Plan 01 - Insurance schema, PatientUpdatedEvent pipeline
  - phase: 02-patient-updates-status-management
    provides: Plan 02 - UpdatePatientRequest, UpdateStatusRequest, InsuranceDto, EmergencyContactDto DTOs
  - phase: 02-patient-updates-status-management
    provides: Plan 03 - PUT /patients/{businessId} + PATCH /patients/{businessId}/status with PatientUpdatedEvent
  - phase: 02-patient-updates-status-management
    provides: Plan 04 - GET/POST/PUT /patients/{businessId}/insurance, PatientDetailResponse.insuranceInfo

provides:
  - Phase02VerificationTest.java with 16 integration tests covering all 6 Phase 2 success criteria
  - Verified: PUT demographics inserts new version, preserves registeredAt, increments version
  - Verified: PATCH /status idempotent on same-status; ADMIN-only enforced (RECEPTIONIST gets 403)
  - Verified: POST/PUT/GET insurance with encrypted PHI; 400 on invalid policyNumber
  - Verified: POST/DELETE/GET emergency contacts; cross-patient ownership check returns 403
  - Verified: PatientUpdatedEvent published and captured via @RecordApplicationEvents
  - V005 migration: fixes idx_patients_unique_identity constraint incompatible with event-sourcing

affects:
  - phase-03 (next phase can proceed — Phase 2 evidence gate passed)
  - any future test patterns referencing Phase02VerificationTest

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@RecordApplicationEvents + ApplicationEvents injection for integration-test event verification"
    - "registerTestPatient() helper with phone-number override for cross-patient ownership tests"
    - "@DirtiesContext(AFTER_CLASS) + @BeforeEach deleteAll() for test isolation with shared Spring context"
    - "Unique per-test phone numbers prevent duplicate detection triggering in same-name cross-patient tests"

key-files:
  created:
    - src/test/java/com/hospital/patient/Phase02VerificationTest.java
    - src/main/resources/db/migration/V005__fix_unique_identity_constraint.sql
  modified:
    - src/main/java/com/hospital/patient/application/EmergencyContactService.java

key-decisions:
  - "V005 drops idx_patients_unique_identity: constraint incorrectly blocked re-activating patients because event-sourced rows retain original ACTIVE status — duplicate detection handled at application layer by DuplicateDetectionService"
  - "EmergencyContactService.addContact() required createdBy from SecurityContext — NOT NULL constraint was missing in standalone endpoint path (only set in PatientService.registerPatient)"
  - "@RecordApplicationEvents used for SC6 event verification — captures Spring ApplicationEventPublisher events within test scope without requiring AsyncTaskExecutor coordination"

patterns-established:
  - "Phone number uniqueness per test: when registering multiple patients in same test, use overloaded registerTestPatient(firstName, lastName, phoneNumber) to prevent duplicate detection 409"
  - "Event-sourced constraint fix: unique constraints with WHERE status='ACTIVE' are incompatible with INSERT-only models that retain all version rows"

requirements-completed: [UPD-01, UPD-02, UPD-03, UPD-04, UPD-05, UPD-06, UPD-07, UPD-09, UPD-10, STAT-01, STAT-02, STAT-03, STAT-04, STAT-05, STAT-06, STAT-07, STAT-08, INS-01, INS-02, INS-03, INS-04, INS-05, EMR-01, EMR-02, EMR-03, EMR-04, UPD-08]

# Metrics
duration: 9min
completed: 2026-02-20
---

# Phase 2 Plan 05: Phase 2 Verification Tests Summary

**16-test MockMvc integration suite proving all 6 Phase 2 success criteria: demographics update, read-only field preservation, admin status management, encrypted insurance CRUD, emergency contact ownership enforcement, and PatientUpdatedEvent event publishing**

## Performance

- **Duration:** 9 min
- **Started:** 2026-02-20T07:01:47Z
- **Completed:** 2026-02-20T07:11:15Z
- **Tasks:** 1
- **Files modified:** 3 (1 created test, 1 new migration, 1 service fix)

## Accomplishments

- Created `Phase02VerificationTest.java` with 16 integration tests covering all 6 Phase 2 success criteria across demographics, status, insurance, and emergency contact domains
- Verified event-sourcing invariants: `registeredAt` unchanged after update, version increments, `photoIdVerified` preserved
- Verified role-based access control: DOCTOR gets 403 on PUT, RECEPTIONIST gets 403 on PATCH /status
- Verified ownership enforcement: cross-patient emergency contact PUT returns 403
- Verified `@RecordApplicationEvents` captures `PatientUpdatedEvent` with correct businessId and changedFields
- Fixed two bugs found during test execution (Rule 1 auto-fixes)

## Task Commits

Each task was committed atomically:

1. **Task 1: Write Phase02VerificationTest covering all success criteria** - `67953fe` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/test/java/com/hospital/patient/Phase02VerificationTest.java` - 16 integration tests covering SC1-SC6 Phase 2 success criteria using MockMvc, @WithMockUser, @RecordApplicationEvents
- `src/main/resources/db/migration/V005__fix_unique_identity_constraint.sql` - Drops idx_patients_unique_identity that was incompatible with event-sourced INSERT pattern for status re-activation
- `src/main/java/com/hospital/patient/application/EmergencyContactService.java` - Added getCurrentUsername() and set createdBy on contact builder in addContact()

## Decisions Made

- `@RecordApplicationEvents` (Spring 5.3.3+ / Boot 2.5+) chosen for SC6 event verification — cleaner than a test ApplicationListener bean, works within the same transaction scope
- `@DirtiesContext(AFTER_CLASS)` retained matching Phase01VerificationTest pattern — context shared within the test class, full DB reset via `@BeforeEach deleteAll()`
- Unique phone numbers per registerTestPatient call when registering multiple patients in one test — avoids triggering DuplicateDetectionService which uses phone number as matching signal

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] V005 migration drops idx_patients_unique_identity constraint**
- **Found during:** Task 1 — test execution (sc3_activatePatient_success failing with 409)
- **Issue:** `idx_patients_unique_identity` is a unique index on `(lower(first_name), lower(last_name), date_of_birth, normalized_phone) WHERE status = 'ACTIVE'`. Event-sourced rows retain their original status value; when re-activating a patient, the INSERT of a new ACTIVE version row with same demographics conflicts with the old version-1 ACTIVE row. The constraint was designed for registration deduplication but breaks the event-sourced update model.
- **Fix:** V005 migration drops the constraint. Application-layer duplicate detection (DuplicateDetectionService) already handles registration deduplication at the controller level.
- **Files modified:** `src/main/resources/db/migration/V005__fix_unique_identity_constraint.sql` (new)
- **Verification:** `sc3_activatePatient_success` passes; all deactivate/reactivate tests pass
- **Committed in:** `67953fe` (Task 1 commit)

**2. [Rule 1 - Bug] EmergencyContactService.addContact() missing createdBy field**
- **Found during:** Task 1 — test execution (sc5_addEmergencyContact_success failing with 409 DataIntegrityViolationException)
- **Issue:** `EmergencyContact.createdBy` is `NOT NULL` in the database. `EmergencyContactService.addContact()` built the entity without calling `.createdBy()` in the builder. Only `PatientService.registerPatient()` (which also creates contacts) set `createdBy`. The standalone POST /emergency-contacts endpoint path was missing this.
- **Fix:** Added `getCurrentUsername()` method to EmergencyContactService (same pattern as PatientService) and added `.createdBy(getCurrentUsername())` to the contact builder.
- **Files modified:** `src/main/java/com/hospital/patient/application/EmergencyContactService.java`
- **Verification:** `sc5_addEmergencyContact_success`, `sc5_crossPatientContactUpdate_returns403`, `sc5_deleteEmergencyContact_success` all pass
- **Committed in:** `67953fe` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (2 bugs — constraint design incompatible with event-sourcing, missing required field in service)
**Impact on plan:** Both bugs would have blocked Phase 2 endpoints from working in production. Critical correctness fixes with no scope creep.

## Issues Encountered

- Phone number validator (`PhoneNumberValidator`) accepts `+1-xxx-xxx-xxxx`, `(xxx) xxx-xxxx`, or `xxx-xxx-xxxx` formats only. The test plan used `+12345678901` (no hyphens) which is invalid. Fixed by using `555-123-4567` format throughout.
- Cross-patient test needed unique phone numbers for patient A and B to avoid duplicate detection triggering on same last name + same phone.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 2 complete: all 16 verification tests passing (BUILD SUCCESS)
- All 28 Phase 2 requirements verified: UPD-01..10, STAT-01..08, INS-01..05, EMR-01..04, UPD-08
- V005 migration corrects the unique identity constraint for production correctness
- EmergencyContactService.addContact() now correctly sets createdBy for audit compliance
- Phase 3 can proceed: Patient Appointment & Scheduling

## Self-Check: PASSED

- `src/test/java/com/hospital/patient/Phase02VerificationTest.java`: FOUND
- `src/main/resources/db/migration/V005__fix_unique_identity_constraint.sql`: FOUND
- `src/main/java/com/hospital/patient/application/EmergencyContactService.java`: FOUND (modified)
- Commit 67953fe: FOUND (feat(02-05): add Phase02VerificationTest)

---
*Phase: 02-patient-updates-status-management*
*Completed: 2026-02-20*
