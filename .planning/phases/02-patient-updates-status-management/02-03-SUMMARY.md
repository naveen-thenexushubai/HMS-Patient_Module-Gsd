---
phase: 02-patient-updates-status-management
plan: 03
subsystem: api
tags: [spring-boot, jpa, event-sourcing, patient, rest-api, security]

# Dependency graph
requires:
  - phase: 02-patient-updates-status-management
    provides: Plan 01 - PatientRepository extensions (findMaxVersionByBusinessId, findFirstVersionByBusinessId), PatientUpdatedEvent
  - phase: 02-patient-updates-status-management
    provides: Plan 02 - UpdatePatientRequest, UpdateStatusRequest DTOs

provides:
  - PatientService.updatePatient() - inserts new patient version row preserving photoIdVerified/status
  - PatientService.changePatientStatus() - inserts new version row with only status changed (idempotent)
  - PUT /api/v1/patients/{businessId} - demographics update endpoint with RECEPTIONIST/ADMIN write permission
  - PATCH /api/v1/patients/{businessId}/status - ADMIN-only status change endpoint
  - toDetailResponse() updated to use findFirstVersionByBusinessId for correct registeredAt/registeredBy
  - PatientUpdatedEvent published after each successful version insert

affects:
  - 02-04-insurance-api
  - 02-05-phase-verification
  - future audit/history views

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Event-sourced INSERT pattern: every patient change inserts a new version row (trigger prevents UPDATE)
    - Idempotent status change: if already in target status, return current state without inserting new version
    - AFTER_COMMIT event delivery: publishEvent called inside @Transactional, Spring delivers after commit
    - Version-1 for registration metadata: findFirstVersionByBusinessId gives original registeredAt/registeredBy
    - Object-level permission delegation: hasPermission(#businessId, 'Patient', 'write') via PatientPermissionEvaluator

key-files:
  created: []
  modified:
    - src/main/java/com/hospital/patient/application/PatientService.java
    - src/main/java/com/hospital/patient/api/PatientController.java

key-decisions:
  - "toDetailResponse() now uses findFirstVersionByBusinessId for registeredAt/registeredBy so multi-version patients show correct registration date"
  - "changePatientStatus() is idempotent: same-status request returns current state without inserting new version"
  - "PUT endpoint uses hasPermission(#businessId, 'Patient', 'write') delegating to PatientPermissionEvaluator (RECEPTIONIST + ADMIN get write); PATCH /status adds hasRole('ADMIN') making it ADMIN-only"
  - "EmergencyContactDto mapping now includes id field so clients can construct PUT/DELETE URLs for contacts"

patterns-established:
  - "New patient version build: always use Patient.builder() with photoIdVerified and status copied from current — never modify existing entity"
  - "Event publishing: publishEvent inside @Transactional method; @TransactionalEventListener(AFTER_COMMIT) ensures post-commit delivery"

requirements-completed: [UPD-01, UPD-02, UPD-03, UPD-04, UPD-05, UPD-06, UPD-07, UPD-08, UPD-09, UPD-10, STAT-01, STAT-02, STAT-03, STAT-04, STAT-05, STAT-06, STAT-07, STAT-08]

# Metrics
duration: 2min
completed: 2026-02-20
---

# Phase 2 Plan 03: Patient Update Service Summary

**Event-sourced PUT and PATCH /status endpoints inserting new patient version rows with role-based write permissions and PatientUpdatedEvent publishing**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-20T06:49:11Z
- **Completed:** 2026-02-20T06:51:30Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Implemented `updatePatient()` in PatientService using event-sourced INSERT pattern: inserts new version row with incremented version, preserving `photoIdVerified` and `status` from current version
- Implemented `changePatientStatus()` with idempotency check: returns current state if already in target status; otherwise inserts new version with only status changed
- Extended `toDetailResponse()` to fetch version-1 via `findFirstVersionByBusinessId` for correct `registeredAt`/`registeredBy` on multi-version patients
- Added PUT `/{businessId}` endpoint with `hasPermission(#businessId, 'Patient', 'write')` (RECEPTIONIST + ADMIN write access)
- Added PATCH `/{businessId}/status` endpoint with `hasRole('ADMIN') and hasPermission(#businessId, 'Patient', 'write')` (ADMIN-only)
- Both endpoints annotated with `@Audited(action = "UPDATE", resourceType = "PATIENT")` for PHI audit logging
- Both endpoints publish `PatientUpdatedEvent` after successful version insert (delivered AFTER_COMMIT)

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend PatientService with updatePatient() and changePatientStatus()** - `5f0f9c8` (feat)
2. **Task 2: Add PUT /{businessId} and PATCH /{businessId}/status to PatientController** - `f4cf107` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/java/com/hospital/patient/application/PatientService.java` - Extended with updatePatient(), changePatientStatus(), updated toDetailResponse() for version-1 metadata, added ApplicationEventPublisher
- `src/main/java/com/hospital/patient/api/PatientController.java` - Extended with PUT /{businessId} and PATCH /{businessId}/status endpoints with correct @PreAuthorize and @Audited annotations

## Decisions Made

- `toDetailResponse()` updated to use `findFirstVersionByBusinessId` so multi-version patients correctly show original `registeredAt`/`registeredBy`, while `lastModifiedAt`/`lastModifiedBy` come from the latest version
- `changePatientStatus()` is idempotent: same-status requests return current state without inserting a new version row (avoids unnecessary version bloat)
- PATCH /status endpoint requires both `hasRole('ADMIN')` AND `hasPermission(#businessId, 'Patient', 'write')` — double guard ensures ADMIN-only access regardless of PatientPermissionEvaluator behavior

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] EmergencyContactDto mapping now includes id field**
- **Found during:** Task 1 (PatientService toDetailResponse update)
- **Issue:** Existing toDetailResponse() mapped EmergencyContactDto without the `id` field, which was added to the DTO in Plan 02-02. Without `id`, clients cannot construct PUT/DELETE URLs for emergency contact management.
- **Fix:** Added `.id(ec.getId())` to the EmergencyContactDto builder in the toDetailResponse() mapping
- **Files modified:** src/main/java/com/hospital/patient/application/PatientService.java
- **Verification:** Compile succeeded with the fix
- **Committed in:** `5f0f9c8` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Essential fix — without the `id` field, emergency contact CRUD from Phase 2 Plan 02 would be incomplete. No scope creep.

## Issues Encountered

- `./mvnw` wrapper was not present in project root (Maven Wrapper not initialized). Used `mvn compile` directly instead. All compilation checks passed with exit 0.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- PUT /api/v1/patients/{businessId} and PATCH /api/v1/patients/{businessId}/status are fully implemented
- PatientUpdatedEvent pipeline ready for downstream consumers
- Ready for Phase 2 Plan 04: Insurance API (InsuranceService + controller)
- No blockers

---
*Phase: 02-patient-updates-status-management*
*Completed: 2026-02-20*
