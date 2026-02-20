---
phase: 01-patient-registration-search
plan: 06
subsystem: api
tags: [levenshtein, fuzzy-search, commons-text, jpql, patient-search]

# Dependency graph
requires:
  - phase: 01-patient-registration-search
    plan: 03
    provides: PatientSearchRepository with JPQL LIKE-based search

provides:
  - Fuzzy name matching second pass in PatientSearchRepository.searchPatients()
  - isNameLikeQuery() guard that prevents fuzzy on phone/email/ID queries
  - fuzzyNameSearch() in-memory Levenshtein edit-distance filtering
  - Three integration tests verifying fuzzy behavior (firstName variation, lastName variation, non-name guard)

affects:
  - Phase 2 Patient Management (search used in patient lookup before updates)
  - Phase 3 Advanced Search (this is the interim fuzzy approach before Lucene)

# Tech tracking
tech-stack:
  added: []  # commons-text was already a dependency via DuplicateDetectionService
  patterns:
    - Two-pass search: LIKE (DB) + Levenshtein (in-memory) merged by patientId deduplication
    - isNameLikeQuery guard pattern for selective fuzzy activation

key-files:
  created: []
  modified:
    - src/main/java/com/hospital/patient/infrastructure/PatientSearchRepository.java
    - src/test/java/com/hospital/patient/infrastructure/PatientSearchRepositoryTest.java

key-decisions:
  - "maxEditDistance=2 chosen: handles 'Jon'->John' (dist 1) and 'Jonhn'->'John' (dist 2); tighter than typical Lucene defaults"
  - "Fuzzy pass fetches ALL filtered patients unpaginated then filters in-memory: safe for Phase 1 <10K scale, avoids full-text index"
  - "Deduplication by patientId (the @Id generated key) not businessId: patientId is the unique row identifier per page slice"
  - "isNameLikeQuery uses digit/@ presence as non-name signal: simple, fast, avoids false fuzzy on phone/email/ID queries"

patterns-established:
  - "Two-pass search: DB LIKE pass first for speed, in-memory Levenshtein second pass for fuzzy name tolerance"
  - "Guard condition before fuzzy: isNameLikeQuery() prevents edit-distance on structured data like phone/email"

requirements-completed: [SRCH-04]

# Metrics
duration: 4min
completed: 2026-02-20
---

# Phase 1 Plan 06: Fuzzy Name Matching for Patient Search Summary

**In-memory Levenshtein second pass added to PatientSearchRepository: "Jon" now finds "John" and "Smyth" now finds "Smith" via edit-distance 2 guard-checked against isNameLikeQuery()**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-02-20T05:18:47Z
- **Completed:** 2026-02-20T05:22:30Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Added `isNameLikeQuery()` guard that prevents fuzzy matching on phone numbers, emails, and patient IDs (any query containing digits or @ is skipped)
- Added `fuzzyNameSearch()` method that fetches all status/gender/bloodGroup-filtered patients and applies Levenshtein edit-distance filtering in-memory (maxEditDistance=2)
- Integrated fuzzy second pass into `searchPatients()` with deduplication by `patientId` to prevent duplicate entries in merged results
- Added 3 integration tests verifying firstName variation ("Jon"→"John"), lastName variation ("Smyth"→"Smith"), and non-name query guard (digit-containing query skips fuzzy)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add fuzzy name matching second pass to PatientSearchRepository** - `67911ad` (feat)
2. **Task 2: Add fuzzy search integration tests to PatientSearchRepositoryTest** - `03d5c37` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified
- `src/main/java/com/hospital/patient/infrastructure/PatientSearchRepository.java` - Added `isNameLikeQuery()`, `fuzzyNameSearch()`, and fuzzy merge logic in `searchPatients()`
- `src/test/java/com/hospital/patient/infrastructure/PatientSearchRepositoryTest.java` - Added 3 fuzzy test methods: `fuzzySearch_findsPatientByFirstNameSpellingVariation`, `fuzzySearch_findsPatientByLastNameSpellingVariation`, `fuzzySearch_doesNotActivateForNonNameQueries`

## Decisions Made
- `maxEditDistance=2` selected to handle 1-character variations ("Jon"→"John", "Smyth"→"Smith") and 2-character variations; wider than 2 would cause too many false positives
- Fuzzy pass fetches ALL patients matching filter criteria (no pagination) then filters in-memory — acceptable for Phase 1 scale (<10K patients), avoids needing full-text indexing
- Used `LevenshteinDistance.getDefaultInstance()` consistent with DuplicateDetectionService pattern already in the codebase
- Deduplication uses `patientId` (the `@Id` generated column) as the unique row key; each patient row has a unique `patientId`

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- **PostgreSQL not accessible on port 5435 during tests:** The existing `hospital-postgres` Docker container (started via separate compose stack) had no port bindings. Started a temporary `hospital-postgres-test` container with `5435:5432` binding to run integration tests. This is an infrastructure issue unrelated to the code change — tests passed after port was made available.
- **`emptySearch_returnsAllPatients_sorted` failure when run with Phase01VerificationTest:** Pre-existing DB state pollution issue (documented in prior plan summaries). PatientSearchRepositoryTest alone: 13/13 pass. Phase01VerificationTest alone: 8/8 pass. Combined run: 1 failure (pre-existing `emptySearch_returnsAllPatients_sorted` gets count 7 instead of 5 due to cross-test pollution). This is the same pre-existing failure documented in deferred-items.md.

## Next Phase Readiness
- SRCH-04 gap is now closed: fuzzy name matching active in patient search path
- Phase 2 (Patient Management) can proceed: search is ready for patient lookup before edits
- Phase 3 Advanced Search: this in-memory Levenshtein approach is the interim solution until Lucene/Hibernate Search is introduced

---
*Phase: 01-patient-registration-search*
*Completed: 2026-02-20*
