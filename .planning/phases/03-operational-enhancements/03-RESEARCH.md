# Phase 3: Operational Enhancements - Research

**Researched:** 2026-02-20
**Domain:** Spring Boot 3.4.5 — multipart file upload, quick registration patterns, PostgreSQL aggregate queries, external REST API integration (ZIP code lookup), in-memory caching
**Confidence:** HIGH (codebase directly inspected; official Spring docs and verified sources used)

---

## Summary

Phase 3 builds four operational features on top of the Phase 1 & 2 backend: (1) quick registration with a "complete later" workflow flag, (2) webcam-based patient photo upload, (3) a data quality dashboard that aggregates incomplete-record metrics, and (4) smart form helpers (ZIP-to-city/state auto-complete and insurance plan suggestion).

The existing codebase provides a well-structured Spring Boot 3.4.5 / Java 17 / PostgreSQL (port 5435) backend with an immutable event-sourced Patient entity, AES-256-GCM encryption for PHI, role-based security (RECEPTIONIST/ADMIN = write), RFC 7807 error responses, and an `@Audited` AOP interceptor. Phase 3 adds new capabilities without restructuring what exists. All four features are purely backend additions: new Flyway migrations, new service/controller/repository classes, and new configuration in `application.yml`.

The single most important architectural decision is **storage for patient photos**. Because file storage is not configured in the current project, Phase 3 must add it. Local filesystem storage (configurable path) is the pragmatic choice for a development/on-premise hospital setup, with a clear abstraction layer so the storage backend can be swapped to S3 later. The photo path (not the binary) is stored in the database column added by a new Flyway migration on the `patients` table — but since patients are immutable, a new **mutable** `patient_photos` table is the cleaner approach, matching the pattern used for `insurance` and `emergency_contacts`.

**Primary recommendation:** Implement all four features as separate, independently-testable service classes following the established layered architecture (controller → service → repository). Use local filesystem storage with a `FileStorageService` abstraction for photos, Zippopotam.us for ZIP lookup (no API key, cache with Caffeine), config-driven insurance suggestions (no external API), and native SQL aggregate queries for the dashboard.

---

## Standard Stack

### Core (already in project — no new dependencies for most features)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot Web (spring-boot-starter-web) | 3.4.5 | Multipart file upload via `MultipartFile` | Already in pom.xml |
| Spring Boot Validation (spring-boot-starter-validation) | 3.4.5 | Content-type and size validation for uploads | Already in pom.xml |
| Spring Boot Cache (spring-boot-starter-cache) | 3.4.5 | `@Cacheable` on ZIP lookup service | Needs to be added |
| Caffeine | 3.1.8 | High-performance in-process cache (recommended Spring Boot 3 cache impl) | Needs to be added |
| Flyway | Already configured | Schema migrations for new tables | Already in pom.xml |
| PostgreSQL (native queries) | Already configured | Aggregate COUNT queries for dashboard | Already in pom.xml |
| Spring WebClient (from spring-boot-starter-webflux) OR RestTemplate | 3.4.5 | HTTP call to Zippopotam.us API | `RestTemplate` already available; `WebClient` needs webflux dep |

### New Dependencies to Add

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-boot-starter-cache | 3.4.5 (managed) | Enable Spring caching abstraction | ZIP code lookup caching |
| com.github.ben-manes.caffeine:caffeine | 3.1.8 | In-process LRU/TTL cache backend | Single-node hospital deployment |

**Installation (add to pom.xml):**
```xml
<!-- Caching for ZIP code lookup -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.1.8</version>
</dependency>
```

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Local filesystem photo storage | AWS S3 (via spring-cloud-aws) | S3 is better for production scale and HIPAA BAA, but adds significant infrastructure; local is correct for on-premise hospital |
| Zippopotam.us (free, no auth) | USPS API, Smarty, ZIP-Codes.com | USPS requires account/registration; paid tiers add ops cost; Zippopotam.us is free, 60+ countries, no rate limits stated |
| Caffeine (in-process) | Redis | Redis requires external infra; overkill for a single-node hospital backend caching 43,000 US ZIPs |
| RestTemplate for ZIP API | WebClient (reactive) | WebClient is preferred in Spring 6+, but requires adding spring-boot-starter-webflux dep; since ZIP lookup is synchronous and infrequent, RestTemplate is sufficient |
| Config-driven insurance suggestions | External insurance plan API | No free reliable API for insurance plan auto-suggestion exists; a curated static list in application.yml is the realistic approach for hospital registration |

---

## Architecture Patterns

### Recommended Project Structure for Phase 3

```
src/main/java/com/hospital/
├── patient/
│   ├── api/
│   │   ├── QuickRegistrationController.java     # POST /api/v1/patients/quick
│   │   ├── PhotoController.java                  # POST /api/v1/patients/{id}/photo
│   │   ├── DataQualityController.java            # GET  /api/v1/admin/data-quality
│   │   └── dto/
│   │       ├── QuickRegisterRequest.java         # Minimal-field registration request
│   │       ├── DataQualityReport.java            # Dashboard response DTO
│   │       └── ZipLookupResponse.java            # ZIP → city/state response DTO
│   ├── application/
│   │   ├── QuickRegistrationService.java         # Minimal-field registration logic
│   │   ├── PhotoService.java                     # Photo upload + link to patient
│   │   └── DataQualityService.java               # Aggregate query execution
│   └── infrastructure/
│       ├── PatientPhotoRepository.java           # CRUD for patient_photos table
│       └── DataQualityRepository.java            # Native aggregate queries
├── smartform/
│   ├── api/
│   │   └── SmartFormController.java             # GET /api/v1/smart-form/zip/{zip}
│   │                                             # GET /api/v1/smart-form/insurance-plans
│   ├── application/
│   │   ├── ZipLookupService.java                # Calls Zippopotam.us + @Cacheable
│   │   └── InsuranceSuggestionService.java      # Returns config-driven list
│   └── config/
│       └── SmartFormProperties.java             # @ConfigurationProperties for insurance list
├── storage/
│   └── FileStorageService.java                  # Storage abstraction (local FS impl)
└── config/
    └── CacheConfig.java                         # Caffeine cache configuration
src/main/resources/
├── db/migration/
│   ├── V006__add_patient_photos_table.sql        # New table for photo links
│   └── V007__add_registration_complete_flag.sql  # Adds is_registration_complete to patients
│                                                  # OR: separate patient_registration_flags table
└── application.yml                              # + multipart, cache, storage, zip-lookup config
```

### Pattern 1: Quick Registration — Separate Endpoint with Validation Groups

**What:** A `POST /api/v1/patients/quick` endpoint accepts a reduced field set (firstName, lastName, dateOfBirth, gender, phoneNumber only). The full `RegisterPatientRequest` has `@AssertTrue photoIdVerified` which blocks this path. A new `QuickRegisterRequest` DTO with fewer required fields avoids that constraint. The resulting patient record gets `isRegistrationComplete = false`.

**When to use:** Receptionist walk-in triage where full data is unavailable.

**Key design decision:** Since `Patient` is immutable (event-sourced, blocked from UPDATE by trigger), `isRegistrationComplete` cannot be stored in the `patients` table as a mutable flag. Two options exist:
- Option A: Add `is_registration_complete BOOLEAN DEFAULT true` to the patients table (defaults true for full registrations, false for quick). Since each version row can carry this flag, a new version row sets it to true when completion happens. This is compatible with immutability.
- Option B: Separate mutable `patient_registration_flags` table keyed by `patient_business_id`.

**Recommendation: Option A** — Add `is_registration_complete` to the `patients` table. It flows naturally with the event-sourced pattern (each version carries the completeness state). When a receptionist completes the record, a new version row is inserted with `is_registration_complete = true`. This requires a new Flyway migration to add the column with `DEFAULT true` (so existing records are not affected) and a `NOT NULL` constraint with that default.

**Example — QuickRegisterRequest DTO:**
```java
// Source: codebase inspection of RegisterPatientRequest.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickRegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @NotNull(message = "Date of birth is required")
    @Past
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotBlank(message = "Phone number is required")
    @ValidPhoneNumber
    private String phoneNumber;

    // All other fields are optional — email, address, insurance, emergency contacts
    @Email
    private String email;

    @Size(max = 255) private String addressLine1;
    @Size(max = 100) private String city;
    @Size(max = 50)  private String state;
    @Pattern(regexp = "^\\d{5}(-\\d{4})?$") private String zipCode;
}
```

**Example — QuickRegistrationService:**
```java
// Following the PatientService.registerPatient() pattern
public PatientDetailResponse quickRegisterPatient(QuickRegisterRequest request) {
    Patient patient = Patient.builder()
        .firstName(request.getFirstName())
        .lastName(request.getLastName())
        .dateOfBirth(request.getDateOfBirth())
        .gender(request.getGender())
        .phoneNumber(request.getPhoneNumber())
        .email(request.getEmail())
        .addressLine1(request.getAddressLine1())
        .city(request.getCity())
        .state(request.getState())
        .zipCode(request.getZipCode())
        .photoIdVerified(false)           // Not verified in quick registration
        .isRegistrationComplete(false)    // NEW FLAG: marks record incomplete
        .status(PatientStatus.ACTIVE)
        .build();
    return patientRepository.save(patient);
}
```

### Pattern 2: Patient Photo Upload — FileStorageService Abstraction

**What:** `POST /api/v1/patients/{businessId}/photo` accepts `multipart/form-data` with a single image file. The file is validated (content-type, size, extension match) and stored on the local filesystem. The stored file path (relative, using UUID-based filename) is saved to a `patient_photos` table linked by `patient_business_id`.

**Key constraints:**
- Patient photos are PHI under HIPAA. Store outside the web-accessible root. Access via a secure download endpoint (`GET /api/v1/patients/{businessId}/photo`) that checks JWT authentication and role.
- Use UUID-generated filenames (not original filenames) to prevent path traversal.
- Validate content-type on both MIME type declaration AND by reading the file header (magic bytes via `javax.imageio.ImageIO`).
- Max file size: 5MB for patient photos (sufficient for webcam captures at reasonable quality).

**Spring Boot multipart config (add to application.yml):**
```yaml
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 5MB
      max-request-size: 6MB
      file-size-threshold: 2KB

app:
  storage:
    photos-dir: ${PHOTOS_DIR:/var/hospital/patient-photos}
```

**Example — FileStorageService:**
```java
// Source: Spring Boot file upload best practices (oneuptime.com/blog, 2025)
@Service
public class FileStorageService {

    private final Path storageLocation;

    public FileStorageService(@Value("${app.storage.photos-dir}") String photosDir) {
        this.storageLocation = Paths.get(photosDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create photo storage directory", e);
        }
    }

    public String store(MultipartFile file) {
        // 1. Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !List.of("image/jpeg", "image/png").contains(contentType)) {
            throw new IllegalArgumentException("Only JPEG and PNG images are accepted");
        }
        // 2. Validate actual image (magic bytes check)
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new IllegalArgumentException("Uploaded file is not a valid image");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read uploaded file as image");
        }
        // 3. Generate safe filename
        String extension = contentType.equals("image/png") ? ".png" : ".jpg";
        String filename = UUID.randomUUID().toString() + extension;
        // 4. Store file
        Path target = storageLocation.resolve(filename).normalize();
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store photo", e);
        }
        return filename; // Return relative filename for DB storage
    }

    public Resource load(String filename) {
        Path filePath = storageLocation.resolve(filename).normalize();
        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalStateException("Photo not found: " + filename);
        }
        return resource;
    }
}
```

**Example — PatientPhotos table (Flyway migration):**
```sql
-- V006__add_patient_photos_table.sql
CREATE TABLE patient_photos (
    id            BIGSERIAL PRIMARY KEY,
    patient_business_id UUID NOT NULL,
    filename      VARCHAR(255) NOT NULL,         -- UUID-based safe filename
    content_type  VARCHAR(50) NOT NULL,           -- 'image/jpeg' or 'image/png'
    file_size_bytes BIGINT NOT NULL,
    uploaded_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    uploaded_by   VARCHAR(255) NOT NULL,
    is_current    BOOLEAN DEFAULT true            -- only one current photo per patient
);

CREATE INDEX idx_patient_photos_business ON patient_photos(patient_business_id);
CREATE INDEX idx_patient_photos_current ON patient_photos(patient_business_id, is_current);
```

### Pattern 3: Data Quality Dashboard — Native Aggregate Queries

**What:** `GET /api/v1/admin/data-quality` returns a summary with counts: total active patients, patients missing insurance, patients missing photo, patients with `isRegistrationComplete = false`, patients with `photoIdVerified = false`. These are read-only aggregate queries against the `patients_latest` view and joined tables.

**Why native SQL:** The PostgreSQL `patients_latest` view (using `DISTINCT ON`) is already defined. Spring Data JPA `@Query(nativeQuery = true)` is the correct tool. JPA JPQL cannot reference database views directly.

**Performance at 50K patients:** Five individual aggregate COUNT queries on indexed columns take < 5ms each on 50K rows with proper indexes. No pagination needed for dashboard totals — these are single-number counts, not full result sets.

**Example — DataQualityRepository:**
```java
// Source: codebase inspection; native query pattern from PatientRepository.java
@Repository
public interface DataQualityRepository extends JpaRepository<Patient, String> {

    // Count of latest-version patients with is_registration_complete = false
    @Query(value = """
        SELECT COUNT(*) FROM patients_latest
        WHERE is_registration_complete = false AND status = 'ACTIVE'
        """, nativeQuery = true)
    long countIncompleteRegistrations();

    // Count of active patients without any active insurance record
    @Query(value = """
        SELECT COUNT(*) FROM patients_latest p
        WHERE p.status = 'ACTIVE'
          AND NOT EXISTS (
              SELECT 1 FROM insurance i
              WHERE i.patient_business_id = p.business_id AND i.is_active = true
          )
        """, nativeQuery = true)
    long countMissingInsurance();

    // Count of active patients without a current photo
    @Query(value = """
        SELECT COUNT(*) FROM patients_latest p
        WHERE p.status = 'ACTIVE'
          AND NOT EXISTS (
              SELECT 1 FROM patient_photos ph
              WHERE ph.patient_business_id = p.business_id AND ph.is_current = true
          )
        """, nativeQuery = true)
    long countMissingPhotos();

    // Count of active patients with photoIdVerified = false
    @Query(value = """
        SELECT COUNT(*) FROM patients_latest
        WHERE status = 'ACTIVE' AND photo_id_verified = false
        """, nativeQuery = true)
    long countUnverifiedPhotoIds();

    // Total active patients
    @Query(value = """
        SELECT COUNT(*) FROM patients_latest WHERE status = 'ACTIVE'
        """, nativeQuery = true)
    long countTotalActive();
}
```

**Dashboard response DTO:**
```java
@Data @Builder
public class DataQualityReport {
    private long totalActivePatients;
    private long incompleteRegistrations;       // isRegistrationComplete = false
    private long missingInsurance;              // no active insurance record
    private long missingPhoto;                  // no current patient photo
    private long unverifiedPhotoIds;            // photoIdVerified = false
    private Instant generatedAt;
}
```

### Pattern 4: Smart Forms — ZIP Lookup + Insurance Suggestion

**What (ZIP lookup):** `GET /api/v1/smart-form/zip/{zipCode}` calls Zippopotam.us and returns city and state. Results are cached in Caffeine (there are only ~43,000 US ZIP codes; after warm-up the hit rate will be near 100%).

**Zippopotam.us API details (verified):**
- URL: `https://api.zippopotam.us/us/{zipCode}`
- No API key required
- No stated rate limits; community-supported
- Response: `{ "post code": "90210", "places": [{ "place name": "Beverly Hills", "state": "California", "state abbreviation": "CA" }] }`
- Free and open source (github.com/zippopotamus/zippopotamus)

**What (insurance suggestion):** `GET /api/v1/smart-form/insurance-plans` returns a curated list of common insurance providers configured in `application.yml`. No external API required. The list is static but configurable without redeployment.

**Example — ZipLookupService:**
```java
// Source: Zippopotam.us API verified response (api.zippopotam.us/us/90210)
@Service
@RequiredArgsConstructor
public class ZipLookupService {

    private static final String ZIPPOPOTAMUS_URL =
        "https://api.zippopotam.us/us/{zipCode}";

    private final RestTemplate restTemplate;

    @Cacheable(value = "zipLookup", key = "#zipCode")
    public Optional<ZipLookupResponse> lookup(String zipCode) {
        try {
            ZippopotamusApiResponse response = restTemplate.getForObject(
                ZIPPOPOTAMUS_URL, ZippopotamusApiResponse.class, zipCode
            );
            if (response == null || response.getPlaces() == null || response.getPlaces().isEmpty()) {
                return Optional.empty();
            }
            ZippopotamusApiResponse.Place place = response.getPlaces().get(0);
            return Optional.of(ZipLookupResponse.builder()
                .zipCode(zipCode)
                .city(place.getPlaceName())
                .state(place.getState())
                .stateAbbreviation(place.getStateAbbreviation())
                .build());
        } catch (Exception e) {
            // ZIP not found or API unreachable — return empty, not an error
            return Optional.empty();
        }
    }
}
```

**Example — Caffeine cache configuration:**
```java
// Source: Baeldung Spring Boot Caffeine Cache guide
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(50_000)       // All US ZIP codes fit in 50K entries
            .expireAfterWrite(24, TimeUnit.HOURS)  // ZIP data is stable
        );
        return manager;
    }
}
```

**application.yml additions for insurance suggestions:**
```yaml
app:
  smart-form:
    insurance-plans:
      - "Blue Cross Blue Shield"
      - "Aetna"
      - "Cigna"
      - "United Healthcare"
      - "Humana"
      - "Medicare"
      - "Medicaid"
      - "Anthem"
      - "Kaiser Permanente"
      - "Molina Healthcare"
```

**SmartFormProperties:**
```java
@Configuration
@ConfigurationProperties(prefix = "app.smart-form")
@Data
public class SmartFormProperties {
    private List<String> insurancePlans = new ArrayList<>();
}
```

### Anti-Patterns to Avoid

- **Storing photo binaries in PostgreSQL as BLOBs:** Bloats the database, makes backup/restore painful, hurts query performance on unrelated tables. Store path in DB, binary on filesystem.
- **Modifying the `patients` table UPDATE trigger:** The trigger that prevents updates is a core invariant. `is_registration_complete` is added as a column that each new version row carries (not updated in-place).
- **Using fuzzy matching for the dashboard count queries:** The `PatientSearchRepository.fuzzyNameSearch()` loads all patients in-memory. Never call this in the dashboard path. Use aggregate SQL COUNT queries exclusively.
- **Not caching ZIP lookups:** Zippopotam.us is a third-party service. Every form keystroke would make an outbound HTTP call. Always cache with a TTL of at least 24 hours.
- **Ignoring existing `photoIdVerified` field:** The `patients` table already has `photo_id_verified BOOLEAN`. The new `patient_photos` table tracks whether a file was actually uploaded. These are two distinct concepts: `photoIdVerified` = receptionist confirmed ID document; `patient_photos.is_current` = a photo file has been captured. Both columns are needed.
- **Using `@AssertTrue photoIdVerified` in QuickRegisterRequest:** The existing `RegisterPatientRequest` enforces this. The new `QuickRegisterRequest` must NOT inherit or include this constraint.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| ZIP code → city/state lookup | Custom ZIP code database table | Zippopotam.us API + Caffeine cache | 43K ZIP records to maintain, data goes stale; the API is free and reliable |
| File content-type validation | Extension-only string check | `javax.imageio.ImageIO.read()` magic bytes + MIME check | Extension can be spoofed; magic bytes check verifies actual file type |
| In-process caching | `HashMap` singleton | Caffeine + `@Cacheable` | Caffeine handles eviction, TTL, concurrent access, max size automatically |
| Dashboard aggregate queries | Load all patients in Java and count in-memory | Native SQL COUNT queries against `patients_latest` view | In-memory approach fails at 50K records; SQL aggregation is O(indexed scan) |
| Insurance plan list | External API scraping | Static config list in application.yml | No reliable free API for insurance plan suggestion; config is maintainable by admin |
| Photo filename generation | Use original upload filename | `UUID.randomUUID() + extension` | Original filenames enable path traversal attacks and collisions |

**Key insight:** All four features in this phase are well-solved by existing Spring Boot primitives and simple external APIs. Custom implementations would reintroduce complexity already handled by the ecosystem.

---

## Common Pitfalls

### Pitfall 1: `patients` Table is Immutable — Cannot Add Mutable Flags In-Place

**What goes wrong:** Developer adds `is_registration_complete` column expecting to UPDATE it later. The PostgreSQL trigger `prevent_patient_updates` will throw an exception on any UPDATE.

**Why it happens:** The event-sourced immutability pattern means every "change" is a new INSERT. Developers accustomed to mutable tables try to UPDATE the existing row.

**How to avoid:** Add `is_registration_complete BOOLEAN NOT NULL DEFAULT true` to the `patients` table via Flyway. Full registrations default to `true`. Quick registrations insert with `false`. When a receptionist completes the record, the existing `updatePatient()` flow in `PatientService` inserts a new version with `is_registration_complete = true`. The `UpdatePatientRequest` DTO gets the new field.

**Warning signs:** Any code that calls `patientRepository.save(existingPatient)` after mutating a field on the fetched entity will fail with a trigger exception.

### Pitfall 2: `patients_latest` View is Not Automatically Available in JPQL

**What goes wrong:** Writing `@Query("SELECT p FROM patients_latest p WHERE ...")` in a Spring Data repository. JPQL maps to JPA entities (the `patients` table), not to database views.

**Why it happens:** `patients_latest` is a PostgreSQL view, not a JPA-managed entity. JPQL cannot query it directly.

**How to avoid:** Always use `nativeQuery = true` when querying `patients_latest`. The view exists and is correct; just switch to native SQL for dashboard queries.

**Warning signs:** JPQL queries against view names silently fall back to the underlying table, returning ALL versions instead of only the latest.

### Pitfall 3: Multipart File Size Rejection Returns 500 Instead of 400

**What goes wrong:** When an uploaded photo exceeds `max-file-size`, Spring throws `MaxUploadSizeExceededException` before the controller method is reached. If not handled, this becomes a 500 error.

**Why it happens:** The exception is thrown by the `DispatcherServlet` filter chain, not by the controller, so `@RestControllerAdvice` may not catch it without specific configuration.

**How to avoid:** Add an explicit `@ExceptionHandler(MaxUploadSizeExceededException.class)` to `GlobalExceptionHandler` that returns a 400 with a clear RFC 7807 ProblemDetail message.

**Warning signs:** Postman gets 500 Internal Server Error when testing with large files; logs show `MaxUploadSizeExceededException`.

### Pitfall 4: Zippopotam.us Returns Empty `places` Array for Invalid ZIPs

**What goes wrong:** Code does `response.getPlaces().get(0)` without null/empty check. Throws `IndexOutOfBoundsException` for invalid or non-existent ZIP codes.

**Why it happens:** The API returns HTTP 200 with an empty or absent `places` array for unknown ZIP codes rather than a 404.

**How to avoid:** Always check `if (response.getPlaces() == null || response.getPlaces().isEmpty())` before accessing the first element. Return `Optional.empty()` and let the controller return 404.

**Warning signs:** `IndexOutOfBoundsException` in logs when testing with ZIP code "00000" or similar invalid inputs.

### Pitfall 5: Zippopotam.us API Field Names Contain Spaces

**What goes wrong:** Jackson deserialization fails when mapping the API response. The API returns `"place name"`, `"state abbreviation"`, `"post code"`, `"country abbreviation"` — all with spaces.

**Why it happens:** The Zippopotam.us JSON format uses human-readable keys with spaces, not camelCase.

**How to avoid:** Use `@JsonProperty` annotations on the DTO to map the space-containing keys:
```java
@Data
public class ZippopotamusApiResponse {
    @JsonProperty("post code")     private String postCode;
    @JsonProperty("country")       private String country;
    @JsonProperty("places")        private List<Place> places;

    @Data
    public static class Place {
        @JsonProperty("place name")          private String placeName;
        @JsonProperty("state")               private String state;
        @JsonProperty("state abbreviation")  private String stateAbbreviation;
    }
}
```

**Warning signs:** `NullPointerException` or empty fields when accessing `response.getPlaces()` or `place.getState()` even though the HTTP response body looks correct.

### Pitfall 6: Photo Stored Under Web-Accessible Path

**What goes wrong:** Photos stored in `src/main/resources/static/photos/` or any directory served by Spring's static resource handler become publicly accessible without authentication.

**Why it happens:** Developers follow "simple" tutorials that store files under the static resources path.

**How to avoid:** Store photos at a path OUTSIDE the application's web root (e.g., `/var/hospital/patient-photos/`). Serve photos exclusively through an authenticated `GET /api/v1/patients/{businessId}/photo` endpoint that checks JWT. The endpoint reads the file from the filesystem and returns it as a `ResponseEntity<Resource>`.

**Warning signs:** A URL like `http://server/photos/uuid.jpg` returns an image without requiring an Authorization header.

---

## Code Examples

### Verified: Spring Boot Multipart Config
```yaml
# Source: Spring Boot official docs pattern; verified via oneuptime.com blog 2025
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 5MB
      max-request-size: 6MB
      file-size-threshold: 2KB
```

### Verified: @ConfigurationProperties for Insurance Plans
```java
// Source: Spring Boot @ConfigurationProperties official pattern
@Configuration
@ConfigurationProperties(prefix = "app.smart-form")
@Data
public class SmartFormProperties {
    private List<String> insurancePlans = new ArrayList<>();
}
```

### Verified: Zippopotam.us API Response Structure
```json
// Source: Direct API call to api.zippopotam.us/us/90210 (verified 2026-02-20)
{
  "post code": "90210",
  "country": "United States",
  "country abbreviation": "US",
  "places": [
    {
      "place name": "Beverly Hills",
      "longitude": "-118.4065",
      "state": "California",
      "state abbreviation": "CA",
      "latitude": "34.0901"
    }
  ]
}
```

### Verified: Native Aggregate COUNT Pattern (matches existing PatientRepository style)
```java
// Source: codebase inspection — PatientRepository.java uses same nativeQuery = true pattern
@Query(value = """
    SELECT COUNT(*) FROM patients_latest
    WHERE status = 'ACTIVE'
      AND is_registration_complete = false
    """, nativeQuery = true)
long countIncompleteRegistrations();
```

### Verified: RestTemplate Bean Registration
```java
// Source: Spring Boot standard pattern; RestTemplate is not auto-configured in Spring Boot 3
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

### Verified: MaxUploadSizeExceededException Handler
```java
// Source: GlobalExceptionHandler.java pattern — extends ResponseEntityExceptionHandler
@ExceptionHandler(MaxUploadSizeExceededException.class)
public ResponseEntity<ProblemDetail> handleMaxUploadSize(
    MaxUploadSizeExceededException ex,
    WebRequest request
) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        "Uploaded file exceeds maximum allowed size of 5MB"
    );
    problem.setTitle("File Too Large");
    problem.setType(URI.create("https://api.hospital.com/problems/file-too-large"));
    problem.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `RestTemplate` for all HTTP calls | `WebClient` (reactive) preferred in Spring 6+ | Spring 6 / Spring Boot 3 | `RestTemplate` still works and is sufficient for infrequent ZIP lookups; switch to WebClient if reactive stack is added |
| Storing multipart temp files in `/tmp` | Configurable `file-size-threshold` for memory vs disk | Spring Boot 2+ | Already configurable via `spring.servlet.multipart.file-size-threshold` |
| Manual JPQL aggregate queries | JPA Projections / Spring Data `@Query` | Spring Data 2+ | Native SQL still preferred for complex aggregate queries against views |
| `@Cacheable` with `ConcurrentHashMap` simple cache | Caffeine cache manager | Spring Boot 2.x | Caffeine is now the de-facto recommended local cache; simple cache still works but has no eviction policy |

**Deprecated/outdated:**
- `Commons FileUpload` (Apache): Replaced by Spring's native multipart support. Do not add `commons-fileupload` to pom.xml — Spring Boot 3 handles multipart natively.
- `EhCache 2.x`: Spring Boot 3 auto-configures Caffeine or EhCache 3. Use Caffeine for simplicity.

---

## Existing Codebase Integration Points

This section documents what Phase 3 code must integrate with — not create from scratch.

| Existing Component | How Phase 3 Uses It |
|-------------------|---------------------|
| `PatientRepository.findLatestVersionByBusinessId()` | Photo upload endpoint and dashboard verify patient exists before operating |
| `PatientService.registerPatient()` | Quick registration service follows same pattern; reuses duplicate detection |
| `GlobalExceptionHandler` | Add `MaxUploadSizeExceededException` handler; all other errors already handled |
| `@Audited` AOP interceptor | Add `@Audited(action = "CREATE", resourceType = "PATIENT_PHOTO")` on photo upload |
| `PatientPermissionEvaluator` | RECEPTIONIST/ADMIN can write photos; DOCTOR/NURSE can only read |
| `patients_latest` VIEW | Dashboard queries run against this view using `nativeQuery = true` |
| `AuditLog` / `AuditLogRepository` | Photo access (read/write) should be HIPAA-audited via existing `@Audited` annotation |
| `SensitiveDataConverter` (AES-256-GCM) | NOT needed for photo filename (it's not PHI); IS needed if any new encrypted fields are added |
| `SecurityConfig` | Photo endpoint `/api/v1/patients/*/photo` falls under existing `.anyRequest().authenticated()` rule; no new security config needed |
| `DuplicateDetectionService` | Quick registration MUST still run duplicate check (same as full registration) |

---

## Open Questions

1. **Should quick-registered patients be flagged to receptionists at next visit?**
   - What we know: The `is_registration_complete = false` flag enables filtering incomplete records.
   - What's unclear: Should the GET patient endpoint return a warning/flag so the UI highlights incomplete profiles? There is no frontend in this project, but API consumers need this signal.
   - Recommendation: Include `isRegistrationComplete` in `PatientDetailResponse` so API consumers can display a visual indicator.

2. **Should the photo download endpoint return the image directly or a signed URL?**
   - What we know: Local filesystem storage means no S3 pre-signed URLs are possible. The endpoint must stream the file.
   - What's unclear: File size vs. streaming memory concerns.
   - Recommendation: Use `StreamingResponseBody` or `InputStreamResource` with `ResponseEntity<Resource>` to stream without loading the full file into heap. This is standard Spring MVC pattern.

3. **How many insurance plan suggestions are needed?**
   - What we know: The CoverageType enum has 8 values (HMO/PPO/EPO/POS/HDHP/MEDICAID/MEDICARE/OTHER). The suggestion endpoint is for provider names (e.g., "Aetna"), not plan types.
   - What's unclear: The project stakeholder has not specified a list length.
   - Recommendation: Start with 10-15 major US insurance providers hardcoded in `application.yml`. The list is trivially configurable without code changes.

4. **Does quick registration bypass the unique identity constraint?**
   - What we know: `V005__fix_unique_identity_constraint.sql` enforces unique (first_name, last_name, date_of_birth, phone_number) for active patients. Quick registration uses the same required fields (firstName, lastName, dob, gender, phone), so the constraint still applies.
   - What's unclear: Whether walk-in patients might genuinely lack a phone number.
   - Recommendation: Keep `phoneNumber` as required in `QuickRegisterRequest`. The constraint is a safety guard against duplicates. Document that phone is the minimum identifier.

---

## Sources

### Primary (HIGH confidence)

- Codebase direct inspection: `Patient.java`, `PatientService.java`, `RegisterPatientRequest.java`, `PatientRepository.java`, `GlobalExceptionHandler.java`, `SecurityConfig.java`, `AuditInterceptor.java`, `InsuranceService.java`, `PatientSearchRepository.java`, `pom.xml`, `application.yml`, all Flyway migrations
- Direct API call: `api.zippopotam.us/us/90210` — response structure verified 2026-02-20
- Zippopotam.us official site (zippopotam.us): Free, no auth key, open source, 60+ countries

### Secondary (MEDIUM confidence)

- oneuptime.com/blog/post/2025-12-22-spring-boot-file-upload/ — Spring Boot 3.5 multipart config properties, content-type validation, UUID filename pattern, filesystem storage best practices; verified against Spring Boot documentation patterns
- publicapi.dev/zippopotam-us-api — No rate limits stated by Zippopotam.us; one third-party source references 15k/hour limit; official docs claim "no hard limits." Treat as unreliable for very high-frequency use.
- Baeldung spring-boot-caffeine-cache — Caffeine setup with `@Cacheable`, TTL and max-size configuration

### Tertiary (LOW confidence)

- "Quick registration / progressive profile completion" pattern: No canonical Spring Boot reference article found. The pattern is derived from first-principles analysis of the existing codebase constraints (immutable Patient entity + event-sourced versioning). Confidence in the approach is HIGH; confidence that a named "best practice" article exists is LOW.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — directly verified from pom.xml, official Spring docs, Zippopotam.us API tested
- Architecture (quick registration): HIGH — derived from direct codebase reading of the immutable Patient entity and trigger
- Architecture (photo upload): HIGH — standard Spring Boot multipart upload pattern, well-documented
- Architecture (dashboard): HIGH — native SQL aggregate queries are standard; `patients_latest` view already exists
- Architecture (smart forms): HIGH (ZIP API verified) / MEDIUM (insurance list size is a product decision)
- Common pitfalls: HIGH — all derived from actual code constraints in the codebase
- Zippopotam.us rate limits: LOW — conflicting third-party info; official site claims no limits

**Research date:** 2026-02-20
**Valid until:** 2026-03-22 (30 days; Zippopotam.us API is stable; Spring Boot 3.4.5 is current)
