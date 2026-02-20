# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-19)

**Core value:** Centralized, secure, and efficient patient information management that serves as the foundation for all other hospital modules
**Current focus:** Phase 1 - Patient Registration & Search

## Current Position

Phase: 1 of 4 (Patient Registration & Search)
Plan: 5 of 5 in current phase
Status: Phase 1 Complete - advancing to Phase 2
Last activity: 2026-02-20 — Completed Plan 01-07: REG-12 Gap Closure (photoIdVerified enforcement)

Progress: [████████████████████████░░░░░░] 50%

## Performance Metrics

**Velocity:**
- Total plans completed: 9
- Average duration: 25 minutes
- Total execution time: 3.43 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 00 - Security & Compliance Foundation | 6 | 68 min | 11 min |
| 01 - Patient Registration & Search | 5 | 155 min | 31 min |

**Recent Trend:**
- Last 5 plans: 12 min, 5 min, 114 min, 15 min, 9 min
- Trend: Plan 01-05 efficient (12 min) - exception handler + 8 verification tests

*Updated after each plan completion*
| Phase 01 P01 | 9 | 2 tasks | 14 files |
| Phase 01 P02 | 15 | 2 tasks | 15 files |
| Phase 01 P03 | 114 | 2 tasks | 6 files |
| Phase 01-patient-registration-search P04 | 5 | 2 tasks | 5 files |
| Phase 01-patient-registration-search P05 | 12 | 2 tasks | 5 files |
| Phase 01-patient-registration-search P06 | 4 | 2 tasks | 2 files |
| Phase 01-patient-registration-search P07 | 8 | 2 tasks | 4 files |

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

### Pending Todos

None yet.

### Blockers/Concerns

**Phase 1 Complete (including gap closure):**
- Phase 0 complete: 26/26 security tests passing ✅
- Phase 1 Plan 01 complete: Event-sourced patient data foundation ✅
- Phase 1 Plan 02 complete: Patient registration API with duplicate detection ✅
- Phase 1 Plan 03 complete: Patient search API with JPQL queries ✅
- Phase 1 Plan 04 complete: PatientPermissionEvaluator authorization rules ✅
- Phase 1 Plan 05 complete: RFC 7807 error handling + Phase 1 verification ✅
- Phase 1 Plan 06 complete: Fuzzy name matching gap closure (SRCH-04) ✅
- Phase 1 Plan 07 complete: REG-12 gap closure (photoIdVerified enforcement at registration) ✅
- 13/13 PatientSearchRepositoryTest passing; 11/11 Phase01VerificationTest passing (24/24 combined)
- SRCH-04 closed: "Jon" finds "John", "Smyth" finds "Smith" via Levenshtein edit-distance 2
- REG-12 closed: POST /api/v1/patients with photoIdVerified=false/null returns 400; with true returns 201
- Next: Phase 2 (Patient Management - edit, status changes)

## Session Continuity

Last session: 2026-02-20
Stopped at: Completed 01-07-PLAN.md (REG-12 Gap Closure - photoIdVerified enforcement at registration)
Resume file: .planning/phases/01-patient-registration-search/01-07-SUMMARY.md
Next action: Begin Phase 2 (Patient Management - edit, status changes)
