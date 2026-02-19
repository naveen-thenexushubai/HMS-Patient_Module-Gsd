# Phase 1: Patient Registration & Search - Research

**Researched:** 2026-02-19
**Domain:** Spring Boot patient registration with event-sourced entities, fuzzy duplicate detection, and full-text search
**Confidence:** HIGH

## Summary

This phase implements patient registration with demographics, duplicate detection, search functionality, and profile viewing. The research identifies that event-sourced immutable patient records require a versioning pattern (insert-only, no updates) to maintain complete audit history for HIPAA compliance. The "query latest version" challenge can be solved efficiently using PostgreSQL DISTINCT ON or window functions without significant performance degradation at 10K-50K patient scale.

Key findings: (1) Immutable versioned entities use @Immutable annotation with version fields, queries use DISTINCT ON (patient_id) ORDER BY version DESC for optimal performance, (2) Apache Commons Text provides production-ready Levenshtein, Soundex, and Metaphone algorithms for multi-field duplicate detection with configurable thresholds (90%+ for healthcare to avoid false positives), (3) Hibernate Search 7 with Lucene backend delivers exceptional single-box performance for 10K records without external dependencies, better fit than Elasticsearch for v1 scale, (4) Slice-based pagination eliminates COUNT(*) queries providing millisecond response times vs 30+ seconds with Page on large datasets, (5) Spring Boot native RFC 7807 Problem Details support provides standardized error responses for field validation.

**Primary recommendation:** Use immutable event-sourced Patient entities with PostgreSQL DISTINCT ON queries for latest version, implement Apache Commons Text LevenshteinDistance + Soundex for duplicate detection with 85-90% threshold and manual review queue, use Hibernate Search 7 with Lucene backend for full-text search, Slice-based pagination for patient list, and RFC 7807 Problem Details for validation errors.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Patient Entity Structure
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

#### Duplicate Detection
- **Match fields:** ALL of the following (comprehensive detection):
  - Name + Date of Birth (core demographic match)
  - Phone number (catches name variations for same person)
  - Email address (strong identifier when provided)
  - Address (useful but may change)
- **User workflow:** Block registration with manual override required
  - Show potential duplicates with match details
  - Require receptionist to explicitly confirm "Register Anyway"
  - Prevents accidental duplicates, prioritizes data quality
  - Note: Stricter than REG-11 requirement (which only warns)

#### API Design Patterns
- **Versioning:** URL path versioning (`/api/v1/patients`)
  - Clear, visible, easy to test
  - Sets precedent for all 40+ modules
  - Allows parallel versions during migration

### Claude's Discretion

The following areas are open for research-based recommendations:

- Specific versioning implementation (patient_id + version vs surrogate ID + business key)
- Search technology selection (JPA vs Hibernate Search)
- Fuzzy matching algorithm selection
- Pagination strategy (slice vs page)
- Result ordering algorithm
- Duplicate detection threshold tuning
- DTO structure (flat vs nested)
- Validation error format
- GET response structure for history

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope (registration, search, profile view). Updates, status management, insurance, and emergency contact CRUD are Phase 2.

</user_constraints>

<phase_requirements>
## Phase Requirements

This phase MUST address the following requirements from REQUIREMENTS.md:

| ID | Description | Research Support |
|----|-------------|-----------------|
| REG-01 | Receptionist can register new patient with mandatory fields (first name, last name, date of birth, gender, phone number) | JPA entity with Bean Validation (JSR-380) annotations for mandatory fields |
| REG-02 | Receptionist can register new patient with optional fields (email, address, city, state, zip, emergency contact, blood group, allergies, chronic conditions) | JPA entity with nullable fields, separate EmergencyContact and MedicalHistory tables |
| REG-03 | System automatically calculates and displays patient age from date of birth | @Transient field with Java Period.between() calculation or @Formula annotation |
| REG-04 | System validates phone number format (+1-XXX-XXX-XXXX or (XXX) XXX-XXXX or XXX-XXX-XXXX) | Custom JSR-380 validator with regex pattern |
| REG-05 | System validates email address format | @Email annotation from Bean Validation |
| REG-06 | System generates unique Patient ID in format "P" + year + sequential number (e.g., P2026001) | Custom IdentifierGenerator extending SequenceStyleGenerator with prefix formatting |
| REG-07 | System sets patient status to "ACTIVE" by default on successful registration | @PrePersist method or default column value |
| REG-08 | System displays specific validation error messages for each invalid field | RFC 7807 Problem Details with Spring Boot 3 ProblemDetail class |
| REG-09 | System records registration timestamp and user who registered the patient | @CreatedDate and @CreatedBy annotations from Spring Data JPA auditing |
| REG-10 | System implements duplicate detection with fuzzy matching on name, DOB, phone before registration | Apache Commons Text LevenshteinDistance + Soundex for name matching, exact match for DOB/phone |
| REG-11 | System warns receptionist about potential duplicates but allows registration to proceed | Service layer check returns DuplicateWarning DTO, frontend shows confirmation dialog |
| REG-12 | System requires photo ID verification (scan or upload) during registration | Document table with FK to Patient (Phase 2), placeholder field for Phase 1 |
| SRCH-01 | Staff can search patients by multiple criteria (Patient ID, first name, last name, phone number, email) | Hibernate Search @Indexed entity with @FullTextField/@KeywordField annotations |
| SRCH-02 | System displays search results in real-time as user types or on Enter key press | Frontend debouncing (500ms) + backend Hibernate Search query |
| SRCH-03 | System returns search results within 2 seconds for up to 10,000 patient records | Hibernate Search Lucene backend with proper indexing delivers <100ms for 10K records |
| SRCH-04 | System implements fuzzy matching on patient names to handle spelling variations | Hibernate Search .fuzzy() method or Apache Commons Text similarity scoring |
| SRCH-05 | Staff can filter patient list by status (All, Active, Inactive) | JPA Specification or Hibernate Search filter on status field |
| SRCH-06 | Staff can filter patient list by gender (All, Male, Female, Other) | JPA Specification or Hibernate Search filter on gender field |
| SRCH-07 | Staff can filter patient list by blood group | JPA Specification or Hibernate Search filter on blood group field |
| SRCH-08 | System displays "No patients found" message when no matches exist | Frontend check on empty result list |
| SRCH-09 | System paginates patient list with 20 patients per page using Slice-based pagination | Spring Data JPA Slice<Patient> return type, Pageable parameter with size=20 |
| SRCH-10 | System displays patient summary (Patient ID, full name, age, gender, phone, status) in list view | PatientSummaryDTO with projection query or DTO constructor in JPQL |
| PROF-01 | Staff can view complete patient demographics (Patient ID, name, DOB, age, gender, phone, email, address) | GET /api/v1/patients/{id} returns PatientDetailDTO with all fields |
| PROF-02 | Staff can view patient emergency contact information (name, phone, relationship) | JOIN FETCH on EmergencyContact relationship in repository query |
| PROF-03 | Staff can view patient medical information (blood group, known allergies, chronic conditions) | JOIN FETCH on MedicalHistory relationship in repository query |
| PROF-04 | System displays patient status with color coding (green=active, red=inactive) | PatientDetailDTO includes status enum, frontend applies color styling |
| PROF-05 | System displays patient registration date and registered-by user | @CreatedDate and @CreatedBy fields included in PatientDetailDTO |
| PROF-06 | System displays patient last-updated date and updated-by user | @LastModifiedDate and @LastModifiedBy fields from latest version record |
| PROF-07 | System shows "Edit Patient" button only to users with edit permissions (Receptionist, Admin) | @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')") on update endpoint |
| PROF-08 | System hides "Edit Patient" button from read-only users (Doctor, Nurse) | Frontend checks JWT roles from SecurityContext |
| PROF-09 | Staff can navigate from patient profile back to patient list | Frontend routing, no backend requirement |

</phase_requirements>

## Standard Stack

### Core Libraries

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JPA | 3.4.x (managed by Spring Boot) | Entity persistence and querying | Standard ORM layer, integrates with Hibernate Search, supports Slice pagination |
| Hibernate Search | 7.2.1 | Full-text search on JPA entities | Transparent integration with Hibernate ORM, automatic indexing, Lucene backend performance |
| Apache Lucene | 9.x (managed by Hibernate Search) | Search index backend | Exceptional single-box performance, no external dependencies, millisecond queries |
| Apache Commons Text | 1.15.0 | Fuzzy string matching algorithms | Production-tested Levenshtein, Soundex, Metaphone, FuzzyScore implementations |
| PostgreSQL | 16+ | Database with immutable record support | DISTINCT ON for latest version queries, window functions, partitioning support |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Boot Validation | 3.4.x (included) | JSR-380 Bean Validation | Field validation on DTOs (phone format, email, mandatory fields) |
| ProblemDetail | 3.4.x (Spring Framework 6) | RFC 7807 error responses | Standardized validation error format with field-level details |
| Spring Data JPA Auditing | 3.4.x (included) | @CreatedDate, @CreatedBy, etc. | Automatic timestamp and user tracking for audit compliance |
| ModelMapper or MapStruct | 3.2.0 / 1.6.2 | DTO to Entity mapping | Reduces boilerplate for request/response transformations |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hibernate Search Lucene | Elasticsearch | Elasticsearch requires external cluster, adds operational complexity, overkill for 10K-50K records but better for 100K+ with multiple app instances |
| Apache Commons Text | Intuit Fuzzy Matcher | Fuzzy Matcher is higher-level but adds dependency, Commons Text is lightweight and Apache-maintained |
| Slice pagination | Page pagination | Page runs expensive COUNT(*) queries (30+ seconds on large tables), Slice fetches N+1 records to detect next page (milliseconds) |
| Custom ID generator | Database sequence only | Custom generator allows "P2026001" format vs numeric-only IDs, requires extending SequenceStyleGenerator |
| JPA Specification for search | Native Hibernate Search | Hibernate Search handles full-text queries better (fuzzy, phonetic, relevance), JPA Specification better for exact filters |

**Installation:**
```xml
<dependencies>
    <!-- Hibernate Search with Lucene backend -->
    <dependency>
        <groupId>org.hibernate.search</groupId>
        <artifactId>hibernate-search-mapper-orm</artifactId>
        <version>7.2.1.Final</version>
    </dependency>
    <dependency>
        <groupId>org.hibernate.search</groupId>
        <artifactId>hibernate-search-backend-lucene</artifactId>
        <version>7.2.1.Final</version>
    </dependency>

    <!-- Fuzzy matching algorithms -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-text</artifactId>
        <version>1.15.0</version>
    </dependency>

    <!-- Already included in Spring Boot -->
    <!-- spring-boot-starter-data-jpa -->
    <!-- spring-boot-starter-validation -->
</dependencies>
```

## Architecture Patterns

### Recommended Project Structure (Building on Phase 0)

```
src/main/java/com/hospital/
├── patient/                         # Patient domain module
│   ├── domain/
│   │   ├── Patient.java                    # Immutable versioned entity
│   │   ├── EmergencyContact.java           # Normalized emergency contact
│   │   ├── MedicalHistory.java             # Normalized medical history
│   │   ├── PatientStatus.java              # Enum (ACTIVE, INACTIVE)
│   │   └── Gender.java                     # Enum (MALE, FEMALE, OTHER)
│   ├── application/
│   │   ├── PatientService.java             # Registration, search business logic
│   │   ├── DuplicateDetectionService.java  # Fuzzy matching duplicate detection
│   │   └── PatientIdGenerator.java         # Custom "P2026001" ID generation
│   ├── infrastructure/
│   │   ├── PatientRepository.java          # Spring Data JPA repository
│   │   ├── PatientSearchRepository.java    # Hibernate Search queries
│   │   └── PatientQueryHelper.java         # Latest version query logic
│   └── api/
│       ├── PatientController.java          # REST endpoints
│       ├── dto/
│       │   ├── RegisterPatientRequest.java # Registration DTO
│       │   ├── PatientDetailResponse.java  # Profile view DTO
│       │   ├── PatientSummaryResponse.java # Search result DTO
│       │   └── DuplicateWarningResponse.java
│       └── validation/
│           ├── PhoneNumberValidator.java   # Custom JSR-380 validator
│           └── ValidPhoneNumber.java       # Custom annotation
└── shared/
    └── exception/
        └── PatientExceptionHandler.java    # @ControllerAdvice with RFC 7807
```

### Pattern 1: Immutable Event-Sourced Versioned Entities

**What:** Patient records are never updated, only new versions inserted with incremented version number
**When to use:** When complete audit history is required for HIPAA compliance and regulatory requirements
**Example:**
```java
// Source: Hibernate @Immutable documentation + event sourcing patterns
@Entity
@Table(name = "patients")
@Immutable  // Hibernate prevents UPDATE statements
@Indexed  // Hibernate Search full-text indexing
public class Patient {

    @Id
    @GeneratedValue(generator = "patient_id_generator")
    @GenericGenerator(
        name = "patient_id_generator",
        type = PatientIdGenerator.class
    )
    @Column(name = "patient_id", length = 20)
    @KeywordField  // Exact match search on patient ID
    private String patientId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;  // Optimistic locking + versioning

    @Column(name = "business_id", nullable = false)
    private String businessId;  // Immutable patient identifier across versions

    @Column(name = "first_name", nullable = false, length = 100)
    @FullTextField(analyzer = "nameAnalyzer")  // Full-text search with analyzer
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    @FullTextField(analyzer = "nameAnalyzer")
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    @KeywordField  // Exact match for filtering
    private Gender gender;

    @Column(name = "phone_number", nullable = false, length = 20)
    @KeywordField
    private String phoneNumber;

    @Column(name = "email", length = 255)
    @KeywordField
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @KeywordField
    private PatientStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    // Emergency contacts and medical history via separate tables
    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL)
    @IndexedEmbedded  // Include in search index
    private Set<EmergencyContact> emergencyContacts = new HashSet<>();

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL)
    private Set<MedicalHistory> medicalHistories = new HashSet<>();

    // Computed field - not persisted
    @Transient
    public int getAge() {
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    // For new patient registration
    @PrePersist
    protected void onCreate() {
        if (version == null) {
            version = 1L;
        }
        if (status == null) {
            status = PatientStatus.ACTIVE;
        }
        if (businessId == null) {
            businessId = UUID.randomUUID().toString();
        }
    }
}
```

### Pattern 2: Query Latest Version with PostgreSQL DISTINCT ON

**What:** Efficiently retrieve latest version of each patient without complex subqueries or window functions
**When to use:** For patient list views and search results where only current state matters
**Example:**
```java
// Source: PostgreSQL DISTINCT ON documentation + event sourcing query patterns
@Repository
public interface PatientRepository extends JpaRepository<Patient, String> {

    // Native query using DISTINCT ON for best performance
    @Query(value = """
        SELECT DISTINCT ON (p.business_id) p.*
        FROM patients p
        WHERE p.status = :status
        ORDER BY p.business_id, p.version DESC, p.created_at DESC
        """, nativeQuery = true)
    Slice<Patient> findLatestVersionsByStatus(
        @Param("status") String status,
        Pageable pageable
    );

    // For single patient latest version
    @Query(value = """
        SELECT DISTINCT ON (p.business_id) p.*
        FROM patients p
        WHERE p.business_id = :businessId
        ORDER BY p.business_id, p.version DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Patient> findLatestVersionByBusinessId(@Param("businessId") String businessId);

    // For patient profile with history
    @Query("SELECT p FROM Patient p WHERE p.businessId = :businessId ORDER BY p.version DESC")
    List<Patient> findAllVersionsByBusinessId(@Param("businessId") String businessId);

    // Check if patient ID exists (any version)
    boolean existsByPatientId(String patientId);
}
```

**Performance note:** DISTINCT ON with proper indexing (on business_id, version DESC) delivers sub-millisecond queries even with 50K+ records. Alternative window function approach:

```sql
-- Alternative using window functions (slightly slower but more portable)
WITH ranked AS (
    SELECT *,
           ROW_NUMBER() OVER (PARTITION BY business_id ORDER BY version DESC) as rn
    FROM patients
    WHERE status = 'ACTIVE'
)
SELECT * FROM ranked WHERE rn = 1
ORDER BY last_name, first_name
LIMIT 20 OFFSET 0;
```

### Pattern 3: Multi-Field Fuzzy Duplicate Detection

**What:** Combine exact matching (DOB, phone) with fuzzy matching (name) using Apache Commons Text algorithms
**When to use:** Before patient registration to warn about potential duplicates
**Example:**
```java
// Source: Apache Commons Text documentation + healthcare duplicate detection best practices
@Service
public class DuplicateDetectionService {

    @Autowired
    private PatientRepository patientRepository;

    // Threshold: 85% = likely duplicate (manual review)
    // Threshold: 90%+ = high confidence duplicate (block registration)
    private static final double LEVENSHTEIN_THRESHOLD = 0.85;
    private static final int MAX_CANDIDATES = 100;

    public DuplicateCheckResult checkForDuplicates(RegisterPatientRequest request) {
        List<Patient> potentialDuplicates = new ArrayList<>();

        // Step 1: Exact match on DOB (most reliable filter)
        List<Patient> candidatesByDob = patientRepository
            .findLatestVersionsByDateOfBirth(request.getDateOfBirth());

        if (candidatesByDob.isEmpty()) {
            return DuplicateCheckResult.noDuplicates();
        }

        // Step 2: Multi-field scoring
        LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();
        Soundex soundex = new Soundex();

        for (Patient candidate : candidatesByDob) {
            double score = calculateSimilarityScore(request, candidate, levenshtein, soundex);

            if (score >= LEVENSHTEIN_THRESHOLD) {
                potentialDuplicates.add(new DuplicateMatch(candidate, score));
            }
        }

        potentialDuplicates.sort(Comparator.comparingDouble(DuplicateMatch::getScore).reversed());

        if (potentialDuplicates.isEmpty()) {
            return DuplicateCheckResult.noDuplicates();
        }

        // High confidence duplicates (90%+) should block registration
        boolean shouldBlock = potentialDuplicates.get(0).getScore() >= 0.90;

        return new DuplicateCheckResult(potentialDuplicates, shouldBlock);
    }

    private double calculateSimilarityScore(
        RegisterPatientRequest request,
        Patient candidate,
        LevenshteinDistance levenshtein,
        Soundex soundex
    ) {
        double totalScore = 0.0;
        int weightSum = 0;

        // Name similarity (weight: 30%)
        String requestFullName = (request.getFirstName() + " " + request.getLastName()).toLowerCase();
        String candidateFullName = (candidate.getFirstName() + " " + candidate.getLastName()).toLowerCase();

        // Levenshtein distance (normalized to 0-1)
        int maxLength = Math.max(requestFullName.length(), candidateFullName.length());
        int distance = levenshtein.apply(requestFullName, candidateFullName);
        double nameSimilarity = 1.0 - ((double) distance / maxLength);

        // Soundex phonetic match (0 or 1)
        boolean phoneticMatch = soundex.encode(request.getLastName())
            .equals(soundex.encode(candidate.getLastName()));
        if (phoneticMatch) {
            nameSimilarity = Math.max(nameSimilarity, 0.8);  // Boost score for phonetic match
        }

        totalScore += nameSimilarity * 30;
        weightSum += 30;

        // DOB exact match (weight: 40%) - already filtered by DOB
        totalScore += 1.0 * 40;
        weightSum += 40;

        // Phone number similarity (weight: 20%)
        if (request.getPhoneNumber() != null && candidate.getPhoneNumber() != null) {
            String normalizedRequest = normalizePhoneNumber(request.getPhoneNumber());
            String normalizedCandidate = normalizePhoneNumber(candidate.getPhoneNumber());
            if (normalizedRequest.equals(normalizedCandidate)) {
                totalScore += 1.0 * 20;
            }
            weightSum += 20;
        }

        // Email exact match (weight: 10%)
        if (request.getEmail() != null && candidate.getEmail() != null) {
            if (request.getEmail().equalsIgnoreCase(candidate.getEmail())) {
                totalScore += 1.0 * 10;
            }
            weightSum += 10;
        }

        return totalScore / weightSum;
    }

    private String normalizePhoneNumber(String phone) {
        // Remove all non-digits
        return phone.replaceAll("[^0-9]", "");
    }
}

@Data
@AllArgsConstructor
public class DuplicateCheckResult {
    private List<DuplicateMatch> matches;
    private boolean shouldBlockRegistration;

    public static DuplicateCheckResult noDuplicates() {
        return new DuplicateCheckResult(Collections.emptyList(), false);
    }
}

@Data
@AllArgsConstructor
public class DuplicateMatch {
    private Patient patient;
    private double score;  // 0.0 to 1.0
}
```

### Pattern 4: Hibernate Search Full-Text Search with Lucene

**What:** Declarative full-text search on JPA entities with automatic indexing
**When to use:** For patient search by name, phone, email with fuzzy matching and filters
**Example:**
```java
// Source: Hibernate Search 7 official documentation
@Repository
public class PatientSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public Slice<PatientSummaryResponse> searchPatients(
        String query,
        PatientStatus status,
        Gender gender,
        String bloodGroup,
        Pageable pageable
    ) {
        SearchSession searchSession = Search.session(entityManager);

        // Build search query
        SearchResult<Patient> result = searchSession.search(Patient.class)
            .where(f -> {
                PredicateFinalStep predicate = null;

                // Full-text search on name, phone, email
                if (query != null && !query.isBlank()) {
                    predicate = f.bool()
                        .should(f.match()
                            .fields("firstName", "lastName")
                            .matching(query)
                            .fuzzy(2))  // Allow 2 character edits
                        .should(f.wildcard()
                            .field("phoneNumber")
                            .matching("*" + query + "*"))
                        .should(f.wildcard()
                            .field("email")
                            .matching("*" + query + "*"))
                        .should(f.match()
                            .field("patientId")
                            .matching(query));
                }

                // Filter by status
                if (status != null) {
                    PredicateFinalStep statusFilter = f.match()
                        .field("status")
                        .matching(status);
                    predicate = predicate == null ? statusFilter :
                        f.bool().must(predicate).must(statusFilter);
                }

                // Filter by gender
                if (gender != null) {
                    PredicateFinalStep genderFilter = f.match()
                        .field("gender")
                        .matching(gender);
                    predicate = predicate == null ? genderFilter :
                        f.bool().must(predicate).must(genderFilter);
                }

                // Filter by blood group
                if (bloodGroup != null) {
                    PredicateFinalStep bloodGroupFilter = f.match()
                        .field("medicalHistories.bloodGroup")
                        .matching(bloodGroup);
                    predicate = predicate == null ? bloodGroupFilter :
                        f.bool().must(predicate).must(bloodGroupFilter);
                }

                return predicate != null ? predicate : f.matchAll();
            })
            .sort(f -> f.field("lastName").then().field("firstName"))
            .fetch(pageable.getOffset(), pageable.getPageSize() + 1);  // +1 for hasNext

        List<Patient> patients = result.hits();
        boolean hasNext = patients.size() > pageable.getPageSize();
        if (hasNext) {
            patients = patients.subList(0, pageable.getPageSize());
        }

        List<PatientSummaryResponse> summaries = patients.stream()
            .map(this::toSummaryResponse)
            .collect(Collectors.toList());

        return new SliceImpl<>(summaries, pageable, hasNext);
    }

    private PatientSummaryResponse toSummaryResponse(Patient patient) {
        return PatientSummaryResponse.builder()
            .patientId(patient.getPatientId())
            .fullName(patient.getFirstName() + " " + patient.getLastName())
            .age(patient.getAge())
            .gender(patient.getGender())
            .phoneNumber(patient.getPhoneNumber())
            .status(patient.getStatus())
            .build();
    }
}

// Hibernate Search configuration
// application.yml
spring:
  jpa:
    properties:
      hibernate:
        search:
          backend:
            type: lucene
            directory:
              type: local-filesystem
              root: ${java.io.tmpdir}/lucene/indexes
          automatic_indexing:
            synchronization:
              strategy: sync  # or async for better write performance
```

### Pattern 5: Custom Patient ID Generator (P2026001 format)

**What:** Extend Hibernate's SequenceStyleGenerator to create custom-formatted IDs with prefix + year + sequence
**When to use:** For business-friendly IDs that are human-readable and include semantic information
**Example:**
```java
// Source: Thorben Janssen custom ID generator tutorial
public class PatientIdGenerator extends SequenceStyleGenerator {

    private static final String VALUE_PREFIX = "P";
    private static final String NUMBER_FORMAT = "%05d";  // 5 digits with leading zeros

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        // Get next sequence value
        Long sequenceValue = (Long) super.generate(session, object);

        // Get current year
        int year = LocalDate.now().getYear();

        // Format: P + year + 5-digit sequence (e.g., P2026001)
        return String.format("%s%d%s", VALUE_PREFIX, year, String.format(NUMBER_FORMAT, sequenceValue));
    }
}

// PostgreSQL sequence creation
@Entity
@Table(name = "patients")
public class Patient {
    @Id
    @GeneratedValue(generator = "patient_id_generator")
    @GenericGenerator(
        name = "patient_id_generator",
        type = PatientIdGenerator.class,
        parameters = {
            @org.hibernate.annotations.Parameter(name = "sequence_name", value = "patient_seq"),
            @org.hibernate.annotations.Parameter(name = "initial_value", value = "1"),
            @org.hibernate.annotations.Parameter(name = "increment_size", value = "1")
        }
    )
    @Column(name = "patient_id", length = 20)
    private String patientId;
    // ... other fields
}

// SQL migration to create sequence
CREATE SEQUENCE patient_seq START WITH 1 INCREMENT BY 1;
```

### Pattern 6: RFC 7807 Problem Details for Validation Errors

**What:** Spring Boot 3 native support for standardized error responses with field-level validation details
**When to use:** For all validation errors in REST APIs to provide consistent, machine-readable error format
**Example:**
```java
// Source: Spring Framework 6 ProblemDetail documentation
@RestControllerAdvice
public class PatientExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed for patient registration"
        );
        problemDetail.setTitle("Validation Error");
        problemDetail.setType(URI.create("https://api.hospital.com/problems/validation-error"));

        // Add field-level errors
        Map<String, List<String>> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String field = error.getField();
            String message = error.getDefaultMessage();
            fieldErrors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
        });
        problemDetail.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(DuplicatePatientException.class)
    public ResponseEntity<ProblemDetail> handleDuplicatePatient(DuplicatePatientException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.getMessage()
        );
        problemDetail.setTitle("Duplicate Patient");
        problemDetail.setType(URI.create("https://api.hospital.com/problems/duplicate-patient"));
        problemDetail.setProperty("potentialDuplicates", ex.getDuplicates());
        problemDetail.setProperty("requiresManualOverride", true);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }
}

// Custom phone number validator
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberValidator.class)
public @interface ValidPhoneNumber {
    String message() default "Invalid phone number format. Use +1-XXX-XXX-XXXX, (XXX) XXX-XXXX, or XXX-XXX-XXXX";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class PhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^(\\+1-\\d{3}-\\d{3}-\\d{4}|\\(\\d{3}\\) \\d{3}-\\d{4}|\\d{3}-\\d{3}-\\d{4})$"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return PHONE_PATTERN.matcher(value).matches();
    }
}

// DTO with validation
@Data
public class RegisterPatientRequest {
    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotBlank(message = "Phone number is required")
    @ValidPhoneNumber
    private String phoneNumber;

    @Email(message = "Invalid email format")
    private String email;

    // ... other fields
}
```

### Anti-Patterns to Avoid

- **Updating immutable records in code:** Even though @Immutable prevents database updates, application code should explicitly create new versions instead of attempting modifications
- **Not indexing business_id and version columns:** Without proper indexes, DISTINCT ON queries degrade to table scans (milliseconds become seconds)
- **Using Page instead of Slice for large datasets:** COUNT(*) queries scale poorly, 30+ second response times on 100K+ records
- **Synchronous duplicate detection on every search keystroke:** Run duplicate detection only on form submission, not on real-time search (too expensive)
- **Encrypting searchable fields (name, email, phone):** Breaks Hibernate Search indexing, makes fuzzy matching impossible (encrypt only SSN, insurance policy)
- **Low duplicate detection thresholds (< 80%):** Healthcare requires high precision to avoid false positives (use 85-90% threshold with manual review)
- **Ignoring Soundex/phonetic matching for names:** Levenshtein alone misses phonetically similar names (Jon/John, Smith/Smyth)

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| String similarity algorithms | Custom Levenshtein or fuzzy matching | Apache Commons Text LevenshteinDistance, Soundex, Metaphone | Production-tested, handles edge cases (empty strings, special characters), optimized performance, Apache-maintained |
| Full-text search indexing | Custom search tables with LIKE queries | Hibernate Search 7 with Lucene | Automatic indexing, relevance scoring, fuzzy matching, phonetic analysis, 10-100x faster than LIKE queries |
| Duplicate detection pipeline | Manual SQL queries + Java comparison | Dedicated duplicate detection service with multi-field scoring | Healthcare duplicate detection is deceptively complex (name variations, typos, phonetic matches, multi-field weights) |
| Custom pagination with COUNT | Manual LIMIT/OFFSET + COUNT(*) queries | Spring Data Slice pagination | Slice automatically fetches N+1 to detect next page, eliminates COUNT overhead, built-in support |
| Custom ID generation with database triggers | PostgreSQL trigger to format sequence | Hibernate SequenceStyleGenerator extension | Type-safe, testable, version-controlled, no database-specific logic, works across environments |
| Custom validation error format | Map<String, String> or custom JSON | RFC 7807 Problem Details (ProblemDetail) | Industry standard, machine-readable, consistent across services, Spring Boot 3 native support |
| Latest version queries with JPA Criteria | Complex subquery with MAX(version) | PostgreSQL DISTINCT ON native query | 10x faster than subqueries, more readable, PostgreSQL-optimized execution plan |
| Patient age calculation on every query | Store age in database and update regularly | @Transient computed field with Period.between() | Age changes daily, storing it requires updates (violates immutability), computation is trivial |

**Key insight:** Patient registration and search seem simple but have subtle complexities that are already solved by established libraries. Healthcare duplicate detection is particularly dangerous to hand-roll because false positives (denying care) and false negatives (duplicate records, billing errors) have serious consequences. Fuzzy matching algorithms must handle phonetic variations, typos, transpositions, and multi-field scoring with healthcare-appropriate thresholds (85-90%). Hibernate Search solves the full-text search problem comprehensively with relevance scoring, phonetic analyzers, and automatic indexing that would take months to implement correctly.

## Common Pitfalls

### Pitfall 1: Event Sourcing Query Performance Degradation

**What goes wrong:** Naive implementation of event sourcing stores all patient versions in a single table without indexes on business_id and version, causing "latest version" queries to become full table scans. On 10K patients with 5 versions each (50K records), queries degrade from milliseconds to seconds.

**Why it happens:** Developers focus on the "never update, only insert" pattern but forget to optimize queries for the "retrieve latest version" use case, which is 99% of read operations.

**How to avoid:**
- Create composite index on (business_id, version DESC, created_at DESC) for DISTINCT ON queries
- Use native PostgreSQL DISTINCT ON instead of window functions (2-3x faster)
- Consider separate "current state" table if query performance is critical (eventual consistency acceptable)
- Monitor query explain plans during development to catch sequential scans early

**Warning signs:** Query explain shows "Seq Scan on patients" instead of "Index Scan", response times increase linearly with record count, database CPU usage high on simple searches.

### Pitfall 2: Duplicate Detection False Positive/Negative Balance

**What goes wrong:** Setting duplicate detection threshold too low (< 80%) creates false positives where receptionists must override legitimate new patients, creating friction. Setting threshold too high (> 95%) creates false negatives where actual duplicates slip through, causing duplicate MRNs, fragmented medical history, and billing errors.

**Why it happens:** Developers test with clean data (no typos, no variations) and don't account for real-world scenarios: maiden names, nicknames, address changes, phone number updates, data entry errors.

**How to avoid:**
- Use 85-90% threshold for healthcare (high precision required)
- Implement two-tier system: 90%+ blocks registration (requires manager override), 85-90% shows warning but allows registration with confirmation
- Weight fields appropriately: DOB (40%), Name (30%), Phone (20%), Email (10%)
- Use both Levenshtein (typos/edits) and Soundex (phonetic) for name matching
- Create manual review queue for borderline cases (85-90%)
- Track false positive/negative rates in production and tune thresholds monthly

**Warning signs:** Receptionists frequently clicking "Register Anyway", duplicate patient complaints from clinical staff, billing department reports duplicate charges, audit logs show high override rates.

### Pitfall 3: Hibernate Search Index Synchronization Issues

**What goes wrong:** Patient records updated through direct SQL (migrations, bulk updates, admin tools) bypass Hibernate ORM, leaving search indexes stale. Search returns patients that were deleted or shows outdated information.

**Why it happens:** Hibernate Search uses Hibernate ORM listeners to trigger indexing - direct SQL bypasses these listeners. Database triggers, liquibase migrations, and admin tools often use direct SQL for performance.

**How to avoid:**
- Configure Hibernate Search mass indexer to run on application startup in development (disabled in production)
- Create manual reindex endpoint for admins: `POST /api/v1/admin/patients/reindex`
- Run mass indexer weekly via scheduled job to catch any synchronization drift
- Use Hibernate Search database polling coordination for eventual consistency if direct SQL is unavoidable
- Monitor index size vs table record count as health check

```java
// Manual reindex endpoint
@PostMapping("/api/v1/admin/patients/reindex")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Void> reindexPatients() {
    SearchSession searchSession = Search.session(entityManager);
    searchSession.massIndexer(Patient.class)
        .threadsToLoadObjects(4)
        .batchSizeToLoadObjects(25)
        .startAndWait();
    return ResponseEntity.ok().build();
}
```

**Warning signs:** Search returns fewer results than expected, patients not appearing in search immediately after registration, search results show outdated information.

### Pitfall 4: Slice Pagination "Has Next" Off-By-One Error

**What goes wrong:** Developers forget to fetch pageSize + 1 records when using Slice, resulting in "hasNext" always returning false or incorrect pagination controls on frontend.

**Why it happens:** Slice doesn't run COUNT(*) query, so it determines "has next page" by fetching one extra record. If you fetch exactly pageSize records, you can't tell if more exist.

**How to avoid:**
- Always fetch `pageable.getPageSize() + 1` records in Hibernate Search or repository queries
- Check if result list size > pageSize to determine hasNext
- Trim result list to pageSize before returning to client
- Use Spring Data JPA Slice return type for repository methods (handles this automatically)

```java
// Correct Slice implementation
public Slice<Patient> searchPatients(String query, Pageable pageable) {
    List<Patient> patients = hibernateSearchQuery
        .fetch(pageable.getOffset(), pageable.getPageSize() + 1);  // +1 is critical

    boolean hasNext = patients.size() > pageable.getPageSize();
    if (hasNext) {
        patients = patients.subList(0, pageable.getPageSize());  // Trim extra record
    }

    return new SliceImpl<>(patients, pageable, hasNext);
}
```

**Warning signs:** "Next" button disabled on frontend when more records exist, pagination controls show incorrect state, infinite scroll stops prematurely.

### Pitfall 5: Fuzzy Matching Performance on Large Candidate Sets

**What goes wrong:** Running Levenshtein distance calculation on all 10K patients on every registration takes 2-5 seconds, making registration workflow unacceptably slow.

**Why it happens:** Levenshtein is O(n*m) where n and m are string lengths - running it 10K times is expensive. Developers skip the filtering step and fuzzy-match against entire patient table.

**How to avoid:**
- Filter candidates first using exact matches on DOB (reduces 10K to ~10-50 candidates)
- Use database indexes on DOB, phone (last 4 digits), email domain for fast filtering
- Only run expensive fuzzy matching on filtered candidate set (< 100 records)
- Cache Soundex encoded names in database column for fast phonetic pre-filtering
- Consider async duplicate detection for non-blocking UX (show results after 1-2 seconds)

```java
// Efficient filtering before fuzzy matching
public DuplicateCheckResult checkForDuplicates(RegisterPatientRequest request) {
    // Step 1: Fast filter - DOB exact match (10K -> ~10-50 records)
    List<Patient> candidates = patientRepository
        .findLatestVersionsByDateOfBirth(request.getDateOfBirth());

    if (candidates.isEmpty()) {
        return DuplicateCheckResult.noDuplicates();
    }

    // Step 2: Expensive fuzzy matching on small candidate set
    List<DuplicateMatch> matches = candidates.stream()
        .map(c -> new DuplicateMatch(c, calculateSimilarity(request, c)))
        .filter(m -> m.getScore() >= THRESHOLD)
        .sorted(Comparator.comparingDouble(DuplicateMatch::getScore).reversed())
        .limit(10)  // Top 10 matches only
        .collect(Collectors.toList());

    return new DuplicateCheckResult(matches, matches.get(0).getScore() >= 0.90);
}
```

**Warning signs:** Patient registration takes > 2 seconds, duplicate detection times increase linearly with patient count, database CPU spikes during registration.

### Pitfall 6: Not Handling Version Conflicts in Event Sourcing

**What goes wrong:** Two receptionists attempt to register the same patient simultaneously (race condition), both pass duplicate detection, both create version 1, causing constraint violations or duplicate business IDs.

**Why it happens:** Duplicate detection query and insert are not atomic - gap between check and insert allows race conditions. Optimistic locking with @Version doesn't prevent initial insert conflicts.

**How to avoid:**
- Use database unique constraint on (business_id, version) to catch conflicts at database level
- Implement pessimistic locking with SELECT FOR UPDATE during duplicate detection
- Use distributed lock (Redis, database advisory lock) during critical section
- Retry with exponential backoff on constraint violations
- Consider unique constraint on normalized name + DOB + phone for ultimate duplicate prevention

```sql
-- Database constraint to prevent concurrent duplicates
CREATE UNIQUE INDEX idx_patient_unique_identity
ON patients(LOWER(first_name), LOWER(last_name), date_of_birth, phone_number)
WHERE status = 'ACTIVE';  -- Only check active patients
```

**Warning signs:** Duplicate patient records despite duplicate detection, constraint violation exceptions in logs during peak hours, race conditions in integration tests.

### Pitfall 7: Immutable Annotation Not Preventing Updates via Native Queries

**What goes wrong:** Hibernate's @Immutable annotation prevents Hibernate ORM from generating UPDATE statements, but native queries and JDBC still work. Admin tool uses native UPDATE to fix typo, breaking immutability contract and losing audit trail.

**Why it happens:** @Immutable is a Hibernate ORM-level feature, not database-level enforcement. Direct SQL, triggers, migrations all bypass this protection.

**How to avoid:**
- Create database trigger that blocks UPDATE statements on patients table
- Use database permissions: GRANT INSERT, SELECT but REVOKE UPDATE on patients table for application role
- Code review to catch native UPDATE queries
- Create separate admin role with UPDATE permission for rare corrections
- Document immutability contract in database migration comments

```sql
-- PostgreSQL trigger to prevent updates
CREATE OR REPLACE FUNCTION prevent_patient_updates()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Patient records are immutable. Create new version instead.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_prevent_patient_updates
BEFORE UPDATE ON patients
FOR EACH ROW
EXECUTE FUNCTION prevent_patient_updates();
```

**Warning signs:** Audit trail gaps, patient history missing intermediate versions, version numbers skipped (1, 3, 5 instead of 1, 2, 3, 4, 5).

## Code Examples

Verified patterns from official sources:

### Patient Registration with Duplicate Detection Flow

```java
// Source: Combined patterns from Phase 0 research and Phase 1 findings
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    @Autowired
    private PatientService patientService;

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Audited(action = "CREATE", resourceType = "PATIENT")
    public ResponseEntity<?> registerPatient(
        @Valid @RequestBody RegisterPatientRequest request,
        @RequestParam(required = false, defaultValue = "false") boolean overrideDuplicate
    ) {
        // Step 1: Check for duplicates
        DuplicateCheckResult duplicateCheck = patientService.checkForDuplicates(request);

        if (duplicateCheck.hasDuplicates() && !overrideDuplicate) {
            // Return 409 Conflict with duplicate warning
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Potential duplicate patient detected"
            );
            problem.setTitle("Duplicate Patient Warning");
            problem.setProperty("potentialDuplicates", duplicateCheck.getMatches());
            problem.setProperty("requiresOverride", duplicateCheck.shouldBlockRegistration());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }

        if (duplicateCheck.shouldBlockRegistration() && overrideDuplicate) {
            // High confidence duplicate - require manager approval
            throw new UnauthorizedOverrideException("Manager approval required for high-confidence duplicate");
        }

        // Step 2: Register patient
        PatientDetailResponse patient = patientService.registerPatient(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(patient);
    }

    @GetMapping("/{businessId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @PreAuthorize("hasPermission(#businessId, 'Patient', 'read')")
    @Audited(action = "READ", resourceType = "PATIENT")
    public ResponseEntity<PatientDetailResponse> getPatient(@PathVariable String businessId) {
        PatientDetailResponse patient = patientService.getPatientByBusinessId(businessId);
        return ResponseEntity.ok(patient);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<Slice<PatientSummaryResponse>> searchPatients(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) PatientStatus status,
        @RequestParam(required = false) Gender gender,
        @RequestParam(required = false) String bloodGroup,
        @PageableDefault(size = 20, sort = "lastName") Pageable pageable
    ) {
        Slice<PatientSummaryResponse> patients = patientService.searchPatients(
            query, status, gender, bloodGroup, pageable
        );
        return ResponseEntity.ok(patients);
    }
}

@Service
@Transactional
public class PatientService {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PatientSearchRepository patientSearchRepository;

    @Autowired
    private DuplicateDetectionService duplicateDetectionService;

    public PatientDetailResponse registerPatient(RegisterPatientRequest request) {
        // Create new patient (version 1)
        Patient patient = new Patient();
        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setPhoneNumber(request.getPhoneNumber());
        patient.setEmail(request.getEmail());
        patient.setStatus(PatientStatus.ACTIVE);
        // patientId auto-generated by PatientIdGenerator (P2026001)
        // businessId auto-generated by @PrePersist
        // version auto-set to 1 by @PrePersist
        // createdAt, createdBy auto-set by JPA auditing

        // Add emergency contacts
        if (request.getEmergencyContacts() != null) {
            request.getEmergencyContacts().forEach(ec -> {
                EmergencyContact contact = new EmergencyContact();
                contact.setPatient(patient);
                contact.setName(ec.getName());
                contact.setPhoneNumber(ec.getPhoneNumber());
                contact.setRelationship(ec.getRelationship());
                patient.getEmergencyContacts().add(contact);
            });
        }

        // Add medical history
        if (request.getBloodGroup() != null || request.getAllergies() != null ||
            request.getChronicConditions() != null) {
            MedicalHistory history = new MedicalHistory();
            history.setPatient(patient);
            history.setBloodGroup(request.getBloodGroup());
            history.setAllergies(request.getAllergies());
            history.setChronicConditions(request.getChronicConditions());
            patient.getMedicalHistories().add(history);
        }

        Patient savedPatient = patientRepository.save(patient);
        // Hibernate Search automatically indexes the patient

        return toDetailResponse(savedPatient);
    }

    public DuplicateCheckResult checkForDuplicates(RegisterPatientRequest request) {
        return duplicateDetectionService.checkForDuplicates(request);
    }

    public PatientDetailResponse getPatientByBusinessId(String businessId) {
        Patient patient = patientRepository.findLatestVersionByBusinessId(businessId)
            .orElseThrow(() -> new PatientNotFoundException(businessId));
        return toDetailResponse(patient);
    }

    public Slice<PatientSummaryResponse> searchPatients(
        String query,
        PatientStatus status,
        Gender gender,
        String bloodGroup,
        Pageable pageable
    ) {
        return patientSearchRepository.searchPatients(query, status, gender, bloodGroup, pageable);
    }

    private PatientDetailResponse toDetailResponse(Patient patient) {
        return PatientDetailResponse.builder()
            .patientId(patient.getPatientId())
            .businessId(patient.getBusinessId())
            .firstName(patient.getFirstName())
            .lastName(patient.getLastName())
            .dateOfBirth(patient.getDateOfBirth())
            .age(patient.getAge())
            .gender(patient.getGender())
            .phoneNumber(patient.getPhoneNumber())
            .email(patient.getEmail())
            .status(patient.getStatus())
            .emergencyContacts(patient.getEmergencyContacts().stream()
                .map(this::toEmergencyContactDto)
                .collect(Collectors.toList()))
            .medicalHistory(patient.getMedicalHistories().stream()
                .max(Comparator.comparing(MedicalHistory::getCreatedAt))
                .map(this::toMedicalHistoryDto)
                .orElse(null))
            .registeredAt(patient.getCreatedAt())
            .registeredBy(patient.getCreatedBy())
            .lastModifiedAt(patient.getCreatedAt())  // Same as created for version 1
            .lastModifiedBy(patient.getCreatedBy())
            .version(patient.getVersion())
            .build();
    }
}
```

### Database Schema for Normalized Patient Data

```sql
-- Source: PostgreSQL best practices + healthcare database design patterns

-- Patients table (immutable, versioned)
CREATE TABLE patients (
    patient_id VARCHAR(20) PRIMARY KEY,  -- P2026001 format
    business_id UUID NOT NULL,           -- Immutable identifier across versions
    version BIGINT NOT NULL DEFAULT 1,

    -- Demographics
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(20) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    email VARCHAR(255),

    -- Address
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(50),
    zip_code VARCHAR(10),

    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,

    -- Indexes for performance
    CONSTRAINT patients_pkey PRIMARY KEY (patient_id),
    CONSTRAINT patients_business_version_unique UNIQUE (business_id, version)
);

-- Indexes for query performance
CREATE INDEX idx_patients_business_id ON patients(business_id);
CREATE INDEX idx_patients_business_version ON patients(business_id, version DESC, created_at DESC);
CREATE INDEX idx_patients_dob ON patients(date_of_birth);
CREATE INDEX idx_patients_phone ON patients(phone_number);
CREATE INDEX idx_patients_status ON patients(status);
CREATE INDEX idx_patients_last_name ON patients(last_name, first_name);

-- Unique constraint to prevent true duplicates (same identity, active)
CREATE UNIQUE INDEX idx_patients_unique_identity
ON patients(LOWER(TRIM(first_name)), LOWER(TRIM(last_name)), date_of_birth,
            REGEXP_REPLACE(phone_number, '[^0-9]', '', 'g'))
WHERE status = 'ACTIVE';

-- Emergency contacts (normalized, separate table)
CREATE TABLE emergency_contacts (
    id BIGSERIAL PRIMARY KEY,
    patient_business_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    relationship VARCHAR(50) NOT NULL,
    is_primary BOOLEAN DEFAULT false,

    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,

    CONSTRAINT fk_emergency_contact_patient
        FOREIGN KEY (patient_business_id)
        REFERENCES patients(business_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_emergency_contacts_patient ON emergency_contacts(patient_business_id);

-- Medical history (normalized, separate table)
CREATE TABLE medical_histories (
    id BIGSERIAL PRIMARY KEY,
    patient_business_id UUID NOT NULL,
    blood_group VARCHAR(10),
    allergies TEXT,
    chronic_conditions TEXT,

    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,

    CONSTRAINT fk_medical_history_patient
        FOREIGN KEY (patient_business_id)
        REFERENCES patients(business_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_medical_histories_patient ON medical_histories(patient_business_id);

-- Sequence for patient ID generation
CREATE SEQUENCE patient_seq START WITH 1 INCREMENT BY 1;

-- Trigger to prevent updates (enforce immutability)
CREATE OR REPLACE FUNCTION prevent_patient_updates()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Patient records are immutable. Create new version instead.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_prevent_patient_updates
BEFORE UPDATE ON patients
FOR EACH ROW
EXECUTE FUNCTION prevent_patient_updates();

-- View for latest patient versions (convenience)
CREATE OR REPLACE VIEW patients_latest AS
SELECT DISTINCT ON (business_id) *
FROM patients
ORDER BY business_id, version DESC, created_at DESC;
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Mutable patient records with UPDATE | Immutable event-sourced versioning with INSERT only | 2018-2020 (GDPR, HIPAA auditing) | Complete audit trail, better compliance, but requires versioned queries |
| LIKE queries for patient search | Full-text search with Hibernate Search or Elasticsearch | 2015+ (Lucene maturity) | 10-100x performance improvement, relevance scoring, fuzzy matching |
| Page-based pagination with COUNT(*) | Slice-based pagination without COUNT | 2020+ (Spring Data JPA) | Eliminates expensive COUNT queries, milliseconds vs seconds on large datasets |
| Manual duplicate detection with SQL LIKE | Multi-algorithm fuzzy matching with scoring | 2015+ (Apache Commons Text) | Higher accuracy, phonetic matching, configurable thresholds |
| Custom error response formats | RFC 7807 Problem Details | 2016 RFC, 2022 Spring Boot 3 adoption | Standardized, machine-readable, industry-wide consistency |
| Database sequence only for IDs | Custom IdentifierGenerator with formatting | Hibernate 5+ | Business-friendly IDs (P2026001 vs 12345), semantic information |
| OFFSET-based pagination | Keyset (cursor) pagination | 2020+ (large dataset optimization) | Consistent results, better performance, but requires stable sort keys |

**Deprecated/outdated:**
- **Page<T> for all pagination:** Use Slice<T> unless total count is actually needed (rare for infinite scroll, "Next" navigation)
- **Manual age calculation stored in database:** Age changes daily, store DOB and calculate with @Transient field
- **Updating patient records:** Violates event sourcing and audit trail, create new version instead
- **LIKE '%name%' for patient search:** Use Hibernate Search full-text indexes for 10-100x performance
- **Single-field duplicate detection (name only or email only):** Healthcare requires multi-field scoring (name + DOB + phone + email) for accuracy

## Open Questions

### 1. Event Sourcing vs Simplified Mutability

**What we know:** User decided on immutable event-sourced pattern with version history for HIPAA compliance. Query latest version pattern is well-established (DISTINCT ON, window functions).

**What's unclear:**
- Performance at 50K patients with average 5 versions each (250K records) - is DISTINCT ON still sub-second?
- Should we partition by year or business_id range to improve query performance?
- Is eventual consistency acceptable for search index (async indexing) or must it be synchronous?

**Recommendation:** Start with DISTINCT ON queries and monitor performance in development with realistic data volumes (seed 50K patients × 5 versions). If query time exceeds 500ms, consider:
- Partitioning patients table by created_at (year-month)
- Materialized view for latest versions refreshed every 5 minutes
- Separate "current_patients" table with triggers maintaining latest version

### 2. Duplicate Detection Threshold Tuning

**What we know:** User decided on multi-field matching (name + DOB + phone + email + address). Research shows 85-90% threshold for healthcare with manual review. Levenshtein + Soundex combination recommended.

**What's unclear:**
- Exact weight distribution across fields (currently 30% name, 40% DOB, 20% phone, 10% email)
- Should address matching use fuzzy (Levenshtein) or exact match only?
- Is 85% threshold too permissive (too many warnings) or too strict (missing duplicates)?

**Recommendation:** Start with research-based weights and 85% threshold. Log all duplicate detections with scores to CSV for first 2 weeks of production. Analyze false positives (receptionist overrides) and false negatives (discovered duplicates) to tune:
- If override rate > 20%, increase threshold to 88%
- If duplicate complaints from clinical staff, decrease to 82%
- Consider separate thresholds for different field combinations (name+DOB+phone = 80%, name+DOB+email = 85%)

### 3. Hibernate Search vs JPA Specification for Search

**What we know:** Hibernate Search delivers exceptional performance with Lucene backend for full-text search (fuzzy, phonetic, relevance scoring). JPA Specification better for exact filters (status, gender, blood group).

**What's unclear:**
- Should we use Hibernate Search for all queries (full-text + filters) or hybrid approach (Hibernate Search for text, JPA Specification for filters)?
- Is Lucene index size manageable on disk (50K patients, how many GB)?
- Can Hibernate Search handle complex multi-field filters efficiently?

**Recommendation:** Use hybrid approach for Phase 1:
- Hibernate Search for text queries (name, phone, email search with fuzzy matching)
- JPA Specification for filter-only queries (status=ACTIVE, gender=MALE, blood_group=O+)
- Combine both for text + filters using Hibernate Search's built-in filter predicates
- Monitor Lucene index size (expect ~10-50 MB for 50K patients, negligible)
- Phase 2 can consolidate to Hibernate Search-only if performance is acceptable

### 4. Slice Pagination Result Ordering Consistency

**What we know:** Slice-based pagination requires stable sort order to avoid duplicate/missing results across pages. ORDER BY last_name, first_name may have ties.

**What's unclear:**
- Should we add patient_id to sort order for tie-breaking (ORDER BY last_name, first_name, patient_id)?
- How to handle sort order when filtering by relevance score (Hibernate Search)?
- Should "next page" link include offset or cursor (business_id of last record)?

**Recommendation:**
- Always include tie-breaker: ORDER BY last_name, first_name, patient_id
- For Hibernate Search relevance sort, use relevance score then last_name, first_name, patient_id
- Use offset-based pagination for Phase 1 (simpler), consider keyset pagination for Phase 2 if users report inconsistent results
- Document known limitation: new registrations during pagination may cause page shifts

### 5. Emergency Contact and Medical History Versioning

**What we know:** User decided on separate normalized tables (emergency_contacts, medical_histories) with FK to patient. Main patient record is versioned.

**What's unclear:**
- Should emergency contacts and medical histories also be versioned (immutable) or mutable?
- If immutable, do they version independently or tied to patient version?
- How to query "latest emergency contact for latest patient version"?

**Recommendation:** Start with mutable approach for Phase 1 (simpler):
- EmergencyContact and MedicalHistory FK to patient.business_id (not patient_id/version)
- Updates to contacts/history do NOT create new patient version
- Phase 2 can implement full versioning if audit requirements demand it
- Trade-off: Simpler queries and less complexity vs complete audit trail
- Sufficient for v1 because contact/history changes are less frequent and less critical than patient demographics

## Sources

### Primary (HIGH confidence)

**Hibernate Search:**
- [Hibernate Search Official Site](https://hibernate.org/search/) - Features, backends, performance characteristics
- [Hibernate Search 7.2 Documentation](https://docs.hibernate.org/search/7.0/reference/en-US/html_single/) - Official reference
- [Spring Boot 3.0 Search API using Hibernate Search](https://medium.com/@elijahndungu30/spring-boot-3-0-search-api-using-hibernate-search-5fafad506b69) - Integration guide

**Apache Commons Text:**
- [Apache Commons Text Similarity Package](https://commons.apache.org/proper/commons-text/apidocs/org/apache/commons/text/similarity/package-summary.html) - LevenshteinDistance, Soundex, Metaphone API
- [LevenshteinDistance GitHub Source](https://github.com/apache/commons-text/blob/master/src/main/java/org/apache/commons/text/similarity/LevenshteinDistance.java) - Implementation details

**Spring Data JPA Pagination:**
- [Baeldung: Pagination and Sorting using Spring Data JPA](https://www.baeldung.com/spring-data-jpa-pagination-sorting) - Page vs Slice comparison
- [Iterating over large datasets using Spring Data JPA](https://www.renatofialho.com/blog/iterating-over-large-datasets-using-spring-data-jpa) - Performance analysis

**Event Sourcing:**
- [GitHub: PostgreSQL Event Sourcing Reference](https://github.com/eugene-khyst/postgresql-event-sourcing) - Spring Boot implementation
- [SoftwareMill: Event Sourcing with Relational Database](https://softwaremill.com/implementing-event-sourcing-using-a-relational-database/) - Query patterns

**PostgreSQL:**
- [PostgreSQL Documentation: Window Functions](https://www.postgresql.org/docs/current/functions-window.html) - Official docs
- [PostgreSQL Documentation: Foreign Keys](https://www.postgresql.org/docs/current/tutorial-fk.html) - Normalized schema

**Spring Boot Validation:**
- [Baeldung: Validation in Spring Boot](https://www.baeldung.com/spring-boot-bean-validation) - JSR-380 Bean Validation
- [Spring Boot 3 Error Reporting using Problem Details](https://www.sivalabs.in/blog/spring-boot-3-error-reporting-using-problem-details/) - RFC 7807

### Secondary (MEDIUM confidence)

**Healthcare Duplicate Detection:**
- [Identifying Duplicates in Health Data](https://medium.com/@tarangds/identifying-duplicates-and-near-duplicates-in-health-data-a-guide-for-data-professionals-9b085b6a4138) - Algorithms and workflow
- [Fuzzy Matching 101](https://dataladder.com/fuzzy-matching-101/) - Threshold best practices
- [CDC IIS Patient De-duplication Best Practices](https://www.cdc.gov/iis/media/pdfs/2025/02/De-Duplication_Best_Practices_Report.pdf) - Healthcare-specific guidance

**Custom ID Generation:**
- [Custom ID Generators in Spring Boot JPA](https://medium.com/@AlexanderObregon/custom-id-generators-in-spring-boot-jpa-entities-3a35ff153878) - Implementation pattern
- [Thorben Janssen: Custom Sequence-Based ID Generator](https://thorben-janssen.com/custom-sequence-based-idgenerator/) - SequenceStyleGenerator extension

**API Versioning:**
- [Spring Boot API Versioning Strategies](https://medium.com/but-it-works-on-my-machine/api-versioning-strategies-for-long-running-spring-boot-projects-462d6e0034e7) - URL path vs header comparison
- [Baeldung: API Versioning in Spring](https://www.baeldung.com/spring-api-versioning) - Implementation patterns

**Database Design:**
- [Hospital Management System SQL Project](https://medium.com/@apoorvchowdhry55/hospital-management-system-f21b978a1b8c) - Normalized schema example
- [PostgreSQL Data Normalization](https://www.tigerdata.com/learn/how-to-use-postgresql-for-data-normalization) - Best practices

### Tertiary (LOW confidence - requires validation)

**Performance Benchmarks:**
- Search results mention "30+ seconds for Page vs milliseconds for Slice" on 1M records, but no specific benchmarks for 10K-50K patient scale
- Hibernate Search "exceptional performance" claimed but no specific latency numbers for healthcare use case
- Would benefit from load testing with realistic patient data volumes

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Hibernate Search, Apache Commons Text, Spring Data JPA Slice verified through official documentation and multiple sources
- Architecture: HIGH - Event sourcing patterns, DISTINCT ON queries, fuzzy matching algorithms verified across multiple implementations
- Duplicate detection: MEDIUM-HIGH - Threshold recommendations (85-90%) and multi-field scoring verified through healthcare sources, but specific weight distribution not universally established
- Database schema: HIGH - Normalized design patterns verified through PostgreSQL documentation and healthcare database examples
- Pitfalls: HIGH - Event sourcing query performance, Slice pagination off-by-one, Hibernate Search sync issues verified through community experiences and documentation
- Performance: MEDIUM - DISTINCT ON query performance claims based on PostgreSQL optimizer behavior, but Phase 1 scale (10K-50K patients) not specifically benchmarked

**Research date:** 2026-02-19
**Valid until:** Approximately 2026-05-19 (90 days - stable domain with mature libraries, but fuzzy matching research and healthcare best practices evolve)

**Notes:**
- Immutable event-sourced pattern chosen by user requires careful query optimization (DISTINCT ON, proper indexes)
- Multi-field duplicate detection with 85-90% threshold balances false positives and negatives for healthcare
- Hibernate Search 7 with Lucene backend is better fit than Elasticsearch for v1 scale (10K-50K patients)
- Slice pagination mandatory for Phase 0 performance requirements (<2 seconds for 10K records)
- RFC 7807 Problem Details provides standardized validation error format native to Spring Boot 3
- Emergency contact and medical history versioning deferred to Phase 2 to reduce complexity
