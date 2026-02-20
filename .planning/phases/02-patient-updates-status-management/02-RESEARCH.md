# Phase 2: Patient Updates & Status Management - Research

**Researched:** 2026-02-20
**Domain:** Spring Boot event-sourced patient update, status management, insurance CRUD, emergency contact management, in-process domain events
**Confidence:** HIGH

## Summary

Phase 2 extends the event-sourced patient model built in Phase 1 to support updates, status changes, insurance, and full emergency contact CRUD — all while maintaining the immutable versioning contract. The critical architectural invariant inherited from Phase 1 is that the `patients` table has a PostgreSQL trigger that physically prevents UPDATE statements; therefore "updating" a patient always means INSERTing a new row with `version = latestVersion + 1`, keeping the same `businessId`. This is a settled decision and must not be revisited.

The three new concerns Phase 2 introduces are: (1) an Insurance entity that does not exist yet and must be designed from scratch with field-level AES-256-GCM encryption for PHI fields (policy number, group number) using the already-built `SensitiveDataConverter`; (2) a `PatientUpdated` domain event that must be published after a successful patient version insert — using Spring's `ApplicationEventPublisher` with `@TransactionalEventListener(phase = AFTER_COMMIT)` since no external message broker is configured yet; and (3) wiring the already-built `PatientPermissionEvaluator.hasPermission()` into `@PreAuthorize` expressions on the new update/status endpoints, which Phase 1 left as role-only checks.

Emergency contact management is largely infrastructure-complete from Phase 1 (the `EmergencyContact` entity and `EmergencyContactRepository` exist with full CRUD query methods); Phase 2 adds the API endpoints for individual contact update and delete with audit trail. Insurance requires a new Flyway migration (`V003`), a new JPA entity with `SensitiveDataConverter` on sensitive fields, a new repository, and new service/controller layers. Status management (activate/deactivate) follows the same event-sourcing insert-new-version pattern as demographic updates, but with only the `status` field changed.

**Primary recommendation:** Implement all "updates" as new patient version inserts using the existing event-sourced pattern; publish `PatientUpdatedEvent` via `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`; design the Insurance table with `SensitiveDataConverter` on policy/group numbers; reuse the existing `EmergencyContactRepository` for contact CRUD; wire `@PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")` on all mutating endpoints.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| UPD-01 | Receptionist/Admin can update patient demographic information via editable form | New `PUT /api/v1/patients/{businessId}` endpoint; service creates new Patient version row |
| UPD-02 | System pre-populates edit form with current patient data | Existing `GET /api/v1/patients/{businessId}` returns `PatientDetailResponse` — reuse as-is |
| UPD-03 | System makes Patient ID read-only (cannot be edited) | `patientId` not included in `UpdatePatientRequest` DTO; response echoes latest version's generated ID |
| UPD-04 | System makes registration date read-only (cannot be edited) | `registeredAt` from version 1 `createdAt` — not in update DTO; response assembles it from original version |
| UPD-05 | System applies same validation rules as registration to updates | Reuse existing `@Valid` annotations from `RegisterPatientRequest` in new `UpdatePatientRequest` DTO |
| UPD-06 | System saves patient updates and displays success message | Service returns updated `PatientDetailResponse` with 200 OK; client shows success toast |
| UPD-07 | System records update timestamp and user who made the update | Event-sourcing: new row's `createdAt` = update timestamp; `createdBy` = authenticated user via `@CreatedBy` |
| UPD-08 | System discards changes on Cancel and returns to profile view | Frontend-only concern; no backend requirement |
| UPD-09 | System displays specific validation error messages for invalid fields on update | Existing `GlobalExceptionHandler` RFC 7807 handler covers this automatically |
| UPD-10 | System publishes PatientUpdated event to message broker after successful update | Spring `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` — no external broker needed yet |
| STAT-01 | Admin can deactivate active patient with confirmation dialog | `PATCH /api/v1/patients/{businessId}/status` with `{"status":"INACTIVE"}`; admin-only `@PreAuthorize` |
| STAT-02 | Admin can activate inactive patient without confirmation | Same endpoint as STAT-01; confirmation dialog is frontend UX |
| STAT-03 | System changes patient status to "INACTIVE" on deactivation | Service inserts new Patient version with `status = INACTIVE` |
| STAT-04 | System changes patient status to "ACTIVE" on activation | Service inserts new Patient version with `status = ACTIVE` |
| STAT-05 | System records status change timestamp and user who made the change | Event-sourcing: new version row `createdAt` and `createdBy` capture this automatically |
| STAT-06 | System displays success message after status change | 200 OK with updated `PatientDetailResponse`; client shows toast |
| STAT-07 | System excludes inactive patients from "Active" filter view | Already handled by Phase 1 search with `status` filter; no new work |
| STAT-08 | System includes both active and inactive in "All" filter with status indicators | Already handled by Phase 1 search `status=null` path; no new work |
| INS-01 | Receptionist can capture insurance information (provider, policy number, group number, coverage type) | New `Insurance` JPA entity + `V003` Flyway migration; new `POST /api/v1/patients/{businessId}/insurance` |
| INS-02 | System validates insurance policy number format | `@Pattern` constraint on `UpdateInsuranceRequest.policyNumber` — standard alphanumeric |
| INS-03 | System stores insurance as part of patient record | Insurance table linked via `patientBusinessId` (UUID FK pattern same as emergency_contacts) |
| INS-04 | Staff can view insurance information on patient profile | `GET /api/v1/patients/{businessId}` — extend `PatientDetailResponse` to include insurance |
| INS-05 | Receptionist/Admin can update insurance information with audit trail | `PUT /api/v1/patients/{businessId}/insurance` — upsert pattern; new row or update mutable fields |
| EMR-01 | Receptionist can add multiple emergency contacts for patient | `POST /api/v1/patients/{businessId}/emergency-contacts` — reuses `EmergencyContactRepository.save()` |
| EMR-02 | System validates emergency contact phone number format | Reuse existing `@ValidPhoneNumber` constraint on `EmergencyContactDto` |
| EMR-03 | Staff can view all emergency contacts on patient profile | Already done in Phase 1 profile view; no new backend work needed |
| EMR-04 | Receptionist/Admin can update or remove emergency contacts with audit trail | `PUT /api/v1/patients/{businessId}/emergency-contacts/{id}` and `DELETE` + audit logging |
</phase_requirements>

---

## Standard Stack

All libraries below are already present in `pom.xml` unless noted. No new dependencies are required for Phase 2.

### Core (All Present in pom.xml)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JPA | 3.4.5 (managed) | Patient version insert, Insurance/EmergencyContact CRUD | ORM layer already configured with Hibernate, AuditingEntityListener |
| Spring Security | 3.4.5 (managed) | `@PreAuthorize hasPermission()` wiring on mutating endpoints | `PatientPermissionEvaluator` + `MethodSecurityConfig` already wired |
| Spring AOP | 3.4.5 (managed) | `@Audited` annotation triggers `AuditInterceptor` on all UPDATE/DELETE actions | AuditInterceptor already handles CREATE/READ/SEARCH |
| Flyway | 3.4.5 (managed) | `V003__create_insurance_schema.sql` migration | V001, V002 already applying; next is V003 |
| Jakarta Validation | 3.4.5 (managed) | `@Pattern`, `@NotBlank`, `@Valid` on new DTOs | `GlobalExceptionHandler` already handles `MethodArgumentNotValidException` |
| Bouncy Castle | 1.78 | AES-256-GCM via `EncryptionService` already built | `SensitiveDataConverter` already uses it; apply to insurance PHI fields |

### No New Dependencies Required

Phase 2 reuses the complete Phase 1 stack. The event publishing pattern uses Spring's built-in `ApplicationEventPublisher` (part of spring-context, already on classpath). No Kafka, RabbitMQ, or other broker dependency is added.

### If UPD-10 Needs External Broker in Future (Out of Scope Now)

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `ApplicationEventPublisher` | Spring Cloud Stream + Kafka | Kafka provides durable, distributed delivery but requires infrastructure; internal events are sufficient for v1 downstream consumption within the same JVM |
| `ApplicationEventPublisher` | Spring AMQP + RabbitMQ | RabbitMQ provides reliable delivery across services but adds operational complexity; out of scope for Phase 2 |

---

## Architecture Patterns

### Phase 2 Project Structure (Additions to Phase 1)

```
src/main/java/com/hospital/
├── patient/
│   ├── domain/
│   │   ├── Patient.java                    # EXISTING — immutable versioned entity
│   │   ├── Insurance.java                  # NEW — mutable insurance entity linked via businessId
│   │   ├── EmergencyContact.java           # EXISTING — mutable child entity
│   │   └── PatientStatus.java              # EXISTING — ACTIVE/INACTIVE enum
│   ├── application/
│   │   ├── PatientService.java             # EXTEND — add updatePatient(), changeStatus()
│   │   ├── InsuranceService.java           # NEW — upsert/get insurance for patient
│   │   └── EmergencyContactService.java    # NEW — add/update/delete individual contacts
│   ├── infrastructure/
│   │   ├── PatientRepository.java          # EXTEND — add findMaxVersionByBusinessId()
│   │   ├── InsuranceRepository.java        # NEW — findByPatientBusinessId()
│   │   └── EmergencyContactRepository.java # EXISTING — has full CRUD methods
│   └── api/
│       ├── PatientController.java          # EXTEND — add PUT update, PATCH status endpoints
│       ├── InsuranceController.java        # NEW — GET/POST/PUT /patients/{id}/insurance
│       ├── EmergencyContactController.java # NEW — POST/PUT/DELETE /patients/{id}/emergency-contacts
│       └── dto/
│           ├── UpdatePatientRequest.java   # NEW — same fields as Register minus photoIdVerified
│           ├── UpdateStatusRequest.java    # NEW — just status field
│           ├── InsuranceDto.java           # NEW — provider, policyNumber, groupNumber, coverageType
│           └── PatientDetailResponse.java  # EXTEND — add insuranceInfo field
├── events/
│   └── PatientUpdatedEvent.java            # NEW — domain event published after successful update
└── shared/
    └── exception/
        └── GlobalExceptionHandler.java     # EXISTING — handles 400/404/409/403/500 already
```

### Pattern 1: Insert New Patient Version (Core Update Pattern)

**What:** "Updating" a patient copies all current field values, applies the changes, increments the version, and inserts a new row. The `patient_id` PK is newly generated; the `businessId` stays constant.
**When to use:** Every demographic update (UPD-01) and status change (STAT-01 through STAT-04).
**Why:** The PostgreSQL trigger `prevent_patient_updates` will throw an exception if any UPDATE is attempted. This is not optional.

```java
// Source: Existing Patient entity @PrePersist and PatientRepository pattern
@Service
@Transactional
public class PatientService {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public PatientDetailResponse updatePatient(UUID businessId, UpdatePatientRequest request) {
        // 1. Load latest version (throws PatientNotFoundException if not found)
        Patient current = patientRepository.findLatestVersionByBusinessId(businessId)
            .orElseThrow(() -> new PatientNotFoundException(businessId.toString()));

        // 2. Find highest version number for this business_id
        Long nextVersion = patientRepository.findMaxVersionByBusinessId(businessId)
            .orElse(0L) + 1;

        // 3. Build new version — copy current, apply requested changes
        Patient newVersion = Patient.builder()
            // patientId: auto-generated by PatientIdGenerator (@GeneratedValue)
            .businessId(current.getBusinessId())           // SAME business ID — never changes
            .version(nextVersion)                          // incremented version
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .dateOfBirth(request.getDateOfBirth())
            .gender(request.getGender())
            .phoneNumber(request.getPhoneNumber())
            .email(request.getEmail())
            .addressLine1(request.getAddressLine1())
            .addressLine2(request.getAddressLine2())
            .city(request.getCity())
            .state(request.getState())
            .zipCode(request.getZipCode())
            .photoIdVerified(current.getPhotoIdVerified())  // preserved from current
            .status(current.getStatus())                    // preserved from current
            // createdAt, createdBy: set by @CreatedDate / @CreatedBy via AuditingEntityListener
            .build();

        Patient saved = patientRepository.save(newVersion);

        // 4. Publish domain event AFTER transaction commits (see Pattern 3)
        eventPublisher.publishEvent(new PatientUpdatedEvent(this, saved.getBusinessId(),
            saved.getVersion(), saved.getCreatedBy()));

        return buildDetailResponse(saved);
    }
}
```

**Critical repository addition needed:**

```java
// In PatientRepository.java — find max version for version increment
@Query("SELECT MAX(p.version) FROM Patient p WHERE p.businessId = :businessId")
Optional<Long> findMaxVersionByBusinessId(@Param("businessId") UUID businessId);
```

### Pattern 2: Status Change as New Version

**What:** Status changes (activate/deactivate) follow the same insert-new-version pattern but only change the `status` field. All other fields are copied from the latest version.
**When to use:** STAT-01 through STAT-04.

```java
// Source: Same event-sourcing insert pattern as demographic updates
public PatientDetailResponse changePatientStatus(UUID businessId, PatientStatus newStatus) {
    Patient current = patientRepository.findLatestVersionByBusinessId(businessId)
        .orElseThrow(() -> new PatientNotFoundException(businessId.toString()));

    if (current.getStatus() == newStatus) {
        return buildDetailResponse(current); // idempotent — no-op if already in target status
    }

    Long nextVersion = patientRepository.findMaxVersionByBusinessId(businessId)
        .orElse(0L) + 1;

    Patient statusVersion = Patient.builder()
        .businessId(current.getBusinessId())
        .version(nextVersion)
        .firstName(current.getFirstName())
        .lastName(current.getLastName())
        .dateOfBirth(current.getDateOfBirth())
        .gender(current.getGender())
        .phoneNumber(current.getPhoneNumber())
        .email(current.getEmail())
        .addressLine1(current.getAddressLine1())
        .addressLine2(current.getAddressLine2())
        .city(current.getCity())
        .state(current.getState())
        .zipCode(current.getZipCode())
        .photoIdVerified(current.getPhotoIdVerified())
        .status(newStatus)                  // ONLY this field changes
        .build();

    Patient saved = patientRepository.save(statusVersion);

    eventPublisher.publishEvent(new PatientUpdatedEvent(this, saved.getBusinessId(),
        saved.getVersion(), saved.getCreatedBy()));

    return buildDetailResponse(saved);
}
```

### Pattern 3: Domain Event via ApplicationEventPublisher + @TransactionalEventListener

**What:** After a successful patient version insert, publish a `PatientUpdatedEvent` that can be consumed by other services within the same JVM. The listener runs AFTER the database transaction commits so the new version row is already visible.
**When to use:** UPD-10; every demographic update and status change.

```java
// Source: Spring ApplicationEventPublisher documentation, Baeldung Spring Events
// Pattern verified: @TransactionalEventListener with AFTER_COMMIT is the correct choice
// because we need the new Patient version to be committed before downstream can read it.

// 1. Event class (simple POJO — no need to extend ApplicationEvent in Spring 4.2+)
public class PatientUpdatedEvent {
    private final UUID businessId;
    private final Long newVersion;
    private final String updatedBy;
    private final Instant occurredAt;

    public PatientUpdatedEvent(Object source, UUID businessId, Long newVersion, String updatedBy) {
        this.businessId = businessId;
        this.newVersion = newVersion;
        this.updatedBy = updatedBy;
        this.occurredAt = Instant.now();
    }
    // getters
}

// 2. Listener (example downstream consumer within same JVM)
@Component
public class PatientUpdatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(PatientUpdatedEventListener.class);

    // AFTER_COMMIT: fires only when transaction successfully committed
    // This means the new patient version is readable before the listener executes
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPatientUpdated(PatientUpdatedEvent event) {
        log.info("Patient updated: businessId={}, version={}, by={}",
            event.getBusinessId(), event.getNewVersion(), event.getUpdatedBy());
        // Future Phase: forward to Kafka/RabbitMQ here
    }
}

// 3. Publisher in service (already shown in Pattern 1)
// eventPublisher.publishEvent(new PatientUpdatedEvent(this, businessId, version, user));

// IMPORTANT CONSTRAINT: Within the @TransactionalEventListener(AFTER_COMMIT) handler,
// you CANNOT write to the database (no open transaction at that point).
// For logging purposes, use a SEPARATE @Async method or a new transaction:
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async  // Optional: makes the listener truly non-blocking
@Transactional(propagation = Propagation.REQUIRES_NEW)  // Opens new transaction if DB write needed
public void onPatientUpdated(PatientUpdatedEvent event) {
    // Can now do DB writes (e.g., outbox table) because REQUIRES_NEW opens fresh transaction
}
```

### Pattern 4: Insurance Entity with Field-Level Encryption

**What:** Insurance stores PHI (policy number, group number) encrypted at rest using the existing `SensitiveDataConverter` (AES-256-GCM). The Insurance table is linked to a patient via `patientBusinessId` UUID, the same FK pattern as `emergency_contacts`.
**When to use:** INS-01 through INS-05.

```java
// Source: Existing SensitiveDataConverter + EmergencyContact pattern
@Entity
@Table(name = "insurance")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Insurance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_business_id", nullable = false)
    private UUID patientBusinessId;

    @Column(name = "provider_name", nullable = false, length = 255)
    private String providerName;                    // Not PHI — not encrypted

    // PHI: encrypt policy number per HIPAA SEC-05 and existing SensitiveDataConverter
    @Convert(converter = SensitiveDataConverter.class)
    @Column(name = "policy_number", nullable = false, length = 512)  // 512 to accommodate base64+IV overhead
    private String policyNumber;

    // PHI: encrypt group number
    @Convert(converter = SensitiveDataConverter.class)
    @Column(name = "group_number", length = 512)
    private String groupNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_type", nullable = false, length = 50)
    private CoverageType coverageType;              // ENUM: HMO, PPO, EPO, POS, HDHP, MEDICAID, MEDICARE, OTHER

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
```

**Why Insurance is mutable (not event-sourced):** Insurance information changes frequently and doesn't require the same immutable audit trail as core patient demographics. The `updatedAt`/`updatedBy` fields from Spring Data JPA auditing (`@LastModifiedDate`, `@LastModifiedBy`) provide a sufficient audit trail for INS-05. Only the `patients` table has the immutability trigger.

### Pattern 5: Wiring PatientPermissionEvaluator to Update Endpoints

**What:** Phase 1 left all endpoints with role-based `@PreAuthorize` only. Phase 2 must wire the already-built `PatientPermissionEvaluator` for object-level authorization on mutating endpoints using `hasPermission()`.
**When to use:** All Phase 2 mutating endpoints.

```java
// Source: MethodSecurityConfig.java already registers PatientPermissionEvaluator
// PatientPermissionEvaluator.hasPermission() already handles ADMIN + RECEPTIONIST = write, DOCTOR/NURSE = read only

// Controller endpoints — combine role check with object-level permission:
@PutMapping("/{businessId}")
@PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
@Audited(action = "UPDATE", resourceType = "PATIENT")
public ResponseEntity<PatientDetailResponse> updatePatient(
    @PathVariable UUID businessId,
    @Valid @RequestBody UpdatePatientRequest request
) {
    // PatientPermissionEvaluator handles: ADMIN=true, RECEPTIONIST=true, DOCTOR=false, NURSE=false
    PatientDetailResponse updated = patientService.updatePatient(businessId, request);
    return ResponseEntity.ok(updated);
}

@PatchMapping("/{businessId}/status")
@PreAuthorize("hasRole('ADMIN') and hasPermission(#businessId, 'Patient', 'write')")
@Audited(action = "UPDATE", resourceType = "PATIENT")
public ResponseEntity<PatientDetailResponse> changeStatus(
    @PathVariable UUID businessId,
    @Valid @RequestBody UpdateStatusRequest request
) {
    // Only ADMIN can change status (stricter than general update)
    PatientDetailResponse updated = patientService.changePatientStatus(businessId, request.getStatus());
    return ResponseEntity.ok(updated);
}

@PostMapping("/{businessId}/emergency-contacts")
@PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
@Audited(action = "UPDATE", resourceType = "PATIENT")
public ResponseEntity<EmergencyContactDto> addEmergencyContact(
    @PathVariable UUID businessId,
    @Valid @RequestBody EmergencyContactDto request
) { ... }
```

### Pattern 6: Emergency Contact Individual Update/Delete

**What:** Phase 1 added emergency contacts only at registration time. Phase 2 adds individual CRUD operations using the existing `EmergencyContactRepository`.
**When to use:** EMR-01, EMR-04.

```java
// Source: Existing EmergencyContactRepository — all query methods already exist
// EmergencyContactRepository.findByPatientBusinessId(UUID) — finds all contacts
// EmergencyContactRepository.deleteByPatientBusinessId(UUID) — bulk delete (not individual)
// Need: findById(Long id) — inherited from JpaRepository<EmergencyContact, Long>

// Add individual delete endpoint:
@DeleteMapping("/{businessId}/emergency-contacts/{contactId}")
@PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
@Audited(action = "DELETE", resourceType = "PATIENT")
public ResponseEntity<Void> deleteEmergencyContact(
    @PathVariable UUID businessId,
    @PathVariable Long contactId
) {
    emergencyContactService.deleteContact(businessId, contactId);
    return ResponseEntity.noContent().build();
}

// Service validates contact belongs to this patient before deleting:
public void deleteContact(UUID businessId, Long contactId) {
    EmergencyContact contact = emergencyContactRepository.findById(contactId)
        .orElseThrow(() -> new ResourceNotFoundException("Emergency contact not found: " + contactId));

    if (!contact.getPatientBusinessId().equals(businessId)) {
        throw new AccessDeniedException("Emergency contact does not belong to patient");
    }

    emergencyContactRepository.deleteById(contactId);
}
```

### Pattern 7: Response Assembly — registeredAt vs lastModifiedAt

**What:** The `PatientDetailResponse` already has `registeredAt` / `registeredBy` and `lastModifiedAt` / `lastModifiedBy` fields. In Phase 1 both were set to the same value (version 1 `createdAt`/`createdBy`). In Phase 2, `registeredAt` must come from the original version 1 record, while `lastModifiedAt` comes from the latest version.
**When to use:** Every `PatientDetailResponse` build in Phase 2.

```java
// Service must fetch TWO records: v1 for registration metadata, latest for current state
private PatientDetailResponse buildDetailResponse(Patient latestVersion) {
    // Fetch original v1 for registration date/user
    Patient originalVersion = patientRepository.findFirstVersionByBusinessId(
            latestVersion.getBusinessId())
        .orElse(latestVersion); // fallback to latest if original not found (shouldn't happen)

    return PatientDetailResponse.builder()
        .patientId(latestVersion.getPatientId())          // Latest patient_id
        .businessId(latestVersion.getBusinessId())
        // ... all current demographic fields from latestVersion ...
        .status(latestVersion.getStatus())
        .registeredAt(originalVersion.getCreatedAt())     // FROM VERSION 1
        .registeredBy(originalVersion.getCreatedBy())     // FROM VERSION 1
        .lastModifiedAt(latestVersion.getCreatedAt())     // FROM LATEST VERSION
        .lastModifiedBy(latestVersion.getCreatedBy())     // FROM LATEST VERSION
        .version(latestVersion.getVersion())
        .build();
}

// New repository method needed:
@Query(value = """
    SELECT p.* FROM patients p
    WHERE p.business_id = :businessId
    ORDER BY p.version ASC
    LIMIT 1
    """, nativeQuery = true)
Optional<Patient> findFirstVersionByBusinessId(@Param("businessId") UUID businessId);
```

### Anti-Patterns to Avoid

- **Calling `patientRepository.save(existingPatient)` with modifications:** The `@Immutable` annotation prevents Hibernate from generating UPDATE statements, but the real guard is the PostgreSQL trigger. Always build a new Patient instance via the builder — never modify an existing managed entity.
- **Publishing domain events inside the `@Transactional` method before the commit:** Use `eventPublisher.publishEvent()` inside the method (Spring holds event delivery until after commit), but the listener must use `@TransactionalEventListener(AFTER_COMMIT)`, NOT `@EventListener`, otherwise the listener runs before the new version is committed.
- **Making Insurance fields encrypted AND indexed:** `SensitiveDataConverter` produces randomized ciphertext (different IV each time), so encrypted fields cannot be searched by index. Encrypt policy number and group number but never add `@Index` annotations to encrypted columns.
- **Deleting emergency contacts to "update" them:** Always use the update endpoint (`PUT /{contactId}`), not delete-then-create. Delete-then-create changes the `id` and loses created_at audit trail.
- **Updating `lastModifiedAt` / `lastModifiedBy` on Insurance by hand:** These fields are managed by `@LastModifiedDate` and `@LastModifiedBy` from Spring Data JPA auditing — do not set them manually in service code.
- **Using `@LastModifiedDate` / `@LastModifiedBy` on the Patient entity:** Patient is `@Immutable` — it only has `@CreatedDate` and `@CreatedBy`. Do not attempt to add modification tracking annotations to Patient.
- **Forgetting to preserve `photoIdVerified` across updates:** The update form does not let users change photo verification status, so the service must copy this from the latest version. Do not default it to `false` in the new version.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Field-level encryption of insurance PHI | Custom encrypt/decrypt wrappers | `SensitiveDataConverter` (already built) with `@Convert` annotation | AES-256-GCM already implemented; handles null values, base64 encoding, IV management |
| Audit trail for insurance updates | Custom audit table or manual timestamps | `@LastModifiedDate` + `@LastModifiedBy` via Spring Data JPA `AuditingEntityListener` | Already configured globally via `AuditorAwareConfig`; zero code needed |
| Object-level permission checking | Custom role checks in service methods | `PatientPermissionEvaluator` (already built) via `@PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")` | Infrastructure fully in place; just wire the annotation |
| Domain event publishing | Custom callback lists, thread-local storage | `ApplicationEventPublisher.publishEvent()` + `@TransactionalEventListener(AFTER_COMMIT)` | Spring guarantees delivery after commit; easy to upgrade to broker later |
| Version number calculation | Manual sequence table, application-level counter | `findMaxVersionByBusinessId()` query + increment in service | Single source of truth; concurrent updates handled by DB unique constraint on (businessId, version) |
| Phone number validation for contacts | Custom regex in service | Existing `@ValidPhoneNumber` annotation + `PhoneNumberValidator` | Already implemented; reuse on all new DTOs |
| HTTP 400 validation error formatting | Custom error response DTOs | Existing `GlobalExceptionHandler` with RFC 7807 `ProblemDetail` | Any `@Valid` failure on new DTOs automatically produces correct error format |

**Key insight:** Phase 2 is an extension phase, not a greenfield phase. The hard parts (encryption, auditing, permission evaluation, event infrastructure) are already built. Phase 2 tasks should be expressed as "wire X to Y" and "add new endpoint that follows pattern Z" rather than building new infrastructure.

---

## Common Pitfalls

### Pitfall 1: Concurrent Update Race Condition on Version Number

**What goes wrong:** Two receptionists simultaneously open the same patient's edit form, both fetch `version = 3`, both increment to `version = 4`, and both attempt to save. The first save succeeds; the second violates the `UNIQUE(business_id, version)` constraint (from V002 migration `uk_patients_business_version`). Without proper handling, this results in a 500 error with an opaque database error message.

**Why it happens:** The read-increment-insert sequence in `updatePatient()` is not atomic. Between the `findMaxVersionByBusinessId()` read and the `save()`, another thread can complete the same sequence.

**How to avoid:** The database unique constraint already provides the safety net. The application must catch `DataIntegrityViolationException` and translate it to a meaningful 409 Conflict response with a message like "Patient was updated concurrently. Please reload and try again." Add this to `GlobalExceptionHandler`.

**Warning signs:** `DataIntegrityViolationException` in logs mentioning `uk_patients_business_version`, especially during load testing or high-traffic periods.

### Pitfall 2: @TransactionalEventListener Without @Async Blocks Commit

**What goes wrong:** If the `PatientUpdatedEventListener` does significant work (network call, heavy computation) synchronously in `@TransactionalEventListener(AFTER_COMMIT)`, it blocks the HTTP response thread until the listener completes. A 200ms patient update becomes a 2-second update because the "fire and forget" event is actually "fire and block."

**Why it happens:** `@TransactionalEventListener` with `AFTER_COMMIT` runs synchronously on the same thread by default. It executes after the commit but before the HTTP response returns to the client.

**How to avoid:** For any non-trivial listener work, add `@Async` to the listener method. Spring will execute the listener on a separate thread pool, and the HTTP response returns immediately. Enable async processing with `@EnableAsync` on a configuration class.

**Warning signs:** Patient update endpoint latency equals database write latency + listener latency. Test with a `Thread.sleep()` in the listener to confirm blocking behavior.

### Pitfall 3: SensitiveDataConverter on Column Width — Encrypted Length Overflow

**What goes wrong:** A policy number "ABC123" is 6 characters plaintext, but AES-256-GCM with a 12-byte IV prepended and base64-encoded produces approximately 50-70 characters. If the database column is defined as `VARCHAR(20)` to match "policy number formats," encrypted storage fails with a data truncation error.

**Why it happens:** Developers look at the plaintext size ("policy numbers are 20 chars") and define the column accordingly, not accounting for encryption overhead.

**How to avoid:** Define all encrypted columns as `VARCHAR(512)` minimum. The `SensitiveDataConverter` adds 12 bytes IV + 16 bytes GCM tag + base64 encoding overhead. Formula: `ceil((plaintext_max_bytes + 28) * 4/3)`. For a 50-char policy number: `ceil(78 * 4/3) = 104`; use 512 for safety.

**Warning signs:** `DataTruncationException` in logs, save failures on insurance records, inconsistent behavior based on policy number length.

### Pitfall 4: UpdatePatientRequest Reusing RegisterPatientRequest With @AssertTrue on photoIdVerified

**What goes wrong:** If `UpdatePatientRequest` copies `RegisterPatientRequest` including the `@AssertTrue(message = "Photo ID must be verified before registration")` on `photoIdVerified`, every update request that doesn't explicitly set `photoIdVerified = true` will fail validation with a confusing error message.

**Why it happens:** Developers clone the registration DTO for the update DTO and forget that photo verification is a registration-time check, not an update-time check. UPD-03 and UPD-04 require patient ID and registration date to be read-only; `photoIdVerified` should also be preserved from the current version, not sent in the update request.

**How to avoid:** `UpdatePatientRequest` must NOT include `photoIdVerified`, `patientId`, or `registeredAt` fields. The service copies `photoIdVerified` from the current patient version. The DTO contains only updateable demographics.

**Warning signs:** 400 validation errors on all patient update requests in testing.

### Pitfall 5: @CreatedBy Not Capturing the Updating User on New Patient Versions

**What goes wrong:** The new patient version row's `created_by` should contain the user who made the update (e.g., "receptionist_jane"). But if the Spring Security context is not set up correctly in the test or the request path, `AuditorAwareConfig.getCurrentAuditor()` may return "system" or "anonymousUser."

**Why it happens:** In tests using `@SpringBootTest` without setting up a `SecurityContext`, or in endpoints that are accidentally not requiring authentication, the `SecurityContextHolder` is empty.

**How to avoid:** Ensure all mutating endpoints have `@PreAuthorize` (which forces authentication). In tests, use `@WithMockUser(roles = {"RECEPTIONIST"})` from `spring-security-test` (already in pom.xml). Verify `created_by` is set correctly in integration tests by asserting the new version row's `created_by` equals the test user.

**Warning signs:** `created_by = "system"` or `created_by = "anonymousUser"` in patient version rows after authenticated updates.

### Pitfall 6: Emergency Contact Update Without Business-ID Ownership Check

**What goes wrong:** A PATCH/PUT request to `/api/v1/patients/{businessId}/emergency-contacts/{contactId}` uses `contactId` from another patient's record. The service fetches the contact by `contactId` and updates it without verifying it belongs to the `businessId` in the URL.

**Why it happens:** `EmergencyContactRepository.findById(contactId)` finds any contact regardless of which patient it belongs to. Developers assume the URL hierarchy implies the check.

**How to avoid:** The `EmergencyContactService` must always verify `contact.getPatientBusinessId().equals(businessId)` before performing any update or delete. If the ownership check fails, throw `AccessDeniedException` which the existing `GlobalExceptionHandler` converts to 403.

**Warning signs:** Receptionist for Patient A accidentally modifies emergency contacts of Patient B through a crafted URL.

### Pitfall 7: patientId in the Response Changes on Every Update

**What goes wrong:** Phase 1 `PatientDetailResponse.patientId` shows the `patient_id` of the latest version row (e.g., `P2026000003` for version 1, then `P2026000015` for version 2 after update). Frontend code that saves the `patientId` from the profile view and then uses it to navigate to the "edit" page may try to `GET /api/v1/patients/P2026000015` expecting a path-by-patientId endpoint, which doesn't exist (the endpoint uses `businessId`).

**Why it happens:** `patientId` is generated fresh for every new version insert (by `PatientIdGenerator`). It's a record-level key, not a patient-level key. The patient-level key is `businessId` (UUID).

**How to avoid:** All Phase 2 endpoints use `businessId` (UUID) as the URL path parameter — consistent with Phase 1's `GET /api/v1/patients/{businessId}`. The `patientId` in the response is informational only. Document this clearly. Consider whether `patientId` in `PatientDetailResponse` should show the version-1 patientId (the "original" patient ID) or always show the latest. Current Phase 1 behavior shows latest — preserve this.

**Warning signs:** 404 errors after patient updates when frontend uses `patientId` to re-fetch the patient profile.

---

## Code Examples

Verified patterns from existing codebase and official sources:

### V003 Flyway Migration: Insurance Table

```sql
-- V003__create_insurance_schema.sql
-- INS-01, INS-03: Insurance information linked to patient business_id
-- Follows same pattern as emergency_contacts (no FK to patients due to event-sourced model)

CREATE TYPE coverage_type AS ENUM ('HMO', 'PPO', 'EPO', 'POS', 'HDHP', 'MEDICAID', 'MEDICARE', 'OTHER');

CREATE TABLE insurance (
    id BIGSERIAL PRIMARY KEY,
    patient_business_id UUID NOT NULL,

    -- Non-PHI fields (not encrypted — searchable)
    provider_name VARCHAR(255) NOT NULL,
    coverage_type VARCHAR(50) NOT NULL,  -- Store as VARCHAR to match @Enumerated(STRING)
    is_active BOOLEAN DEFAULT true,

    -- PHI fields — encrypted at rest by SensitiveDataConverter (AES-256-GCM)
    -- Column width 512 to accommodate IV (12 bytes) + ciphertext + base64 overhead
    policy_number VARCHAR(512) NOT NULL,
    group_number VARCHAR(512),

    -- Audit fields (mutable table — uses both created and modified tracking)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(255)
);

CREATE INDEX idx_insurance_patient ON insurance(patient_business_id);
CREATE INDEX idx_insurance_active ON insurance(patient_business_id, is_active);

COMMENT ON TABLE insurance IS 'Patient insurance information. policy_number and group_number are encrypted PHI.';
COMMENT ON COLUMN insurance.policy_number IS 'AES-256-GCM encrypted via SensitiveDataConverter';
COMMENT ON COLUMN insurance.group_number IS 'AES-256-GCM encrypted via SensitiveDataConverter';
```

### UpdatePatientRequest DTO

```java
// Source: RegisterPatientRequest pattern (existing) minus read-only fields
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePatientRequest {
    // EXCLUDED: patientId (UPD-03 — read-only), registeredAt (UPD-04 — read-only)
    // EXCLUDED: photoIdVerified — preserved from current version, not updated by form
    // EXCLUDED: status — use dedicated PATCH /status endpoint

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

    @Size(max = 255) private String addressLine1;
    @Size(max = 255) private String addressLine2;
    @Size(max = 100) private String city;
    @Size(max = 50)  private String state;

    @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "Invalid ZIP code format")
    private String zipCode;
}
```

### UpdateStatusRequest DTO

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusRequest {
    @NotNull(message = "Status is required")
    private PatientStatus status;  // ACTIVE or INACTIVE
}
```

### InsuranceDto (Request + Response)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceDto {
    private Long id;  // null on create, present on response and update

    @NotBlank(message = "Insurance provider name is required")
    @Size(max = 255)
    private String providerName;

    @NotBlank(message = "Policy number is required")
    @Pattern(regexp = "^[A-Za-z0-9\\-]{3,50}$",
             message = "Policy number must be 3-50 alphanumeric characters")
    private String policyNumber;  // plaintext in DTO — SensitiveDataConverter encrypts at JPA layer

    @Pattern(regexp = "^[A-Za-z0-9\\-]{0,50}$",
             message = "Group number must be 0-50 alphanumeric characters")
    private String groupNumber;

    @NotNull(message = "Coverage type is required")
    private CoverageType coverageType;

    private Boolean isActive;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
}
```

### Concurrent Update Exception Handler Addition

```java
// Add to GlobalExceptionHandler.java
@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
    DataIntegrityViolationException ex,
    WebRequest request
) {
    // Check if it's a version conflict (concurrent update)
    if (ex.getMessage() != null && ex.getMessage().contains("uk_patients_business_version")) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Patient was updated concurrently. Please reload the patient profile and try again."
        );
        problemDetail.setTitle("Concurrent Update Conflict");
        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/concurrent-update"));
        problemDetail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    // Other integrity violations — generic 409
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Data integrity constraint violated"
    );
    problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/conflict"));
    problemDetail.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
}
```

---

## State of the Art

| Old Approach | Current Approach | Phase Relevance |
|--------------|------------------|-----------------|
| Mutable patient records with `UPDATE` SQL | Immutable event-sourced versioning — INSERT only | Core constraint for Phase 2; every update = new row |
| External broker (Kafka/RabbitMQ) for all events | Spring `ApplicationEventPublisher` + `@TransactionalEventListener` for in-process events | UPD-10 can be satisfied without broker infrastructure in Phase 2 |
| Role-only `@PreAuthorize("hasRole()")` | Object-level `@PreAuthorize("hasPermission()")` with `PermissionEvaluator` | Phase 2 wires the existing evaluator that Phase 1 left un-wired |
| Manual audit timestamp tracking | Spring Data JPA `@LastModifiedDate` / `@LastModifiedBy` via `AuditingEntityListener` | Applies to Insurance entity (mutable); not applicable to Patient (immutable) |
| Plaintext PHI in database | AES-256-GCM field-level encryption via `SensitiveDataConverter` | Insurance policy/group number must use `@Convert(converter = SensitiveDataConverter.class)` |

**Deprecated/outdated for this phase:**
- `@EventListener` (non-transactional): Do NOT use for the `PatientUpdatedEvent` handler — it fires before the transaction commits, causing the downstream consumer to potentially read stale data.
- `patientRepository.save(existingManagedEntity)` with field mutations: The `@Immutable` annotation will cause Hibernate to silently ignore the UPDATE; the trigger will also block it. Always create a new `Patient` instance via builder.

---

## Open Questions

### 1. Insurance: Upsert or Multiple Records?

**What we know:** INS-03 says "stores insurance as part of patient record" and INS-05 says "update insurance information." This suggests one active insurance record per patient.

**What's unclear:** Can a patient have multiple active insurance policies simultaneously (primary + secondary)? The requirements (INS-01: "capture insurance information" singular) suggest one at a time.

**Recommendation:** Implement as a single active record per patient: `POST` creates if none exists, `PUT` updates existing active record. Use `isActive` flag. If multiple insurances are needed in the future, the schema (one row per record linked by `patientBusinessId`) already supports it without migration changes. Phase 2 planner should implement single-active-record semantics.

### 2. PatientUpdated Event: What Data to Include?

**What we know:** UPD-10 says "publishes PatientUpdated event to message broker." No downstream consumers exist yet. The event needs to be useful when future modules subscribe.

**What's unclear:** Should the event carry just the `businessId` (let consumers re-fetch), or should it carry the full new patient state? Carrying full state avoids a second DB read for consumers but makes the event payload large.

**Recommendation:** Carry `businessId`, `newVersion`, `updatedBy`, `occurredAt`, and a list of `changedFields` (String list). This allows downstream consumers to filter on what changed without re-fetching, while keeping the payload small. Full denormalization can be added later when a specific consumer requires it.

### 3. EmergencyContact: Does Updating a Contact Require a New Patient Version?

**What we know:** Emergency contacts are linked via `patientBusinessId` and are mutable (not event-sourced). Phase 1 decided emergency contacts do NOT trigger new patient versions.

**What's unclear:** HIPAA audit requirements: does modifying an emergency contact need to appear in the patient version history? The `audit_logs` table already captures all PHI access via `@Audited`. Is that sufficient?

**Recommendation:** Keep emergency contacts mutable (no new patient version on contact change). The `@Audited(action = "UPDATE")` on the emergency contact endpoint will log the change in `audit_logs` with user, timestamp, and resource. This satisfies EMR-04's "audit trail" requirement. Only demographic changes to the patient entity itself require new version rows.

### 4. Response Assembly for registeredAt: One Extra Query per Request

**What we know:** Building `PatientDetailResponse` with correct `registeredAt` (from version 1) and `lastModifiedAt` (from latest version) requires fetching both the earliest and latest versions. This adds one extra query per patient fetch.

**What's unclear:** Is the performance impact acceptable? `findFirstVersionByBusinessId()` with `ORDER BY version ASC LIMIT 1` on indexed `business_id` is O(log n) — sub-millisecond.

**Recommendation:** Add the `findFirstVersionByBusinessId()` query and accept the extra round trip. The performance impact is negligible. Alternatively, cache the version-1 metadata (registeredAt, registeredBy, originalPatientId) in a separate `patient_registrations` table at registration time — but that adds complexity that isn't warranted at this scale.

---

## Sources

### Primary (HIGH confidence)

- Existing codebase — `Patient.java`, `PatientRepository.java`, `PatientService.java`, `PatientController.java`, `PatientPermissionEvaluator.java`, `SensitiveDataConverter.java`, `EncryptionService.java`, `GlobalExceptionHandler.java`, `V001`/`V002` migrations — read directly
- `MethodSecurityConfig.java` — confirms `DefaultMethodSecurityExpressionHandler` registers `PatientPermissionEvaluator`, so `hasPermission()` expressions work immediately
- `AuditInterceptor.java` — confirms `@Audited(action = "UPDATE")` will generate audit log entries automatically
- `EmergencyContactRepository.java` — confirms all query methods exist for Phase 2 contact CRUD

### Secondary (MEDIUM confidence)

- [Spring Events | Baeldung](https://www.baeldung.com/spring-events) — `ApplicationEventPublisher` + `@TransactionalEventListener` AFTER_COMMIT pattern
- [Transaction Synchronization and @TransactionalEventListener | DZone](https://dzone.com/articles/transaction-synchronization-and-spring-application) — AFTER_COMMIT cannot write to DB; use REQUIRES_NEW propagation or @Async for any post-event DB writes
- [Introduction to Spring Method Security | Baeldung](https://www.baeldung.com/spring-security-method-security) — `hasPermission()` expression delegates to registered `PermissionEvaluator`
- [Database column-level encryption with Spring Data JPA | sultanov.dev](https://sultanov.dev/blog/database-column-level-encryption-with-spring-data-jpa/) — `AttributeConverter` encryption pattern for JPA fields
- [HTTP PUT vs PATCH in a REST API | Baeldung](https://www.baeldung.com/http-put-patch-difference-spring) — PUT for full resource update (demographics), PATCH for partial (status only)
- [HIPAA Encryption Requirements 2026 | HIPAA Journal](https://www.hipaajournal.com/hipaa-encryption-requirements/) — PHI encryption requirements including insurance policy numbers

### Tertiary (LOW confidence — verified against codebase but not official docs)

- Pattern for `findMaxVersionByBusinessId()` and version increment is logical inference from existing `DISTINCT ON` queries — low risk since the unique constraint provides the safety net
- Insurance coverage type enumeration (HMO, PPO, etc.) is based on common US health insurance types — not validated against a specific requirements document

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all Phase 2 libraries are already in pom.xml; no new dependencies required
- Architecture: HIGH — event-sourced insert pattern, permission evaluator wiring, and insurance design all confirmed against actual codebase
- Insurance encryption: HIGH — `SensitiveDataConverter` and `EncryptionService` code read directly; column width calculation is arithmetic
- Domain events: MEDIUM-HIGH — `ApplicationEventPublisher` + `@TransactionalEventListener` pattern confirmed via multiple sources; AFTER_COMMIT behavior constraints verified
- Emergency contact CRUD: HIGH — `EmergencyContactRepository` methods exist; pattern is additive to existing Phase 1 service
- Concurrent update handling: HIGH — `UNIQUE(business_id, version)` constraint exists in V002 migration; exception type is standard Spring
- Pitfalls: HIGH — all pitfalls derive from reading actual code and known Spring behaviors

**Research date:** 2026-02-20
**Valid until:** 2026-05-20 (90 days — stable domain; Spring Boot 3.4.x LTS release cadence)

**Key decisions resolved by this research:**
1. No new Maven dependencies needed for Phase 2
2. `PatientUpdatedEvent` uses `ApplicationEventPublisher` — no broker needed
3. Insurance uses `SensitiveDataConverter` on policy/group number fields with 512-char columns
4. Emergency contact CRUD reuses existing repository; no new versioning
5. `@PreAuthorize("hasPermission(..., 'Patient', 'write')")` is the correct wiring for mutating endpoints
6. `registeredAt` requires fetching version 1 row; `lastModifiedAt` comes from latest version row
