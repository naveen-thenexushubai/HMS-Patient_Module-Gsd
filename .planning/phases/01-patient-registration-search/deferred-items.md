# Deferred Items - Phase 01

## Out of Scope Issues (Not Fixed)

### PatientSearchRepository Compilation Error
- **Found during:** Plan 01-02, Task 1 compilation
- **File:** `src/main/java/com/hospital/patient/infrastructure/PatientSearchRepository.java`
- **Issue:** Hibernate Search type incompatibility in lambda expression (line 98)
- **Status:** File was untracked and prematurely created. Removed to unblock compilation.
- **Reason:** Hibernate Search implementation belongs to Phase 3 (Advanced Search), not Phase 1
- **Resolution:** Will be properly implemented in Phase 3 when search features are in scope
