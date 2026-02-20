---
phase: 02-patient-updates-status-management
plan: 01
subsystem: database
tags: [postgres, flyway, jpa, encryption, spring-events, aes-256-gcm]

# Dependency graph
requires:
  - phase: 01-patient-registration-search
    provides: Patient JPA entity, PatientRepository base, SensitiveDataConverter, GlobalExceptionHandler
  - phase: 00-security-compliance-foundation
    provides: SensitiveDataConverter (AES-256-GCM field encryption), AuditingEntityListener

provides:
  - V003 Flyway migration: insurance table with PHI-encrypted columns (policy_number, group_number VARCHAR 512)
  - CoverageType enum: HMO, PPO, EPO, POS, HDHP, MEDICAID, MEDICARE, OTHER
  - Insurance JPA entity with @Convert(SensitiveDataConverter) on policyNumber and groupNumber
  - InsuranceRepository: findByPatientBusinessId, findByPatientBusinessIdAndIsActiveTrue
  - PatientRepository: findMaxVersionByBusinessId (JPQL MAX), findFirstVersionByBusinessId (native SQL)
  - PatientUpdatedEvent + PatientUpdatedEventListener (@Async + @TransactionalEventListener AFTER_COMMIT)
  - GlobalExceptionHandler: DataIntegrityViolationException handler returning RFC 7807 409 with uk_patients_business_version detection

affects:
  - 02-02 (patient update DTOs and emergency contacts — needs InsuranceRepository)
  - 02-03 (patient update service — needs findMaxVersionByBusinessId and PatientUpdatedEvent)
  - 02-04 (patient status management — needs event pipeline and 409 handler)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@Convert(SensitiveDataConverter) on PHI fields — VARCHAR(512) for AES-256-GCM + IV + base64 overhead"
    - "No FK from insurance to patients (event-sourced: business_id non-unique across versions)"
    - "PatientUpdatedEvent delivered AFTER_COMMIT via @TransactionalEventListener to ensure row is readable"
    - "@EnableAsync on HospitalApplication enables non-blocking @Async listener execution"
    - "DataIntegrityViolationException handler checks message string for constraint name before returning 409"

key-files:
  created:
    - src/main/resources/db/migration/V003__create_insurance_schema.sql
    - src/main/java/com/hospital/patient/domain/CoverageType.java
    - src/main/java/com/hospital/patient/domain/Insurance.java
    - src/main/java/com/hospital/patient/infrastructure/InsuranceRepository.java
    - src/main/java/com/hospital/events/PatientUpdatedEvent.java
    - src/main/java/com/hospital/events/PatientUpdatedEventListener.java
  modified:
    - src/main/java/com/hospital/patient/infrastructure/PatientRepository.java
    - src/main/java/com/hospital/HospitalApplication.java
    - src/main/java/com/hospital/shared/exception/GlobalExceptionHandler.java

key-decisions:
  - "insurance.policy_number and group_number use VARCHAR(512) to fit AES-256-GCM ciphertext + 12-byte IV + base64 overhead without column expansion"
  - "No @Index on encrypted columns — SensitiveDataConverter uses random IV per encryption, ciphertext is non-deterministic"
  - "No FK from insurance to patients — event-sourced pattern: business_id repeats across versions"
  - "@TransactionalEventListener(AFTER_COMMIT) chosen over @EventListener to guarantee new patient row is committed before listener fires"
  - "@EnableAsync added to HospitalApplication to support non-blocking @Async listener execution"

patterns-established:
  - "Mutable child tables (insurance) use all four JPA auditing fields: @CreatedDate, @CreatedBy, @LastModifiedDate, @LastModifiedBy"
  - "Immutable parent table (patients) uses only @CreatedDate, @CreatedBy"

requirements-completed: [INS-01, INS-02, INS-03, UPD-07, UPD-10, STAT-05]

# Metrics
duration: 6min
completed: 2026-02-20
---

# Phase 2 Plan 01: Infrastructure Foundation Summary

**Insurance table DDL with AES-256-GCM PHI encryption, PatientRepository version queries, AFTER_COMMIT event pipeline, and concurrent-update 409 handler**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-20T06:31:55Z
- **Completed:** 2026-02-20T06:37:49Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- V003 Flyway migration applied successfully: insurance table with PHI-safe VARCHAR(512) columns for policy_number and group_number
- Insurance JPA entity with @Convert(SensitiveDataConverter) on both PHI fields; CoverageType enum covers 8 plan types
- PatientRepository extended with findMaxVersionByBusinessId (JPQL) and findFirstVersionByBusinessId (native SQL) for version-based updates
- PatientUpdatedEvent + PatientUpdatedEventListener wired for AFTER_COMMIT delivery — new patient row guaranteed readable before listener fires
- GlobalExceptionHandler handles DataIntegrityViolationException with uk_patients_business_version detection returning RFC 7807 409 Conflict

## Task Commits

Each task was committed atomically:

1. **Task 1: V003 Insurance Migration + Insurance JPA Entity + CoverageType Enum + InsuranceRepository** - `7b749fe` (feat)
2. **Task 2: PatientRepository Extensions + PatientUpdatedEvent Pipeline + DataIntegrityViolation 409 Handler** - `7618af1` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `src/main/resources/db/migration/V003__create_insurance_schema.sql` - Insurance table DDL with PHI column comments and composite index on (patient_business_id, is_active)
- `src/main/java/com/hospital/patient/domain/CoverageType.java` - Enum: HMO, PPO, EPO, POS, HDHP, MEDICAID, MEDICARE, OTHER
- `src/main/java/com/hospital/patient/domain/Insurance.java` - JPA entity with SensitiveDataConverter on policyNumber and groupNumber; Spring Data JPA auditing
- `src/main/java/com/hospital/patient/infrastructure/InsuranceRepository.java` - findByPatientBusinessId and findByPatientBusinessIdAndIsActiveTrue
- `src/main/java/com/hospital/events/PatientUpdatedEvent.java` - Domain event POJO: source, businessId, newVersion, updatedBy, occurredAt, changedFields
- `src/main/java/com/hospital/events/PatientUpdatedEventListener.java` - @Async + @TransactionalEventListener(AFTER_COMMIT) log-based listener
- `src/main/java/com/hospital/patient/infrastructure/PatientRepository.java` - Added findMaxVersionByBusinessId and findFirstVersionByBusinessId
- `src/main/java/com/hospital/HospitalApplication.java` - Added @EnableAsync
- `src/main/java/com/hospital/shared/exception/GlobalExceptionHandler.java` - Added DataIntegrityViolationException handler with 409 RFC 7807 response

## Decisions Made
- VARCHAR(512) for encrypted PHI columns — accommodates AES-256-GCM ciphertext + 12-byte IV + base64 padding without column expansion
- No @Index on policy_number or group_number — SensitiveDataConverter uses random IV, so indexes on non-deterministic ciphertext are useless
- No FK from insurance to patients — event-sourced pattern means business_id is non-unique across patient versions
- @TransactionalEventListener(AFTER_COMMIT) not @EventListener — guarantees new patient version row is committed before listener fires
- @EnableAsync on HospitalApplication (not a separate config class) — minimal change, same effect

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed wildcard import ambiguity for @Id in Insurance.java**
- **Found during:** Task 1 (Insurance JPA entity compilation)
- **Issue:** Plan template used `import org.springframework.data.annotation.*;` which imports `org.springframework.data.annotation.Id` — ambiguous with `jakarta.persistence.Id` when both are on classpath
- **Fix:** Replaced wildcard `org.springframework.data.annotation.*` import with explicit imports for `@CreatedDate`, `@CreatedBy`, `@LastModifiedDate`, `@LastModifiedBy`; kept explicit `jakarta.persistence.Id`
- **Files modified:** `src/main/java/com/hospital/patient/domain/Insurance.java`
- **Verification:** `mvn compile` exits 0 after fix
- **Committed in:** `7b749fe` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - compile-time ambiguity bug)
**Impact on plan:** Required for correctness. No scope creep.

## Issues Encountered
- BMAD project's `hospital-postgres` container name conflicted with Hospital_Gsd project's docker-compose container name. Started a separate `hospital-gsd-postgres` container on port 5435 to verify Flyway migrations.
- Port 8080 occupied by BMAD container prevented full Spring Boot startup, but Flyway ran successfully before Tomcat started — all 3 migrations applied and verified in DB logs.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- InsuranceRepository ready for use by Plan 02 (patient update DTOs and emergency contact management)
- findMaxVersionByBusinessId ready for use by Plan 03 (patient update service to compute next version)
- PatientUpdatedEvent ready to be published by Plan 03 after each successful patient version insert
- 409 Concurrent Update Conflict handler active for Plan 03 race condition protection

---
*Phase: 02-patient-updates-status-management*
*Completed: 2026-02-20*
