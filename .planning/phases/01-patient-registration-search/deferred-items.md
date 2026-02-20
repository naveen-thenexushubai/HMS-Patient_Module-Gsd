# Deferred Items - Phase 01

## Out of Scope Issues (Not Fixed)

### PatientSearchRepository Compilation Error
- **Found during:** Plan 01-02, Task 1 compilation
- **File:** `src/main/java/com/hospital/patient/infrastructure/PatientSearchRepository.java`
- **Issue:** Hibernate Search type incompatibility in lambda expression (line 98)
- **Status:** File was untracked and prematurely created. Removed to unblock compilation.
- **Reason:** Hibernate Search implementation belongs to Phase 3 (Advanced Search), not Phase 1
- **Resolution:** Will be properly implemented in Phase 3 when search features are in scope

## Pre-existing Test Failures (Identified During Plan 01-05)

### PatientRepositoryTest.shouldFindLatestVersionsByStatus

- **Status:** Pre-existing failure (confirmed existed before Plan 01-05 changes)
- **Root cause:** Database state pollution - test expects exactly 2 active patients but finds 3+ due to leftover records from previous test runs in other test classes
- **Note:** Uses `@DataJpaTest` with `@AutoConfigureTestDatabase(replace = NONE)` (real database), so data from integration test classes accumulates
- **Fix needed:** Add truncate/cleanup in `@BeforeEach`, or use `@Sql` annotations for isolation
- **Deferred to:** Phase maintenance or Phase 02 test setup work

### PatientSearchRepositoryTest.emptySearch_returnsAllPatients_sorted

- **Status:** Pre-existing failure (confirmed existed before Plan 01-05 changes)
- **Root cause:** Same database state pollution - test expects 5 records but finds 6+
- **Note:** Same `@DataJpaTest` + real database isolation issue
- **Fix needed:** Same as above - proper test isolation with database cleanup
- **Deferred to:** Phase maintenance or Phase 02 test setup work
