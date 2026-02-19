# Phase 1: Patient Registration & Search - Context

**Gathered:** 2026-02-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Staff can register new patients with complete demographics, search existing patients efficiently, and view patient profiles with duplicate prevention. This phase builds the core patient data layer that all 40+ hospital modules will depend on.

Registration includes: demographics, emergency contacts, medical history (allergies, chronic conditions). Search supports: patient ID, name, phone, email with fuzzy matching. Profile view shows complete patient information with edit permissions based on role.

</domain>

<decisions>
## Implementation Decisions

### Patient Entity Structure
- **Emergency contacts:** Separate EmergencyContact table (normalized) with FK to Patient
  - Allows multiple contacts per patient
  - Easier to query contacts independently
  - Better audit trail for contact changes
- **Medical information:** Separate MedicalHistory table (normalized)
  - Structured fields with timestamps
  - Records who entered each medical history entry
  - Full audit compliance for PHI changes
- **Mutability:** Immutable event-sourced pattern
  - Never update patient records, only insert new versions
  - Full history preserved for HIPAA audit compliance
  - Complex queries (need latest version logic) but complete audit trail

### Search Implementation
- **Technology:** JPA/Hibernate queries vs Hibernate Search vs Elasticsearch - choose based on 10K record performance target and fuzzy matching needs
- **Fuzzy matching:** Levenshtein vs Soundex/Metaphone vs both - choose based on healthcare name matching patterns
- **Pagination:** Slice-based (no count) recommended by Phase 0 research - choose based on performance needs
- **Result ordering:** Exact matches first vs most recent vs relevance scoring - choose based on receptionist workflow

### Duplicate Detection
- **Match fields:** ALL of the following (comprehensive detection):
  - Name + Date of Birth (core demographic match)
  - Phone number (catches name variations for same person)
  - Email address (strong identifier when provided)
  - Address (useful but may change)
- **Matching strictness:** Strict vs moderate vs loose fuzzy thresholds - choose based on false positive tolerance
- **When to check:** During form submission vs before registration - choose based on workflow efficiency
- **User workflow:** Block registration with manual override required
  - Show potential duplicates with match details
  - Require receptionist to explicitly confirm "Register Anyway"
  - Prevents accidental duplicates, prioritizes data quality
  - Note: Stricter than REG-11 requirement (which only warns)

### API Design Patterns
- **Versioning:** URL path versioning (`/api/v1/patients`)
  - Clear, visible, easy to test
  - Sets precedent for all 40+ modules
  - Allows parallel versions during migration
- **Request DTOs:** Flat vs nested structure - choose based on frontend form organization
- **Error responses:** Field-level errors vs RFC 7807 Problem Details - choose based on frontend validation needs
- **GET response for immutable records:** Latest only vs with history vs separate history endpoint - choose based on Phase 2 audit requirements

### Claude's Discretion
- Specific versioning implementation (patient_id + version vs surrogate ID + business key)
- Search technology selection (JPA vs Hibernate Search)
- Fuzzy matching algorithm selection
- Pagination strategy (slice vs page)
- Result ordering algorithm
- Duplicate detection threshold tuning
- DTO structure (flat vs nested)
- Validation error format
- GET response structure for history

</decisions>

<specifics>
## Specific Ideas

- Phase 0 established audit logging infrastructure - build on that for patient change tracking
- Research recommended Slice-based pagination for performance - consider for search results
- HIPAA Security Risk Assessment from Phase 0 covers patient data - ensure alignment
- JWT authentication already implements role-based access (Receptionist, Doctor, Nurse, Admin) - enforce in patient API
- Object-level authorization framework (PatientPermissionEvaluator) from Phase 0 ready to implement actual rules

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope (registration, search, profile view). Updates, status management, insurance, and emergency contact CRUD are Phase 2.

</deferred>

---

*Phase: 01-patient-registration-search*
*Context gathered: 2026-02-19*
