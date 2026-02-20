---
phase: 03-operational-enhancements
plan: 01
subsystem: infra
tags: [flyway, caffeine, cache, multipart, file-storage, configuration-properties, rest-template, postgresql]

# Dependency graph
requires:
  - phase: 02-patient-updates-status-management
    provides: patients table schema for V007 ALTER TABLE; event-sourced pattern for patient_photos no-FK design
provides:
  - V006 patient_photos DDL with patient_business_id, filename, content_type, file_size_bytes, uploaded_at, uploaded_by, is_current and two indexes
  - V007 is_registration_complete BOOLEAN NOT NULL DEFAULT true on patients table
  - FileStorageService with UUID-filename storage, ImageIO magic-byte validation, path traversal prevention
  - RestTemplate @Bean for HTTP calls (used by ZipLookupService in 03-05)
  - CaffeineCacheManager with zipLookup cache (50K max, 24h TTL) via @EnableCaching
  - SmartFormProperties @ConfigurationProperties(app.smart-form) with 10 insurance plan names
  - application.yml: spring.servlet.multipart (5MB max) and app: section with photos-dir, insurance-plans
affects:
  - 03-02-quick-registration (needs V007 is_registration_complete column)
  - 03-03-photo-upload (needs FileStorageService store/load methods)
  - 03-04-patient-photos (needs patient_photos table from V006)
  - 03-05-zip-lookup (needs CacheConfig zipLookup cache + RestTemplate bean)

# Tech tracking
tech-stack:
  added:
    - spring-boot-starter-cache (Spring caching abstraction)
    - caffeine 3.1.8 (in-memory cache with LRU eviction and TTL)
  patterns:
    - UUID-filename pattern for file uploads (prevents path traversal attacks)
    - ImageIO magic-byte validation over MIME-type-only trust
    - @ConfigurationProperties for type-safe YAML list binding
    - @EnableCaching on dedicated CacheConfig class (not on HospitalApplication)
    - Flyway DEFAULT true on new NOT NULL columns (backward compatibility with existing rows)

key-files:
  created:
    - src/main/resources/db/migration/V006__add_patient_photos_table.sql
    - src/main/resources/db/migration/V007__add_registration_complete_flag.sql
    - src/main/java/com/hospital/storage/FileStorageService.java
    - src/main/java/com/hospital/config/RestTemplateConfig.java
    - src/main/java/com/hospital/config/CacheConfig.java
    - src/main/java/com/hospital/smartform/config/SmartFormProperties.java
  modified:
    - pom.xml (added spring-boot-starter-cache, caffeine 3.1.8)
    - src/main/resources/application.yml (servlet.multipart + app: section)

key-decisions:
  - "No FK from patient_photos.patient_business_id to patients — event-sourced pattern: same business_id repeats across versions, no unique constraint on business_id"
  - "V007 DEFAULT true on is_registration_complete — all existing patient rows must be treated as complete without data migration"
  - "@EnableCaching on CacheConfig only — NOT on HospitalApplication to avoid duplicate startup warning"
  - "FileStorageService uses @Value constructor injection (not @Autowired field) so storage directory is created during bean initialization"
  - "RestTemplate declared as @Bean explicitly — Spring Boot 3 does not auto-configure RestTemplate"
  - "SmartFormProperties uses @Configuration + @ConfigurationProperties — no separate @EnableConfigurationProperties needed"

patterns-established:
  - "UUID-filename pattern: UUID.randomUUID() + extension — never use original upload filename"
  - "ImageIO.read() null check: validates magic bytes beyond MIME type claim, returns null if not a valid image"
  - "Path traversal guard: target.startsWith(storageLocation) after normalize()"
  - "Multipart nested in spring: block — SnakeYAML does NOT merge duplicate top-level keys"

requirements-completed: []

# Metrics
duration: 2min
completed: 2026-02-20
---

# Phase 3 Plan 01: Phase 3 Infrastructure Foundation Summary

**Flyway migrations V006/V007, Caffeine CacheManager with zipLookup cache, FileStorageService with UUID+ImageIO validation, RestTemplate bean, and SmartFormProperties binding for 10 insurance plans**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-20T08:18:39Z
- **Completed:** 2026-02-20T08:20:57Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- Flyway V006 creates patient_photos table with UUID-based file metadata storage and two indexes (by patient, by current flag)
- Flyway V007 adds is_registration_complete BOOLEAN NOT NULL DEFAULT true to patients table for quick-registration workflow
- FileStorageService provides secure photo storage with UUID filenames, ImageIO magic-byte validation, and path traversal prevention
- CacheConfig enables Caffeine-backed Spring caching with zipLookup cache (50K entries, 24h TTL) for ZIP code lookup
- SmartFormProperties binds 10 insurance plan names from application.yml via @ConfigurationProperties
- RestTemplateConfig provides RestTemplate @Bean for HTTP calls to Zippopotam.us API

## Task Commits

Each task was committed atomically:

1. **Task 1: pom.xml Caffeine deps + V006 patient_photos migration + V007 is_registration_complete migration** - `fe28860` (feat)
2. **Task 2: FileStorageService + RestTemplateConfig + CacheConfig + SmartFormProperties + application.yml** - `63836aa` (feat)

## Files Created/Modified
- `pom.xml` - Added spring-boot-starter-cache and caffeine 3.1.8 dependencies
- `src/main/resources/db/migration/V006__add_patient_photos_table.sql` - patient_photos DDL with id, patient_business_id, filename, content_type, file_size_bytes, uploaded_at, uploaded_by, is_current and two indexes
- `src/main/resources/db/migration/V007__add_registration_complete_flag.sql` - ALTER TABLE patients ADD COLUMN is_registration_complete BOOLEAN NOT NULL DEFAULT true
- `src/main/java/com/hospital/storage/FileStorageService.java` - Local filesystem storage with UUID filenames, ImageIO magic-byte validation, path traversal prevention
- `src/main/java/com/hospital/config/RestTemplateConfig.java` - @Bean RestTemplate for ZIP code API calls
- `src/main/java/com/hospital/config/CacheConfig.java` - @EnableCaching + CaffeineCacheManager (zipLookup cache, 50K max, 24h TTL)
- `src/main/java/com/hospital/smartform/config/SmartFormProperties.java` - @ConfigurationProperties(prefix = "app.smart-form") with List<String> insurancePlans
- `src/main/resources/application.yml` - servlet.multipart (5MB max-file-size) nested in spring: block; new app: section with photos-dir and 10 insurance-plans

## Decisions Made
- No FK from patient_photos.patient_business_id to patients — event-sourced pattern: same business_id repeats across version rows, no unique constraint on business_id in patients table
- V007 DEFAULT true ensures all existing patient rows are treated as registration-complete without requiring a data migration
- @EnableCaching placed on CacheConfig only (not HospitalApplication) to avoid duplicate Spring startup warning
- FileStorageService uses constructor @Value injection so storage directory creation happens at bean initialization time, surfacing config errors early
- RestTemplate must be declared as @Bean in Spring Boot 3 — not auto-configured unlike Spring Boot 2.x
- SmartFormProperties uses @Configuration + @ConfigurationProperties — satisfies component scanning without a separate @EnableConfigurationProperties

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Project has no Maven wrapper (./mvnw) — used system `mvn` command directly. Build succeeded normally.

## User Setup Required
None - no external service configuration required. The app.storage.photos-dir defaults to /var/hospital/patient-photos (overridable via PHOTOS_DIR env var).

## Next Phase Readiness
- 03-02 (Quick Registration): V007 migration provides is_registration_complete column. Ready to implement quick-registration API.
- 03-03 (Photo Upload): FileStorageService provides store()/load() methods. Ready to implement PhotoService and upload endpoint.
- 03-04 (Patient Photos): V006 provides patient_photos table. Ready to implement photo repository and metadata management.
- 03-05 (ZIP Lookup): CacheConfig provides zipLookup Caffeine cache; RestTemplateConfig provides RestTemplate bean. Ready to implement ZipLookupService with @Cacheable.

---
*Phase: 03-operational-enhancements*
*Completed: 2026-02-20*
