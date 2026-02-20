---
phase: 03-operational-enhancements
plan: "04"
subsystem: api
tags: [spring-boot, jpa, native-sql, postgresql, security, admin]

# Dependency graph
requires:
  - phase: 03-01
    provides: V006 patient_photos table and V007 is_registration_complete column added to patients
  - phase: 02-01
    provides: insurance table with is_active column
  - phase: 01-01
    provides: patients table with patients_latest view and photo_id_verified column
provides:
  - GET /api/v1/admin/data-quality endpoint (ADMIN-only) returning DataQualityReport JSON
  - DataQualityReport DTO with 6 fields: totalActivePatients, incompleteRegistrations, missingInsurance, missingPhoto, unverifiedPhotoIds, generatedAt
  - DataQualityRepository with 5 nativeQuery=true COUNT queries against patients_latest view
  - DataQualityService orchestrating all 5 queries in a read-only transaction
affects: [admin-ui, reporting, phase-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "nativeQuery=true for PostgreSQL view queries (JPQL cannot target views — would silently query underlying entity table)"
    - "NOT EXISTS subquery pattern for absence checks (countMissingInsurance, countMissingPhotos)"
    - "Admin-only endpoint pattern: @RequestMapping('/api/v1/admin') + @PreAuthorize('hasRole(ADMIN)')"
    - "Read-only transaction for aggregate reporting (@Transactional(readOnly = true))"

key-files:
  created:
    - src/main/java/com/hospital/patient/api/dto/DataQualityReport.java
    - src/main/java/com/hospital/patient/infrastructure/DataQualityRepository.java
    - src/main/java/com/hospital/patient/application/DataQualityService.java
    - src/main/java/com/hospital/patient/api/DataQualityController.java
  modified: []

key-decisions:
  - "nativeQuery=true required for all queries against patients_latest view — JPQL resolves @Entity class (patients table) not the view, returning stale multi-version data"
  - "DataQualityRepository extends JpaRepository<Patient, String> as placeholder — JPA requires a managed entity type, but all methods use native SQL that bypasses JPA entity mapping"
  - "Admin-only endpoint at /api/v1/admin (not /api/v1/patients) — follows admin endpoint convention, aggregate quality data not exposed to clinical staff"
  - "NOT EXISTS preferred over LEFT JOIN for absence checks — more efficient for checking non-existence in indexed subquery tables"

patterns-established:
  - "Native SQL view query pattern: extend JpaRepository<SomeEntity, SomeId>, use @Query(nativeQuery=true) to target views"
  - "Admin aggregate endpoint: /api/v1/admin prefix + @PreAuthorize(hasRole('ADMIN')) + @Audited"

requirements-completed: []

# Metrics
duration: 2min
completed: 2026-02-20
---

# Phase 3 Plan 04: Data Quality Dashboard Summary

**Admin-only GET /api/v1/admin/data-quality endpoint returning real-time DataQualityReport with 5 native SQL COUNT queries against the patients_latest PostgreSQL view**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-20T08:24:26Z
- **Completed:** 2026-02-20T08:26:46Z
- **Tasks:** 1
- **Files modified:** 4

## Accomplishments
- DataQualityReport DTO with totalActivePatients, incompleteRegistrations, missingInsurance, missingPhoto, unverifiedPhotoIds, generatedAt fields
- DataQualityRepository with 5 native SQL COUNT queries targeting the patients_latest view (bypassing JPQL entity resolution which cannot target views)
- DataQualityService builds real-time report in a single read-only transaction (no caching — reflects live database state)
- DataQualityController exposes GET /api/v1/admin/data-quality with ADMIN-only access via @PreAuthorize and audit logging via @Audited

## Task Commits

Each task was committed atomically:

1. **Task 1: DataQualityReport DTO + DataQualityRepository + DataQualityService + DataQualityController** - `0f4c30f` (feat — committed as part of 03-05 bundle)

**Plan metadata:** (committed with SUMMARY.md)

## Files Created/Modified
- `src/main/java/com/hospital/patient/api/dto/DataQualityReport.java` - Response DTO with 6 fields for quality metrics
- `src/main/java/com/hospital/patient/infrastructure/DataQualityRepository.java` - JPA repository with 5 nativeQuery=true COUNT methods
- `src/main/java/com/hospital/patient/application/DataQualityService.java` - Service building DataQualityReport in @Transactional(readOnly=true)
- `src/main/java/com/hospital/patient/api/DataQualityController.java` - GET /api/v1/admin/data-quality endpoint ADMIN-only

## Decisions Made
- nativeQuery=true required for all queries: JPQL resolves @Entity (patients table) not the patients_latest view, silently returning wrong multi-version counts
- DataQualityRepository extends JpaRepository<Patient, String> as JPA placeholder — all query methods use native SQL that completely bypasses JPA entity mapping
- NOT EXISTS subquery pattern chosen over LEFT JOIN IS NULL for countMissingInsurance and countMissingPhotos — more efficient for indexed absence checks
- Admin endpoint at /api/v1/admin prefix (not /api/v1/patients) — aggregate quality data is administrative, not clinical

## Deviations from Plan

None - plan executed exactly as written. The implementation files were already present in the repository (committed in 03-05 bundle commit 0f4c30f). All files match the plan specification exactly and compilation succeeds.

## Issues Encountered
- The four DataQuality files (DataQualityReport, DataQualityRepository, DataQualityService, DataQualityController) were already committed in commit 0f4c30f (the 03-05 SmartFormController commit) by a prior execution. Files were confirmed present, correct, and compiling successfully — no re-implementation required.

## Next Phase Readiness
- Data quality dashboard API complete — ADMIN users can query /api/v1/admin/data-quality for real-time quality metrics
- incompleteRegistrations count reflects quick-registered patients from plan 03-02
- missingPhoto count uses patient_photos table from V006 migration (plan 03-01)
- is_registration_complete column from V007 migration (plan 03-01) drives incompleteRegistrations count
- Ready for Phase 3 Plans 05-06 (smart form and remaining enhancements)

---
*Phase: 03-operational-enhancements*
*Completed: 2026-02-20*
