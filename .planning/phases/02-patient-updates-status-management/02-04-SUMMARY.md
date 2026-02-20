---
phase: 02-patient-updates-status-management
plan: 04
subsystem: api
tags: [insurance, jpa, spring-security, rest, encryption, audit]

# Dependency graph
requires:
  - phase: 02-01
    provides: Insurance entity, InsuranceRepository, CoverageType enum
  - phase: 02-02
    provides: InsuranceDto with validation constraints
  - phase: 02-03
    provides: PatientService with updatePatient/changePatientStatus, PatientDetailResponse
provides:
  - InsuranceService with createInsurance, updateInsurance, getActiveInsurance
  - InsuranceController with GET/POST/PUT /api/v1/patients/{businessId}/insurance
  - PatientDetailResponse extended with insuranceInfo field
  - PatientService wired to InsuranceService for patient profile GET
  - GlobalExceptionHandler handling EntityNotFoundException with 404
affects:
  - 02-05
  - phase-03

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Single-active-record pattern for insurance (deactivate old, insert new on POST)
    - Optional-based null-safe insurance lookup in patient profile aggregation

key-files:
  created:
    - src/main/java/com/hospital/patient/application/InsuranceService.java
    - src/main/java/com/hospital/patient/api/InsuranceController.java
  modified:
    - src/main/java/com/hospital/patient/api/dto/PatientDetailResponse.java
    - src/main/java/com/hospital/patient/application/PatientService.java
    - src/main/java/com/hospital/shared/exception/GlobalExceptionHandler.java

key-decisions:
  - "POST /insurance deactivates existing active record and inserts new one (single-active-record semantics, preserves history)"
  - "PUT /insurance modifies active record in-place (mutable insurance table, updatedAt/updatedBy via AuditingEntityListener)"
  - "getActiveInsurance() returns Optional<InsuranceDto> so patient profile GET sets insuranceInfo=null gracefully (not 404)"
  - "EntityNotFoundException handler added to GlobalExceptionHandler returning RFC 7807 404 for PUT with no existing insurance"

patterns-established:
  - "Single-active-record insurance: POST deactivates existing + inserts new; PUT mutates existing"
  - "Patient profile aggregation: toDetailResponse() orchestrates entity lookups (contacts + history + insurance) and maps to DTO"
  - "Optional.orElse(null) for nullable embedded DTOs in response"

requirements-completed: [INS-01, INS-02, INS-03, INS-04, INS-05]

# Metrics
duration: 2min
completed: 2026-02-20
---

# Phase 2 Plan 04: Insurance CRUD API Summary

**Insurance GET/POST/PUT API with single-active-record semantics, SensitiveDataConverter encryption, and patient profile integration returning insuranceInfo**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-20T07:35:14Z
- **Completed:** 2026-02-20T07:37:14Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- InsuranceService with upsert semantics: POST deactivates existing active insurance and creates new; PUT modifies active record in-place
- InsuranceController with 3 endpoints: GET (200/404), POST (201), PUT (200) with @PreAuthorize and @Audited
- PatientDetailResponse extended with insuranceInfo field, PatientService wired to InsuranceService so GET /patients/{id} returns insurance data
- GlobalExceptionHandler extended with EntityNotFoundException handler returning RFC 7807 404

## Task Commits

Each task was committed atomically:

1. **Task 1: InsuranceService + InsuranceController** - `0d27c19` (feat)
2. **Task 2: Extend PatientDetailResponse + wire InsuranceService** - `6708c8d` (feat)

## Files Created/Modified

- `src/main/java/com/hospital/patient/application/InsuranceService.java` - Service with createInsurance(), updateInsurance(), getActiveInsurance(); single-active-record semantics
- `src/main/java/com/hospital/patient/api/InsuranceController.java` - REST controller mapping GET/POST/PUT /api/v1/patients/{businessId}/insurance
- `src/main/java/com/hospital/patient/api/dto/PatientDetailResponse.java` - Added private InsuranceDto insuranceInfo field
- `src/main/java/com/hospital/patient/application/PatientService.java` - Added @Autowired InsuranceService; toDetailResponse() now fetches and includes insuranceDto
- `src/main/java/com/hospital/shared/exception/GlobalExceptionHandler.java` - Added EntityNotFoundException handler returning 404 RFC 7807

## Decisions Made

- POST creates with single-active-record semantics: deactivates existing active insurance before inserting new, preserving history in inactive records
- PUT modifies the active record in-place; updatedAt/updatedBy populated automatically by @LastModifiedDate/@LastModifiedBy via AuditingEntityListener
- getActiveInsurance() uses Optional so patient profile GET returns insuranceInfo=null for patients without insurance (not a 404 on the patient)
- EntityNotFoundException handler added to GlobalExceptionHandler so PUT on a patient with no insurance returns 404 with RFC 7807 body instead of 500

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Insurance API complete: POST/PUT/GET /api/v1/patients/{businessId}/insurance operational
- Patient profile GET (/api/v1/patients/{businessId}) now returns insuranceInfo embedded in response
- Phase 2 Plan 05 (integration tests or remaining plans) can proceed
- All INS-01 through INS-05 requirements fulfilled

## Self-Check: PASSED

- InsuranceService.java: FOUND
- InsuranceController.java: FOUND
- 02-04-SUMMARY.md: FOUND
- Commit 0d27c19: FOUND
- Commit 6708c8d: FOUND

---
*Phase: 02-patient-updates-status-management*
*Completed: 2026-02-20*
