---
phase: 02-patient-updates-status-management
plan: 02
subsystem: api
tags: [spring-boot, jpa, dto, emergency-contacts, hipaa-audit, flyway, spring-security]

# Dependency graph
requires:
  - phase: 02-01
    provides: CoverageType enum needed by InsuranceDto to compile

provides:
  - UpdatePatientRequest DTO (demographics-only PUT body, excludes read-only fields)
  - UpdateStatusRequest DTO (single PatientStatus field for PATCH /status)
  - InsuranceDto (request+response dual-purpose DTO with policyNumber @Pattern validation)
  - EmergencyContactService (add/update/delete/list with ownership verification)
  - EmergencyContactController (POST/GET/PUT/DELETE under /api/v1/patients/{businessId}/emergency-contacts)
  - V004 migration (updated_at/updated_by columns on emergency_contacts)
  - EmergencyContact audit fields (updatedAt/updatedBy via Spring Data JPA auditing)

affects:
  - 02-03 (patient update service depends on UpdatePatientRequest and UpdateStatusRequest DTOs)
  - 02-04 (insurance service depends on InsuranceDto)
  - 02-05 (integration tests will exercise EmergencyContactController endpoints)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Ownership verification before mutating child resources (contact.getPatientBusinessId().equals(businessId))
    - Object-level security with hasPermission(#businessId, 'Patient', 'write') on mutating endpoints
    - @EntityListeners(AuditingEntityListener) for Spring Data JPA audit field population
    - Dual-purpose DTO pattern (same class for request and response, response-only fields null on input)

key-files:
  created:
    - src/main/java/com/hospital/patient/api/dto/UpdatePatientRequest.java
    - src/main/java/com/hospital/patient/api/dto/UpdateStatusRequest.java
    - src/main/java/com/hospital/patient/api/dto/InsuranceDto.java
    - src/main/java/com/hospital/patient/application/EmergencyContactService.java
    - src/main/java/com/hospital/patient/api/EmergencyContactController.java
    - src/main/resources/db/migration/V004__add_emergency_contact_audit_fields.sql
  modified:
    - src/main/java/com/hospital/patient/domain/EmergencyContact.java
    - src/main/java/com/hospital/patient/api/dto/EmergencyContactDto.java

key-decisions:
  - "EmergencyContactDto gets an id field (response-only) so clients can reference contacts in subsequent PUT/DELETE calls"
  - "Cross-patient ownership check is mandatory in service layer not controller — defense-in-depth against URL manipulation"
  - "InsuranceDto policyNumber @Pattern uses 3-50 chars alphanumeric+hyphen as specified (INS-02)"

patterns-established:
  - "Child resource ownership verified by comparing child.patientBusinessId to URL businessId before any mutation"
  - "All state-changing endpoints annotated with @Audited for HIPAA audit trail"

requirements-completed: [UPD-01, UPD-02, UPD-03, UPD-04, UPD-05, UPD-08, UPD-09, EMR-01, EMR-02, EMR-03, EMR-04, INS-02, STAT-01, STAT-02]

# Metrics
duration: 3min
completed: 2026-02-20
---

# Phase 2 Plan 02: Patient Update DTOs and Emergency Contact CRUD Summary

**Three request/response DTOs plus full Emergency Contact CRUD API (POST/GET/PUT/DELETE) with object-level security and ownership verification preventing cross-patient modification**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-20T06:42:36Z
- **Completed:** 2026-02-20T06:45:40Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- Created UpdatePatientRequest (demographics only, excludes status/patientId/photoIdVerified/registeredAt per UPD-03/04)
- Created UpdateStatusRequest (single @NotNull PatientStatus for STAT-01/02 dedicated status endpoint)
- Created InsuranceDto with @Pattern on policyNumber (3-50 alphanumeric+hyphens, INS-02) as dual-purpose request+response DTO
- Implemented EmergencyContactService with ownership check (contact.patientBusinessId == URL businessId) on update and delete
- Implemented EmergencyContactController with POST (201), GET (200), PUT (200), DELETE (204) all under /api/v1/patients/{businessId}/emergency-contacts
- Added updatedAt/updatedBy audit fields to EmergencyContact via @EntityListeners(AuditingEntityListener) (EMR-04)
- V004 migration adds updated_at/updated_by columns to emergency_contacts table

## Task Commits

Each task was committed atomically:

1. **Task 1: Create UpdatePatientRequest, UpdateStatusRequest, and InsuranceDto DTOs** - `368452e` (feat)
2. **Task 2: EmergencyContact Update Fields + V004 Migration + EmergencyContactService + EmergencyContactController** - `c91b667` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `src/main/java/com/hospital/patient/api/dto/UpdatePatientRequest.java` - Demographics-only PUT request body; omits photoIdVerified, status, patientId, registeredAt
- `src/main/java/com/hospital/patient/api/dto/UpdateStatusRequest.java` - Single @NotNull PatientStatus field for PATCH /status endpoint
- `src/main/java/com/hospital/patient/api/dto/InsuranceDto.java` - Dual-purpose request/response DTO with @Pattern policyNumber validation
- `src/main/java/com/hospital/patient/application/EmergencyContactService.java` - add/update/delete/list with patient-existence verification and ownership check
- `src/main/java/com/hospital/patient/api/EmergencyContactController.java` - 4 endpoints with hasPermission object-level security and @Audited on all endpoints
- `src/main/resources/db/migration/V004__add_emergency_contact_audit_fields.sql` - ALTER TABLE emergency_contacts ADD COLUMN updated_at, updated_by
- `src/main/java/com/hospital/patient/domain/EmergencyContact.java` - Added @EntityListeners, @LastModifiedDate updatedAt, @LastModifiedBy updatedBy
- `src/main/java/com/hospital/patient/api/dto/EmergencyContactDto.java` - Added id field for response (needed for PUT/DELETE URL references)

## Decisions Made
- Added `id` field to EmergencyContactDto (response-only, null on request) so clients get back the contactId to use in subsequent PUT/DELETE calls
- Ownership check placed in service layer (not controller) for defense-in-depth — the check is closer to data access
- InsuranceDto policyNumber uses `^[A-Za-z0-9\-]{3,50}$` pattern as specified in INS-02 to allow alphanumeric policy numbers with hyphens

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added id field to EmergencyContactDto**
- **Found during:** Task 2 (EmergencyContactService implementation)
- **Issue:** EmergencyContactDto had no id field; toDto() mapping would silently drop the database ID; clients could not construct PUT/DELETE URLs without it
- **Fix:** Added `private Long id;` field to EmergencyContactDto with response-only comment
- **Files modified:** src/main/java/com/hospital/patient/api/dto/EmergencyContactDto.java
- **Verification:** Compile succeeds; toDto() in service maps contact.getId() to dto.id
- **Committed in:** c91b667 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** The plan's task description noted "check if absent, add id field" — this was explicitly called out as needed. No scope creep.

## Issues Encountered
None - compilation succeeded on first attempt for both tasks.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plans 03 and 04 can now proceed: UpdatePatientRequest and UpdateStatusRequest exist for patient update service; InsuranceDto exists for insurance service
- EmergencyContact CRUD is a complete vertical slice ready for integration testing in Plan 05
- EmergencyContactRepository's findByPatientBusinessIdOrderByIsPrimaryDesc already existed from Phase 1 — no repository changes needed

## Self-Check: PASSED

All 6 created/modified source files verified present on disk.
Both task commits (368452e, c91b667) verified in git log.

---
*Phase: 02-patient-updates-status-management*
*Completed: 2026-02-20*
