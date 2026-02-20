---
phase: 01-patient-registration-search
plan: 07
subsystem: api
tags: [spring-boot, bean-validation, javax-validation, jakarta-validation, AssertTrue, NotNull, registration, photo-id]

# Dependency graph
requires:
  - phase: 01-patient-registration-search
    provides: RegisterPatientRequest DTO, PatientService, Patient entity with photo_id_verified column, Phase01VerificationTest framework
provides:
  - REG-12 gap closure: photoIdVerified=true required for patient registration at API level
  - Bean Validation enforcement: @NotNull + @AssertTrue on Boolean photoIdVerified field
  - RFC 7807 error response for photoIdVerified validation failures with fieldErrors.photoIdVerified
  - 3 new REG-12 integration tests (400 on false, 400 on null, 201 on true)
affects: [02-patient-management, any phase using patient registration API]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@AssertTrue on Boolean (boxed) field pattern for enforcing mandatory true values without custom validators"
    - "Layered validation: @NotNull catches null, @AssertTrue catches false, ensuring only true passes"

key-files:
  created: []
  modified:
    - src/main/java/com/hospital/patient/api/dto/RegisterPatientRequest.java
    - src/main/java/com/hospital/patient/application/PatientService.java
    - src/test/java/com/hospital/patient/integration/Phase01VerificationTest.java
    - src/test/java/com/hospital/patient/infrastructure/PatientSearchRepositoryTest.java

key-decisions:
  - "@AssertTrue on Boolean (not boolean primitive) used for photoIdVerified so @NotNull can catch null separately"
  - "No file upload/storage logic added - REG-12 satisfied for Phase 1 via API flag enforcement only; UI scan/upload is Phase 3 concern"

patterns-established:
  - "@AssertTrue + @NotNull layering pattern for mandatory boolean enforcement in DTOs"

requirements-completed: [REG-12]

# Metrics
duration: 8min
completed: 2026-02-20
---

# Phase 01 Plan 07: REG-12 Gap Closure Summary

**photoIdVerified=true enforced at registration via @NotNull + @AssertTrue Bean Validation with RFC 7807 fieldErrors response and 11 passing integration tests**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-20T05:25:10Z
- **Completed:** 2026-02-20T05:33:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Added `Boolean photoIdVerified` field with `@NotNull` and `@AssertTrue` to `RegisterPatientRequest` - null returns 400 with `fieldErrors.photoIdVerified`, false returns 400, true passes validation
- Updated `PatientService.registerPatient()` to pass `request.getPhotoIdVerified()` to `Patient.builder().photoIdVerified()`
- Added 3 new REG-12 integration tests to `Phase01VerificationTest`: 400 on false, 201 on true, 400 on null/missing field
- Full test suite: 11 Phase01VerificationTest + 13 PatientSearchRepositoryTest = 24 passing

## Task Commits

Each task was committed atomically:

1. **Task 1: Add photoIdVerified field with enforcement to RegisterPatientRequest and PatientService** - `09b72ae` (feat)
2. **Task 2: Add REG-12 enforcement integration tests** - `e7a1403` (test)
3. **Deviation fix: PatientSearchRepositoryTest test isolation** - `274154c` (fix)

**Plan metadata:** (pending docs commit)

## Files Created/Modified

- `src/main/java/com/hospital/patient/api/dto/RegisterPatientRequest.java` - Added `@NotNull @AssertTrue Boolean photoIdVerified` field after `zipCode`
- `src/main/java/com/hospital/patient/application/PatientService.java` - Added `.photoIdVerified(request.getPhotoIdVerified())` to Patient builder chain
- `src/test/java/com/hospital/patient/integration/Phase01VerificationTest.java` - Added `.photoIdVerified(true)` to all existing builder usages + 3 new REG-12 tests
- `src/test/java/com/hospital/patient/infrastructure/PatientSearchRepositoryTest.java` - Added `patientRepository.deleteAll()` to setUp() (deviation fix)

## Decisions Made

- `@AssertTrue` works on `Boolean` (boxed) not `boolean` (primitive) - using boxed type so `@NotNull` can catch null before `@AssertTrue` evaluates to false
- No file upload or multipart changes - REG-12 satisfied for Phase 1 with API flag enforcement only; scan/upload UI is Phase 3 concern
- `GlobalExceptionHandler.handleMethodArgumentNotValid()` already produces RFC 7807 format with `fieldErrors` map - no changes needed to error handling

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed PatientSearchRepositoryTest missing deleteAll() causing cross-test DB contamination**
- **Found during:** Overall verification step (combined test run)
- **Issue:** `PatientSearchRepositoryTest.setUp()` seeded 5 patients without first clearing the DB. When run after `Phase01VerificationTest` (which seeds up to 10,000 patients then uses `@DirtiesContext` - which reloads context but NOT the real PostgreSQL DB), residual records caused `emptySearch_returnsAllPatients_sorted` to find 7 patients instead of expected 5
- **Fix:** Added `patientRepository.deleteAll()` as first line of `setUp()` in `PatientSearchRepositoryTest`
- **Files modified:** `src/test/java/com/hospital/patient/infrastructure/PatientSearchRepositoryTest.java`
- **Verification:** Combined run of `Phase01VerificationTest,PatientSearchRepositoryTest` now returns 24 tests, 0 failures
- **Committed in:** `274154c`

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug)
**Impact on plan:** Pre-existing test isolation bug surfaced during combined verification run. Fix is minimal (1 line added). No scope creep.

## Issues Encountered

- Docker PostgreSQL container was running without host port mapping (5432 only, no 5435 mapping). Required stopping/removing the old container and starting via docker-compose to get the correct `0.0.0.0:5435->5432/tcp` binding.

## Next Phase Readiness

- REG-12 is fully closed: `POST /api/v1/patients` with `photoIdVerified: false` or null returns 400 with `fieldErrors.photoIdVerified`; with `true` returns 201
- `photo_id_verified` column is set to `true` in DB for successfully registered patients
- Phase 1 gap closures complete (SRCH-04 via plan 06, REG-12 via plan 07)
- Ready to begin Phase 2 (Patient Management: edit, status changes)

---
*Phase: 01-patient-registration-search*
*Completed: 2026-02-20*

## Self-Check: PASSED

All claimed artifacts verified:
- FOUND: RegisterPatientRequest.java (contains photoIdVerified field with @NotNull @AssertTrue)
- FOUND: PatientService.java (contains .photoIdVerified(request.getPhotoIdVerified()))
- FOUND: Phase01VerificationTest.java (contains 3 reg12_ test methods)
- FOUND: PatientSearchRepositoryTest.java (contains deleteAll() fix)
- FOUND commit: 09b72ae (feat: photoIdVerified field + PatientService + existing test updates)
- FOUND commit: e7a1403 (test: 3 new REG-12 integration tests)
- FOUND commit: 274154c (fix: PatientSearchRepositoryTest test isolation)
