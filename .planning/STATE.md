# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-19)

**Core value:** Centralized, secure, and efficient patient information management that serves as the foundation for all other hospital modules
**Current focus:** Phase 3 - Patient Appointment & Scheduling

## Current Position

Phase: 2 of 4 (Patient Updates & Status Management)
Plan: 5 of 5 in current phase
Status: Complete
Last activity: 2026-02-20 — Completed Plan 02-05: Phase 2 Verification Tests (16 integration tests, all Phase 2 criteria verified)

Progress: [████████████████████████████████████████] 80%

## Performance Metrics

**Velocity:**
- Total plans completed: 13
- Average duration: 18 minutes
- Total execution time: 3.78 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 00 - Security & Compliance Foundation | 6 | 68 min | 11 min |
| 01 - Patient Registration & Search | 5 | 155 min | 31 min |
| 02 - Patient Updates & Status Management | 5 | 22 min | 4 min |

**Recent Trend:**
- Last 5 plans: 114 min, 15 min, 6 min, 2 min, 9 min
- Trend: Phase 2 complete in 22 min total — well-established patterns, fast execution

*Updated after each plan completion*
| Phase 01 P01 | 9 | 2 tasks | 14 files |
| Phase 01 P02 | 15 | 2 tasks | 15 files |
| Phase 01 P03 | 114 | 2 tasks | 6 files |
| Phase 01-patient-registration-search P04 | 5 | 2 tasks | 5 files |
| Phase 01-patient-registration-search P05 | 12 | 2 tasks | 5 files |
| Phase 01-patient-registration-search P06 | 4 | 2 tasks | 2 files |
| Phase 01-patient-registration-search P07 | 8 | 2 tasks | 4 files |
| Phase 02-patient-updates-status-management P01 | 6 | 2 tasks | 9 files |
| Phase 02-patient-updates-status-management P02 | 3 | 2 tasks | 8 files |
| Phase 02-patient-updates-status-management P03 | 2 | 2 tasks | 2 files |
| Phase 02-patient-updates-status-management P04 | 2 | 2 tasks | 5 files |
| Phase 02-patient-updates-status-management P05 | 9 | 1 tasks | 3 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Initialization: Spring Boot 3.4.5+ required due to CVE-2025-22235 (upgrades constraint from 3.2.x)
- Initialization: Modular monolith architecture for v1 (microservices deferred based on research)
- Initialization: HIPAA compliance prioritized in Phase 0 before any patient data
- Plan 00-01: Environment variables chosen for Phase 0 secrets management (Vault/Secrets Manager in Phase 1)
- Plan 00-01: Profile-based configuration (dev/prod) for environment-specific settings
- Plan 00-03: PostgreSQL port 5435 to avoid conflicts with existing containers
- Plan 00-03: pgAudit extension deferred - application-level logging satisfies HIPAA (database-level audit requires custom Docker image)
- Plan 00-03: Hibernate @JdbcTypeCode chosen for PostgreSQL-specific types (inet, jsonb)
- Plan 00-03: Annual table partitioning for 6-year HIPAA retention requirement
- Plan 00-02: JJWT 0.13.0 with Keys.hmacShaKeyFor() for type-safe JWT key management
- Plan 00-02: AES/GCM/NoPadding for authenticated encryption (prevents tampering)
- Plan 00-02: IV stored with ciphertext (no separate column needed)
- Plan 00-04: Placeholder authorization logic for Phase 0 (actual rules refined in Phase 1 when PatientRepository exists)
- Plan 00-04: Optional TlsConfig with @Profile("prod") for flexible deployment (load balancer vs application-level TLS)
- Plan 00-04: TLS 1.3 preferred with TLS 1.2 fallback for client compatibility
- [Phase 00]: Verification checkpoint completed with 2 gaps identified: AuditInterceptorTest cleanup and details field population
- Plan 00-06: @DirtiesContext chosen over transaction rollback for test cleanup (cleaner, respects database state)
- Plan 00-06: Accept Hibernate 6.6 + PostgreSQL JSONB limitation for Phase 0 (core audit fields work, details supplementary)
- [Phase 01]: Remove FK constraints for event-sourced pattern (business_id not unique across versions)
- [Phase 01]: Defer Hibernate Search to Phase 3 due to String ID compatibility issues (Plan 01-01)
- [Phase 01]: Custom IdentifierGenerator implementation for P2026XXXXX format with String IDs
- Plan 01-03: Use JPQL LIKE queries instead of Hibernate Search for Phase 1 search - indexing not working in @DataJpaTest context despite 7 fix attempts
- Plan 01-03: JPQL search acceptable for Phase 1 scale (<10K patients, <100ms queries)
- [Phase 01-04]: DOCTOR read-only in Phase 1 (Phase 2 will add patient_assignments table check)
- [Phase 01-04]: PatientPermissionEvaluator first overload defaults to Patient type (evaluator is Patient-scoped)
- [Phase 01-04]: NURSE read-only in Phase 1 (Phase 2 will add care_team table check)
- [Phase 01-05]: RFC 7807 ProblemDetail via Spring Framework 6 native support; GlobalExceptionHandler in shared.exception package; blocking duplicates (>=90%) return 403 even with overrideDuplicate=true
- [Phase 01-patient-registration-search]: Plan 01-06: maxEditDistance=2 for Levenshtein fuzzy search (1-char edit catches Jon/John and Smyth/Smith; in-memory pass safe for Phase 1 <10K scale)
- [Phase 01-07]: @AssertTrue on Boolean (not boolean primitive) for photoIdVerified so @NotNull catches null and @AssertTrue catches false separately
- [Phase 01-07]: REG-12 satisfied for Phase 1 via API flag enforcement only; no file upload/storage - scan/upload UI is Phase 3 concern
- [Phase 02-01]: VARCHAR(512) for encrypted PHI columns (policy_number, group_number) — accommodates AES-256-GCM ciphertext + 12-byte IV + base64 overhead
- [Phase 02-01]: No @Index on encrypted insurance columns — SensitiveDataConverter uses random IV per encryption, ciphertext is non-deterministic
- [Phase 02-01]: No FK from insurance to patients — event-sourced pattern: business_id repeats across versions
- [Phase 02-01]: @TransactionalEventListener(AFTER_COMMIT) over @EventListener — guarantees new patient row committed before listener fires
- [Phase 02-01]: @EnableAsync added to HospitalApplication for non-blocking @Async listener execution
- [Phase 02-02]: EmergencyContactDto gets id field (response-only) so clients can construct PUT/DELETE URLs after creation
- [Phase 02-02]: Cross-patient ownership check in service layer (contact.patientBusinessId == URL businessId) for defense-in-depth
- [Phase 02-02]: InsuranceDto policyNumber pattern ^[A-Za-z0-9\-]{3,50}$ per INS-02 requirement
- [Phase 02-03]: toDetailResponse() uses findFirstVersionByBusinessId for registeredAt/registeredBy — multi-version patients show correct registration date
- [Phase 02-03]: changePatientStatus() is idempotent — same-status requests return current state without inserting new version
- [Phase 02-03]: PATCH /status endpoint requires hasRole('ADMIN') AND hasPermission write — ADMIN-only access enforced at two levels
- [Phase 02-04]: POST /insurance deactivates existing active record and inserts new (single-active-record semantics); PUT modifies active record in-place; EntityNotFoundException handler added to GlobalExceptionHandler for 404 on missing active insurance
- [Phase 02-patient-updates-status-management]: V005 drops idx_patients_unique_identity: constraint incompatible with event-sourced INSERT pattern for status re-activation; duplicate detection handled at application layer
- [Phase 02-patient-updates-status-management]: EmergencyContactService.addContact() required createdBy from SecurityContext — NOT NULL constraint fix in standalone endpoint path

### Pending Todos

None yet.

### Blockers/Concerns

**Phase 2 Complete:**
- Phase 0 complete: 26/26 security tests passing ✅
- Phase 1 complete (all 7 plans including gap closures) ✅
- Phase 2 Plan 01 complete: Insurance schema, event pipeline, 409 handler ✅
- Phase 2 Plan 02 complete: UpdatePatientRequest, UpdateStatusRequest, InsuranceDto DTOs + EmergencyContact CRUD API ✅
- Phase 2 Plan 03 complete: PUT /patients/{businessId} + PATCH /patients/{businessId}/status with PatientUpdatedEvent ✅
- Phase 2 Plan 04 complete: Insurance CRUD API (GET/POST/PUT /api/v1/patients/{businessId}/insurance), PatientDetailResponse with insuranceInfo ✅
- Phase 2 Plan 05 complete: 16 integration tests - all Phase 2 success criteria verified ✅
- Next: Phase 3 - Patient Appointment & Scheduling

## Session Continuity

Last session: 2026-02-20
Stopped at: Completed 02-05-PLAN.md (Phase 2 Verification Tests — 16 integration tests, Phase 2 COMPLETE)
Resume file: .planning/phases/02-patient-updates-status-management/02-05-SUMMARY.md
Next action: Execute Phase 3 Plan 01
