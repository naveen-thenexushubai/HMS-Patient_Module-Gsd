# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-19)

**Core value:** Centralized, secure, and efficient patient information management that serves as the foundation for all other hospital modules
**Current focus:** Phase 3 - Operational Enhancements

## Current Position

Phase: 3 of 4 (Operational Enhancements)
Plan: 5 of 6 in current phase
Status: In Progress
Last activity: 2026-02-20 — Completed Plan 03-05: Smart Form Assistance (ZIP code auto-complete via Caffeine-cached Zippopotam.us API, insurance plan suggestion endpoint)

Progress: [████████████████████████████████████████] 80%

## Performance Metrics

**Velocity:**
- Total plans completed: 14
- Average duration: 17 minutes
- Total execution time: 3.81 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 00 - Security & Compliance Foundation | 6 | 68 min | 11 min |
| 01 - Patient Registration & Search | 5 | 155 min | 31 min |
| 02 - Patient Updates & Status Management | 5 | 22 min | 4 min |
| 03 - Operational Enhancements | 5 | 3 min | 1 min |

**Recent Trend:**
- Last 5 plans: 15 min, 6 min, 2 min, 9 min, 2 min
- Trend: Phase 3 started with infrastructure plan in 2 min — established patterns carry forward

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
| Phase 03-operational-enhancements P01 | 2 | 2 tasks | 8 files |
| Phase 03-operational-enhancements P02 | 8 | 2 tasks | 7 files |
| Phase 03-operational-enhancements P03 | 2 | 2 tasks | 5 files |
| Phase 03 P04 | 2 | 1 tasks | 4 files |
| Phase 03-operational-enhancements P05 | 1 | 2 tasks | 4 files |

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
- [Plan 03-01]: No FK from patient_photos.patient_business_id to patients — event-sourced pattern, business_id repeats across versions
- [Plan 03-01]: V007 DEFAULT true on is_registration_complete — existing rows treated as complete without data migration
- [Plan 03-01]: @EnableCaching on CacheConfig only, not HospitalApplication — avoids duplicate startup warning
- [Plan 03-01]: RestTemplate declared as @Bean — Spring Boot 3 does not auto-configure RestTemplate
- [Plan 03-01]: FileStorageService uses constructor @Value injection for early storage directory creation at bean init time
- [Plan 03-02]: QuickRegistrationService delegates to PatientService.getPatientByBusinessId() after save — avoids duplicating toDetailResponse() and ensures consistent response with insurance, contacts, registeredAt
- [Plan 03-02]: Boolean (wrapper) not boolean (primitive) for isRegistrationComplete in Patient — null-safe for pre-Phase-3 rows; @PrePersist and service-layer null guards default to true
- [Plan 03-02]: QuickRegistrationController and PatientController share /api/v1/patients base path; Spring MVC disambiguates via /quick sub-path — no conflict
- [Phase 03-03]: Object[] return from PhotoService.getCurrentPhoto() to avoid creating a dedicated wrapper DTO for a two-value tuple (Resource + contentType String)
- [Phase 03-03]: MaxUploadSizeExceededException handler in @RestControllerAdvice — exception thrown before controller executes, cannot be caught in try/catch in controller
- [Phase 03]: nativeQuery=true required for all patients_latest view queries — JPQL resolves to patients entity table (wrong multi-version counts)
- [Phase 03]: Admin endpoint at /api/v1/admin prefix for data quality dashboard — aggregate quality data not exposed to clinical staff (DOCTOR/NURSE get 403)
- [Plan 03-05]: @JsonProperty on space-containing Zippopotam.us JSON keys ("place name", "state abbreviation") — mandatory for correct deserialization
- [Plan 03-05]: HTTP 404 from Zippopotam.us caught as Optional.empty() — 404 is expected user input outcome, not an error
- [Plan 03-05]: InsuranceSuggestionService does not use @Cacheable — SmartFormProperties already holds list in JVM heap via ConfigurationProperties binding

### Pending Todos

None yet.

### Blockers/Concerns

**Phase 3 In Progress:**
- Phase 0 complete: 26/26 security tests passing
- Phase 1 complete (all 7 plans including gap closures)
- Phase 2 complete: 16 integration tests, all Phase 2 success criteria verified
- Phase 3 Plan 01 complete: Infrastructure foundation (V006/V007 migrations, FileStorageService, CacheConfig, SmartFormProperties, RestTemplateConfig)
- Phase 3 Plan 02 complete: Quick registration (POST /api/v1/patients/quick, QuickRegisterRequest, QuickRegistrationService, isRegistrationComplete field)
- Phase 3 Plans 03-05 complete (executed previously)

## Session Continuity

Last session: 2026-02-20
Stopped at: Completed 03-02-PLAN.md (Quick Registration — QuickRegisterRequest, QuickRegistrationService, QuickRegistrationController, isRegistrationComplete field)
Resume file: .planning/phases/03-operational-enhancements/03-02-SUMMARY.md
Next action: Execute Phase 3 Plan 06 (verification tests)
