# Architecture Research: Patient Management Module

**Domain:** Hospital Patient Management System (Microservices-based)
**Researched:** 2026-02-19
**Confidence:** MEDIUM

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     API Gateway Layer                            │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Spring Cloud Gateway / Kong                              │   │
│  │  - Authentication (OAuth 2.0 / JWT)                       │   │
│  │  - Rate Limiting & Traffic Management                     │   │
│  │  - FHIR Compliance Routing                               │   │
│  │  - Audit Logging Interceptor                             │   │
│  └───────────────────────┬──────────────────────────────────┘   │
└────────────────────────────┼──────────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
┌────────▼────────┐  ┌──────▼───────┐  ┌───────▼────────┐
│  Patient Module │  │ Auth Module  │  │ Appointments   │
│  (Spring Boot)  │  │ (External)   │  │ (External)     │
├─────────────────┤  └──────────────┘  └────────────────┘
│ Controllers:    │           │                 │
│ - PatientCtrl   │           │                 │
│ - DemogrCtrl    │    ┌──────▼─────────────────▼────────┐
│ - MedHistCtrl   │    │   Service Registry (Eureka)      │
├─────────────────┤    └──────────────────────────────────┘
│ Service Layer:  │
│ - PatientSvc    │    ┌────────────────────────────────┐
│ - ValidationSvc │◄───┤  Message Broker (RabbitMQ)     │
│ - NotifySvc     │    │  - Patient.Created             │
├─────────────────┤    │  - Patient.Updated             │
│ Data Layer:     │    │  - Patient.Deactivated         │
│ - JPA Repos     │    └────────────────────────────────┘
│ - Entities      │
└────────┬────────┘
         │
    ┌────▼──────────────────────────────────┐
    │   PostgreSQL Database                  │
    │   ┌──────────────┬────────────────┐   │
    │   │ patients     │ audit_logs     │   │
    │   │ demographics │ consent_logs   │   │
    │   │ addresses    │ access_logs    │   │
    │   │ contacts     │ phi_access     │   │
    │   └──────────────┴────────────────┘   │
    └───────────────────────────────────────┘
         │
    ┌────▼──────────────────────────────────┐
    │   Separate Audit Storage              │
    │   (Immutable, Long-term retention)    │
    └───────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| **API Gateway** | Single entry point, authentication, routing, rate limiting, cross-cutting concerns | Spring Cloud Gateway or Kong with JWT validation, FHIR-aware routing |
| **Service Registry** | Service discovery, health checks, load balancing | Netflix Eureka or Consul for dynamic service location |
| **Patient Service** | CRUD operations on patient data, validation, PHI protection | Spring Boot 3.x with Spring Data JPA, PostgreSQL |
| **Validation Service** | Business rule validation, duplicate detection, data quality | Embedded within Patient Service, can be extracted for reuse |
| **Notification Service** | Async notifications to other modules via events | Event publisher using RabbitMQ/Kafka for loose coupling |
| **Audit Service** | Immutable audit trail, PHI access logging, HIPAA compliance | Separate write-only service, append-only database |
| **Database** | Persistent storage with encryption at rest | PostgreSQL 14+ with row-level security, encrypted volumes |
| **Message Broker** | Asynchronous inter-service communication | RabbitMQ or Apache Kafka for event-driven architecture |

## Recommended Project Structure

```
patient-service/
├── src/main/java/com/hospital/patient/
│   ├── config/                    # Security, JPA, messaging configs
│   │   ├── SecurityConfig.java    # Spring Security with RBAC
│   │   ├── AuditConfig.java       # Audit logging interceptors
│   │   └── FhirConfig.java        # FHIR resource mappings
│   ├── controller/                # REST API endpoints
│   │   ├── PatientController.java       # /api/v1/patients
│   │   ├── DemographicsController.java  # /api/v1/patients/{id}/demographics
│   │   └── MedicalHistoryController.java
│   ├── service/                   # Business logic layer
│   │   ├── PatientService.java
│   │   ├── ValidationService.java
│   │   └── EventPublisherService.java
│   ├── repository/                # Data access layer
│   │   ├── PatientRepository.java
│   │   ├── DemographicsRepository.java
│   │   └── AuditLogRepository.java
│   ├── entity/                    # JPA entities
│   │   ├── Patient.java
│   │   ├── Demographics.java
│   │   ├── Address.java
│   │   └── ContactInfo.java
│   ├── dto/                       # Data transfer objects
│   │   ├── request/               # API request models
│   │   └── response/              # API response models
│   ├── mapper/                    # Entity-DTO mapping
│   │   └── PatientMapper.java     # MapStruct or manual
│   ├── event/                     # Event definitions
│   │   ├── PatientCreatedEvent.java
│   │   └── PatientUpdatedEvent.java
│   ├── exception/                 # Exception handling
│   │   ├── GlobalExceptionHandler.java
│   │   └── PatientNotFoundException.java
│   ├── security/                  # Security components
│   │   ├── JwtAuthFilter.java
│   │   ├── RoleBasedAccessControl.java
│   │   └── AuditInterceptor.java
│   └── fhir/                      # FHIR resource adapters
│       ├── FhirPatientAdapter.java
│       └── FhirResourceMapper.java
├── src/main/resources/
│   ├── application.yml            # Spring Boot configuration
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── db/migration/              # Flyway database migrations
│       ├── V1__create_patients_table.sql
│       ├── V2__create_audit_tables.sql
│       └── V3__add_rbac_indexes.sql
└── src/test/
    ├── unit/                      # Unit tests
    ├── integration/               # Integration tests
    └── performance/               # Load tests
```

### Structure Rationale

- **config/**: Centralized configuration enables consistency across security, audit, and integration patterns
- **controller/**: RESTful endpoints separate presentation from business logic, following single responsibility
- **service/**: Business logic isolation enables reuse, testability, and clear boundaries with external modules
- **repository/**: Data access abstraction allows database independence and simplified testing
- **dto/**: Request/response separation from entities protects internal model, enables API versioning
- **event/**: Explicit event definitions document inter-module contracts and enable event sourcing
- **security/**: Security as a first-class concern with dedicated components for audit, authentication, authorization
- **fhir/**: FHIR interoperability layer enables external EHR integration without coupling core domain model

## Architectural Patterns

### Pattern 1: Database-per-Service with Event-Driven Synchronization

**What:** Each microservice maintains its own PostgreSQL database. Changes propagate to other services via events published to RabbitMQ/Kafka, not direct database access.

**When to use:** In microservices architectures where services need data isolation and independent scalability. Patient Service owns patient core data; other services subscribe to Patient.Created/Patient.Updated events to maintain local denormalized copies.

**Trade-offs:**
- **Pros:** Strong service boundaries, independent deployment, prevents cascade failures, enables polyglot persistence
- **Cons:** Eventual consistency challenges, increased complexity in distributed transactions, data duplication
- **Mitigation:** Use Saga pattern for multi-service workflows, implement idempotent event handlers, maintain clear event schemas

**Example:**
```java
// Patient Service publishes event
@Service
public class PatientService {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Transactional
    public Patient createPatient(PatientRequest request) {
        Patient patient = patientRepository.save(mapToEntity(request));

        // Publish event for other services
        PatientCreatedEvent event = new PatientCreatedEvent(
            patient.getId(),
            patient.getFullName(),
            patient.getDateOfBirth()
        );
        rabbitTemplate.convertAndSend("patient.exchange",
            "patient.created", event);

        return patient;
    }
}

// Appointment Service subscribes
@RabbitListener(queues = "appointment.patient.queue")
public void handlePatientCreated(PatientCreatedEvent event) {
    // Update local denormalized patient cache
    patientCacheRepository.save(new PatientCache(event));
}
```

### Pattern 2: API Gateway with JWT-Based Authentication

**What:** Spring Cloud Gateway acts as single entry point, validates JWT tokens from Auth Service, enforces RBAC, logs all PHI access, and routes to appropriate microservices.

**When to use:** Always in healthcare microservices. Gateway provides centralized authentication, authorization audit logging, and HIPAA compliance without duplicating logic across services.

**Trade-offs:**
- **Pros:** Centralized security, consistent audit logging, single TLS termination, rate limiting
- **Cons:** Single point of failure (mitigate with clustering), added latency (1-5ms typical), gateway as bottleneck
- **Mitigation:** Deploy multiple gateway instances behind load balancer, implement circuit breakers, cache JWT validation

**Example:**
```java
// application.yml for Spring Cloud Gateway
spring:
  cloud:
    gateway:
      routes:
        - id: patient-service
          uri: lb://PATIENT-SERVICE
          predicates:
            - Path=/api/v1/patients/**
          filters:
            - name: JwtAuthenticationFilter
            - name: AuditLoggingFilter
            - name: RateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200

// Custom filter for PHI access logging
@Component
public class AuditLoggingFilter implements GatewayFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                            GatewayFilterChain chain) {
        String userId = extractUserFromJwt(exchange);
        String resource = exchange.getRequest().getPath().value();

        // Log PHI access for HIPAA compliance
        auditService.logAccess(userId, resource, timestamp);

        return chain.filter(exchange);
    }
}
```

### Pattern 3: FHIR Facade Pattern

**What:** Adapter layer that translates between internal domain model and HL7 FHIR R4 resources. Patient Service exposes both native REST API (/api/v1/patients) and FHIR-compliant endpoints (/fhir/Patient).

**When to use:** When building healthcare systems that need to integrate with external EHRs, HIEs, or comply with interoperability mandates (21st Century Cures Act). Not needed if system is fully internal.

**Trade-offs:**
- **Pros:** Standards compliance, easy integration with Epic/Cerner, future-proof interoperability
- **Cons:** FHIR complexity, mapping overhead, version management (R4 vs R5)
- **Mitigation:** Use HAPI FHIR library for heavy lifting, separate FHIR endpoints from internal API, version both independently

**Example:**
```java
// FHIR endpoint using HAPI FHIR
@RestController
@RequestMapping("/fhir")
public class FhirPatientController {
    @Autowired
    private FhirPatientAdapter fhirAdapter;

    @GetMapping("/Patient/{id}")
    public ResponseEntity<String> getFhirPatient(@PathVariable String id) {
        Patient internalPatient = patientService.findById(id);

        // Convert to FHIR R4 Patient resource
        org.hl7.fhir.r4.model.Patient fhirPatient =
            fhirAdapter.toFhirResource(internalPatient);

        // Serialize to JSON
        FhirContext ctx = FhirContext.forR4();
        String fhirJson = ctx.newJsonParser()
            .encodeResourceToString(fhirPatient);

        return ResponseEntity.ok(fhirJson);
    }
}

// Adapter class
@Component
public class FhirPatientAdapter {
    public org.hl7.fhir.r4.model.Patient toFhirResource(Patient patient) {
        org.hl7.fhir.r4.model.Patient fhirPatient =
            new org.hl7.fhir.r4.model.Patient();
        fhirPatient.setId(patient.getId().toString());
        fhirPatient.addName()
            .setFamily(patient.getLastName())
            .addGiven(patient.getFirstName());
        fhirPatient.setBirthDate(patient.getDateOfBirth());
        // Map other fields...
        return fhirPatient;
    }
}
```

### Pattern 4: Audit-First Architecture with Immutable Logs

**What:** Every operation that creates, reads, updates, or deletes PHI generates an immutable audit log entry in a separate, append-only audit database. Logs include who, what, when, where, and why (if available).

**When to use:** Required for HIPAA compliance. Non-negotiable for any healthcare system handling PHI. Audit logs must be tamper-proof, retained for 6+ years, and available for compliance audits.

**Trade-offs:**
- **Pros:** HIPAA compliance, forensic analysis, breach detection, non-repudiation
- **Cons:** Storage costs, performance overhead (1-2ms per operation), complexity in distributed systems
- **Mitigation:** Async audit logging, dedicated audit service, compressed storage, separate audit database

**Example:**
```java
// Audit interceptor using Spring AOP
@Aspect
@Component
public class AuditInterceptor {
    @Autowired
    private AuditService auditService;

    @AfterReturning(pointcut = "@annotation(AuditLog)",
                    returning = "result")
    public void auditMethodCall(JoinPoint joinPoint, Object result) {
        String userId = SecurityContextHolder.getContext()
            .getAuthentication().getName();
        String action = joinPoint.getSignature().getName();
        String resourceType = extractResourceType(joinPoint);
        String resourceId = extractResourceId(result);

        AuditEntry entry = AuditEntry.builder()
            .timestamp(Instant.now())
            .userId(userId)
            .action(action)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .ipAddress(getCurrentIpAddress())
            .userAgent(getCurrentUserAgent())
            .build();

        // Async to avoid blocking business logic
        auditService.logAsync(entry);
    }
}

// Usage in controller
@AuditLog
@GetMapping("/{id}")
public PatientResponse getPatient(@PathVariable Long id) {
    return patientService.findById(id);
}
```

### Pattern 5: Saga Pattern for Distributed Transactions

**What:** Coordinate multi-service workflows (e.g., patient registration that triggers account creation, appointment slot allocation, billing setup) using compensating transactions instead of two-phase commit.

**When to use:** When a business process spans multiple microservices and requires consistency. Example: "Create Patient" workflow that must also create records in Billing and Appointments services.

**Trade-offs:**
- **Pros:** Avoids distributed transactions, maintains service autonomy, handles partial failures
- **Cons:** Increased complexity, requires compensating actions, eventual consistency only
- **Mitigation:** Use saga orchestration library (e.g., Temporal, Camunda), clear rollback logic, idempotent operations

**Example:**
```java
// Orchestration-based Saga
@Service
public class PatientRegistrationSaga {
    @Autowired
    private PatientService patientService;
    @Autowired
    private BillingServiceClient billingClient;
    @Autowired
    private AppointmentServiceClient appointmentClient;

    @Transactional
    public RegistrationResult registerPatient(RegistrationRequest request) {
        Long patientId = null;
        String billingAccountId = null;

        try {
            // Step 1: Create patient record
            patientId = patientService.createPatient(request.getPatientData());

            // Step 2: Create billing account
            billingAccountId = billingClient.createAccount(patientId);

            // Step 3: Initialize appointment slots
            appointmentClient.initializePatient(patientId);

            return RegistrationResult.success(patientId);

        } catch (Exception e) {
            // Compensating transactions (rollback)
            if (billingAccountId != null) {
                billingClient.deleteAccount(billingAccountId);
            }
            if (patientId != null) {
                patientService.deletePatient(patientId);
            }
            throw new RegistrationFailedException(e);
        }
    }
}
```

## Data Flow

### Patient Registration Flow

```
User (Web/Mobile)
    |
    | POST /api/v1/patients
    ↓
[API Gateway]
    |
    | 1. Validate JWT token
    | 2. Check RBAC permissions (role: ADMIN, RECEPTIONIST)
    | 3. Log access attempt
    ↓
[Patient Service]
    |
    | 4. Validate business rules (duplicate check, required fields)
    | 5. Encrypt sensitive fields (SSN, insurance)
    ↓
[PostgreSQL - patients table]
    |
    | 6. INSERT patient record (encrypted at rest)
    | 7. Write audit log (immutable, append-only)
    ↓
[Event Publisher]
    |
    | 8. Publish PatientCreatedEvent to RabbitMQ
    ↓
[Message Broker]
    |
    ├─→ [Appointments Service] (subscribe to create patient cache)
    ├─→ [Billing Service] (subscribe to initialize billing account)
    ├─→ [EMR Service] (subscribe to create medical record shell)
    └─→ [Notification Service] (subscribe to send welcome email)
```

### Patient Data Read Flow

```
User (Doctor viewing patient record)
    |
    | GET /api/v1/patients/{id}
    ↓
[API Gateway]
    |
    | 1. Validate JWT token
    | 2. Check RBAC (role: DOCTOR, NURSE, ADMIN)
    | 3. Check patient-provider relationship (authorization)
    | 4. Log PHI access (HIPAA audit)
    ↓
[Patient Service]
    |
    | 5. Query database with row-level security
    ↓
[PostgreSQL]
    |
    | 6. Return encrypted data
    ↓
[Patient Service]
    |
    | 7. Decrypt sensitive fields
    | 8. Apply field-level permissions (mask SSN if not authorized)
    | 9. Map to DTO (exclude internal fields)
    ↓
[API Gateway]
    |
    | 10. Return JSON response over TLS
    ↓
User receives patient data
```

### Patient Update with Event Propagation

```
User (Receptionist updates address)
    |
    | PUT /api/v1/patients/{id}/demographics
    ↓
[API Gateway] → Validate & Audit
    ↓
[Patient Service]
    |
    | 1. Load existing patient
    | 2. Apply updates (optimistic locking)
    | 3. Update database
    ↓
[PostgreSQL]
    |
    | 4. UPDATE patient record (version incremented)
    | 5. INSERT audit_log entry
    ↓
[Event Publisher]
    |
    | 6. Publish PatientUpdatedEvent
    |    - patientId
    |    - changedFields: ["address"]
    |    - timestamp
    ↓
[Message Broker]
    |
    ├─→ [Appointments Service] (update cached address for confirmation letters)
    ├─→ [Billing Service] (update billing address if changed)
    └─→ [EMR Service] (update patient header demographics)
```

### Key Data Flows

1. **Patient Creation:** API Gateway → Patient Service → Database → Event Broker → Downstream Services (Appointments, Billing, EMR)
2. **Patient Search:** API Gateway → Patient Service → Database (with filters/pagination) → Response
3. **Patient Deactivation:** API Gateway → Patient Service → Database (soft delete) → Event Broker → Downstream Services (cancel pending appointments, finalize billing)
4. **Audit Query:** Compliance Officer → Audit Service (separate endpoint) → Audit Database (read-only) → Report Generation

## Scaling Considerations

| Scale | Architecture Adjustments | Rationale |
|-------|--------------------------|-----------|
| **0-100 users** | Single Patient Service instance, PostgreSQL on same server, RabbitMQ optional | Monolith is simpler. Focus on features, not scaling. Use Spring profiles for easy extraction later. |
| **100-1,000 users** | 2-3 Patient Service instances behind load balancer, dedicated PostgreSQL server, introduce Redis cache for frequent reads | Horizontal scaling of stateless services. Database becomes bottleneck. Cache patient demographics (30min TTL). |
| **1,000-10,000 users** | Auto-scaling Patient Service (5-15 instances), PostgreSQL read replicas for queries, Redis cluster, separate Audit Service | Read-heavy workload (10:1 read:write). Use read replicas for searches/reports. Write to primary only. Offload audit logs to dedicated service. |
| **10,000+ users** | Regional deployment with database sharding (by hospital/region), CDN for static assets, Kafka instead of RabbitMQ for higher throughput | Geographic distribution reduces latency. Shard patient data by facility ID (most queries are single-facility). Kafka handles 100k+ events/sec. |

### Scaling Priorities

1. **First bottleneck: Database read capacity (at ~500 concurrent users)**
   - **Symptoms:** Slow patient search, timeouts on dashboard
   - **Fix:** Add PostgreSQL read replica, introduce Redis cache for hot data (recently accessed patients, current appointments)
   - **Implementation:** Spring's `@Cacheable` annotation on `PatientService.findById()`, 15-30min TTL
   - **Cost:** Minimal (read replica ~$50-100/month, Redis ~$30/month)

2. **Second bottleneck: Service instance capacity (at ~2,000 concurrent users)**
   - **Symptoms:** Increased response time, higher CPU on service instances
   - **Fix:** Horizontal auto-scaling based on CPU/memory metrics
   - **Implementation:** Kubernetes HPA (Horizontal Pod Autoscaler) targeting 70% CPU utilization
   - **Cost:** Variable based on usage, typically 3-5x base infrastructure cost

3. **Third bottleneck: Database write capacity (at ~5,000 concurrent users with heavy updates)**
   - **Symptoms:** Write lock contention, slow patient updates
   - **Fix:** Connection pooling optimization (HikariCP), batch operations, consider eventual consistency for non-critical fields
   - **Implementation:** Increase connection pool size, use async event processing for non-critical updates
   - **Cost:** Upgrade database instance (~$200-500/month for higher tier)

### Right-Sizing Recommendations

For **50,000 patients with 100 concurrent users** (as specified in project context):
- **Patient Service:** 2-3 instances (4GB RAM, 2 vCPU each)
- **Database:** PostgreSQL (16GB RAM, 4 vCPU, 500GB SSD)
- **Cache:** Redis (2GB RAM) for frequently accessed patient records
- **Message Broker:** RabbitMQ (2GB RAM) sufficient for event volume
- **Expected Load:** ~200 requests/sec peak, ~50 requests/sec average
- **Cost Estimate:** $400-600/month cloud infrastructure (AWS/Azure)

## Anti-Patterns

### Anti-Pattern 1: Direct Database Access Between Services

**What people do:** Appointments Service directly queries Patient Service's PostgreSQL database to get patient names for appointment confirmations.

**Why it's wrong:**
- Breaks service encapsulation and creates tight coupling
- Prevents database schema evolution (Patient Service can't change schema without breaking Appointments)
- Bypasses security/audit controls (no record of who accessed PHI)
- HIPAA violation (no audit trail of PHI access across services)

**Do this instead:**
- Appointments Service calls Patient Service REST API: `GET /api/v1/patients/{id}/demographics`
- Or: Appointments maintains denormalized copy of patient names via event subscription
- Or: Use API Gateway to enforce audit logging on all cross-service calls

**Example:**
```java
// WRONG: Direct database access
@Service
public class AppointmentService {
    @Autowired
    private JdbcTemplate patientDatabase; // Don't do this!

    public AppointmentDetails getAppointment(Long id) {
        String patientName = patientDatabase.queryForObject(
            "SELECT name FROM patients.patient WHERE id = ?",
            String.class, patientId);
        // This bypasses Patient Service completely
    }
}

// RIGHT: API-based access
@Service
public class AppointmentService {
    @Autowired
    private PatientServiceClient patientClient; // Use Feign/RestTemplate

    public AppointmentDetails getAppointment(Long id) {
        PatientDemographics patient = patientClient.getPatient(patientId);
        // Proper audit trail, encapsulation, security
    }
}
```

### Anti-Pattern 2: Shared Database for "Convenience"

**What people do:** Multiple services (Patient, Appointments, Billing) share a single database with different schemas to "simplify" data access and avoid microservices complexity.

**Why it's wrong:**
- Negates primary benefit of microservices (independent deployment)
- Shared database becomes single point of failure
- Can't scale services independently (all bound to same database performance)
- Schema changes require coordination across teams
- Difficult to enforce service boundaries (temptation to bypass APIs)

**Do this instead:**
- Database-per-service pattern with event-driven synchronization
- Each service owns its data and exposes it only via APIs
- Use eventual consistency and compensating transactions
- Accept short-term data duplication for long-term flexibility

### Anti-Pattern 3: Logging PHI in Application Logs

**What people do:** Using `log.info("Processing patient: name={}, ssn={}, diagnosis={}", name, ssn, diagnosis)` for debugging.

**Why it's wrong:**
- PHI in plain text log files violates HIPAA
- Logs often go to insecure destinations (CloudWatch, Splunk without proper access controls)
- Log retention may not meet 6-year HIPAA requirement
- Developers/ops with log access may not be authorized to view PHI

**Do this instead:**
- Log only non-PHI identifiers: `log.info("Processing patient: id={}", patientId)`
- Use structured audit logs in separate, secure database for PHI access
- Implement log scrubbing/masking for any PHI before storage
- Restrict log access to authorized personnel only

**Example:**
```java
// WRONG: PHI in logs
log.info("Patient registration: name={}, ssn={}, dob={}",
    patient.getName(), patient.getSsn(), patient.getDob());

// RIGHT: Only identifiers in logs
log.info("Patient registration: patientId={}, correlationId={}",
    patient.getId(), correlationId);

// PHI access goes to audit log instead
auditService.logPhiAccess(AuditEntry.builder()
    .userId(currentUser)
    .action("PATIENT_VIEW")
    .resourceId(patient.getId())
    .resourceType("Patient")
    .timestamp(Instant.now())
    .build());
```

### Anti-Pattern 4: Synchronous Saga with Timeout Hell

**What people do:** Patient registration makes synchronous REST calls to Billing, Appointments, EMR services in sequence, waiting for each to complete. If any service times out (network issue, service down), entire registration fails.

**Why it's wrong:**
- Poor user experience (slow registration due to serial calls)
- Brittle (one service down = entire workflow fails)
- Difficult to rollback partially completed work
- Ties up threads waiting for downstream services

**Do this instead:**
- Create patient record immediately (core operation)
- Publish `PatientCreated` event asynchronously
- Downstream services process in their own time
- Use eventual consistency with compensation for failures
- User gets immediate feedback, background processing continues

**Example:**
```java
// WRONG: Synchronous saga
@PostMapping("/patients")
public ResponseEntity<?> createPatient(@RequestBody PatientRequest req) {
    Patient patient = patientService.create(req);

    // Blocking calls - slow and brittle
    billingClient.createAccount(patient.getId()); // 2 sec
    appointmentClient.initialize(patient.getId()); // 1 sec
    emrClient.createRecord(patient.getId()); // 3 sec

    return ResponseEntity.ok(patient); // User waits 6+ seconds
}

// RIGHT: Async event-driven
@PostMapping("/patients")
public ResponseEntity<?> createPatient(@RequestBody PatientRequest req) {
    Patient patient = patientService.create(req);

    // Publish event (non-blocking, <10ms)
    eventPublisher.publish(new PatientCreatedEvent(patient.getId()));

    return ResponseEntity.ok(patient); // User gets response immediately
}
```

### Anti-Pattern 5: No API Versioning Strategy

**What people do:** Change API response format (add required field, rename field, change data type) without versioning, breaking existing clients (mobile apps, integrations).

**Why it's wrong:**
- Forces all consumers to upgrade simultaneously (big bang deployment)
- Breaks mobile apps already in users' hands
- Violates API contract (consumers expect stability)
- Causes integration failures with external systems (EMR, labs)

**Do this instead:**
- URI-based versioning: `/api/v1/patients`, `/api/v2/patients`
- Maintain backward compatibility for at least 2 versions
- Deprecation warnings in headers: `X-API-Deprecated: true; sunset=2026-12-31`
- Clear migration guide for consumers

**Example:**
```java
// Version 1 (maintained for backward compatibility)
@RestController
@RequestMapping("/api/v1/patients")
public class PatientControllerV1 {
    @GetMapping("/{id}")
    public PatientResponseV1 getPatient(@PathVariable Long id) {
        // Returns old format
    }
}

// Version 2 (new features, enhanced data)
@RestController
@RequestMapping("/api/v2/patients")
public class PatientControllerV2 {
    @GetMapping("/{id}")
    public PatientResponseV2 getPatient(@PathVariable Long id) {
        // Returns new format with additional fields
    }
}
```

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| **Auth Service** | REST API + JWT validation | Patient Service validates JWT tokens issued by Auth Service. Uses public key to verify signatures. Caches user roles/permissions (5min TTL). |
| **Appointments Module** | Event-driven + REST fallback | Subscribe to `PatientCreated`, `PatientUpdated`, `PatientDeactivated` events via RabbitMQ. Use REST API for synchronous queries (e.g., "Does patient have upcoming appointments?"). |
| **EMR Module** | REST API + FHIR | Bidirectional integration. Patient Service exposes FHIR Patient resource (`/fhir/Patient/{id}`). EMR calls REST API for patient demographics. Must use API Gateway for audit trail. |
| **Billing Module** | Event-driven primarily | Subscribe to patient events for billing account creation. Patient Service does NOT call Billing directly. Billing queries Patient API for address updates. |
| **External Labs** | HL7 v2.x / FHIR | Outbound patient demographics via HL7 ADT messages or FHIR Patient resource. Requires mapping layer and message queue (e.g., Mirth Connect). |
| **Health Information Exchange (HIE)** | FHIR R4 | Read-only access to external patient records. Use SMART on FHIR for authorization. Implement retry logic and circuit breaker (external dependency). |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| **Patient Service ↔ Auth Service** | Synchronous REST (JWT validation) | Patient Service calls Auth API on first request per token, caches result. Auth Service does NOT call Patient. One-way dependency. |
| **Patient Service ↔ Appointments** | Async events + occasional sync REST | Events: `PatientCreated` (to initialize), `PatientDeactivated` (to cancel appointments). REST: Appointments calls Patient API to display patient name in scheduler. |
| **Patient Service ↔ Billing** | Async events only | Events: `PatientCreated` (create billing account), `PatientUpdated` (update billing address). No synchronous calls. Loose coupling. |
| **Patient Service ↔ EMR** | Bidirectional REST + FHIR | REST: EMR reads patient demographics for clinical context. FHIR: External EHRs query Patient resource. Use API Gateway to enforce access controls. |
| **Patient Service ↔ Audit Service** | Async message queue | Patient Service publishes audit events to dedicated queue. Audit Service consumes and stores in append-only database. Fire-and-forget pattern. |
| **Patient Service ↔ Notification Service** | Async events | Events: `PatientCreated` (welcome email), `PatientUpdated` (confirmation of changes). Notification subscribes to all patient events. Patient doesn't know about notifications. |

### API Contract Example

```yaml
# Patient Service API Contract (OpenAPI 3.0)
/api/v1/patients:
  post:
    summary: Create new patient
    security:
      - BearerAuth: [ROLE_ADMIN, ROLE_RECEPTIONIST]
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/PatientCreateRequest'
    responses:
      201:
        description: Patient created successfully
        headers:
          X-Patient-ID:
            schema:
              type: string
            description: Unique patient identifier
      400:
        description: Validation error
      401:
        description: Authentication required
      403:
        description: Insufficient permissions

/api/v1/patients/{patientId}:
  get:
    summary: Get patient by ID
    security:
      - BearerAuth: [ROLE_DOCTOR, ROLE_NURSE, ROLE_ADMIN]
    parameters:
      - name: patientId
        in: path
        required: true
        schema:
          type: integer
          format: int64
    responses:
      200:
        description: Patient found
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/PatientResponse'
      404:
        description: Patient not found
      403:
        description: Not authorized to view this patient
```

## Build Order and Dependencies

### Recommended Build Sequence

Build Patient Module in this order to minimize rework and establish stable integration points:

#### Phase 1: Core Data Model (Week 1-2)
1. **Database schema** - Create tables (patients, demographics, addresses, contacts)
2. **JPA entities** - Map domain objects to database
3. **Repository layer** - Basic CRUD operations
4. **Why first:** Other layers depend on data model. Changes are expensive later.

#### Phase 2: Security Foundation (Week 2-3)
5. **JWT validation filter** - Integrate with Auth Service
6. **RBAC implementation** - Role-based access control
7. **Audit logging interceptor** - HIPAA compliance from day one
8. **Why next:** Security touches everything. Retrofit is harder than build-in.

#### Phase 3: Core Business Logic (Week 3-5)
9. **Service layer** - Business rules, validation, patient CRUD
10. **REST controllers** - API endpoints (v1)
11. **DTO mapping** - Request/response models
12. **Exception handling** - Global exception handler
13. **Why next:** Business logic depends on security and data. API depends on business logic.

#### Phase 4: Event Integration (Week 5-6)
14. **Event definitions** - PatientCreated, PatientUpdated, etc.
15. **RabbitMQ configuration** - Exchanges, queues, bindings
16. **Event publisher service** - Publish events after operations
17. **Why next:** Events enable async integration with other modules without blocking core functionality.

#### Phase 5: API Gateway Integration (Week 6-7)
18. **Gateway routing** - Configure routes to Patient Service
19. **Gateway filters** - Rate limiting, additional audit logging
20. **Service registration** - Register with Eureka
21. **Why next:** Gateway depends on stable API. Other modules discover service via gateway.

#### Phase 6: Advanced Features (Week 7-10)
22. **Search functionality** - Advanced patient search with filters
23. **Caching layer** - Redis for frequently accessed patients
24. **FHIR adapter** - Optional, if EMR integration required
25. **Batch operations** - Bulk patient import/export
26. **Why last:** These enhance existing functionality but aren't blockers for other modules.

### Dependencies on Other Modules

**Patient Module requires:**
- **Auth Service** - MUST be deployed first. Patient Service can't start without JWT validation.
- **PostgreSQL Database** - MUST exist with proper configuration (encryption, backups).
- **RabbitMQ** - SHOULD exist for event publishing. Patient Module can run without it (fallback to sync), but loses async benefits.
- **API Gateway** - SHOULD route traffic. Direct access possible for development, but production requires gateway for audit/security.

**Other modules depending on Patient Module:**
- **Appointments** - Needs Patient API to validate patient exists, display demographics
- **EMR** - Needs Patient API to link clinical records to patient identity
- **Billing** - Needs Patient events to create billing accounts, needs API for address updates
- **Reporting** - Needs Patient API to enrich reports with demographics

### Integration Testing Order

After Patient Module is built:
1. **Test Patient ↔ Auth** - Validate JWT authentication works
2. **Test Patient → RabbitMQ** - Confirm events publish correctly
3. **Test Appointments ← Patient (events)** - Appointments subscribes to patient events
4. **Test EMR ↔ Patient (REST)** - Bidirectional API calls
5. **Test Billing ← Patient (events)** - Billing creates accounts on patient creation

### Deployment Dependencies

```
Phase 1 (Prerequisites):
- PostgreSQL database deployed
- Auth Service deployed
- RabbitMQ deployed
- API Gateway configured (routing rules)

Phase 2 (Patient Module):
- Deploy Patient Service (2-3 instances)
- Register with Eureka
- Verify health checks

Phase 3 (Dependent Services):
- Deploy Appointments Service (subscribes to patient events)
- Deploy Billing Service (subscribes to patient events)
- Deploy EMR Service (calls patient API)

Phase 4 (Validation):
- End-to-end workflow testing
- Load testing
- Security audit
```

## Sources

### Architecture Patterns & Microservices
- [Embracing FHIR-Native Microservices Architecture in Healthcare IT - Health IT Answers](https://www.healthitanswers.net/embracing-fhir-native-microservices-architecture-in-healthcare-it/)
- [Hospital Management System Development Guide for 2026 - TopFlight Apps](https://topflightapps.com/ideas/how-to-develop-a-hospital-management-system/)
- [Java System Design: Hospital Management System - JavaTechOnline](https://javatechonline.com/java-system-design-hospital-management-system/)
- [What Is Microservice Architecture & How Is Healthcare Adopting It? - HealthTech](https://healthtechmagazine.net/article/2024/09/what-is-microservice-architecture-perfcon)
- [Introduction to Healthcare Microservices Architecture - Medium](https://medium.com/@Larisa10/introduction-to-healthcare-microservices-architecture-d81badc0949f)

### HIPAA Compliance & Security
- [Java Microservices in the Healthcare Sector - Springfuse](https://www.springfuse.com/healthcare-sector-microservices-architecture/)
- [HIPAA Compliance for APIs: A Technical Implementation Guide - IntuitionLabs](https://intuitionlabs.ai/articles/hipaa-compliant-api-guide)
- [Secure API Design & Management for HIPAA-Compliant Healthcare - SpringCT](https://springct.com/technicalarticles/secure-api-design-management-for-hipaa-compliant-healthcare/)
- [Implementing HIPAA Technical Safeguards in your API Platform - Moesif](https://www.moesif.com/blog/technical/compliance/Implementing-HIPAA-Technical-Safeguards-in-your-API/)
- [Healthcare API Integration: HIPAA-Compliant Strategies - Airbyte](https://airbyte.com/data-engineering-resources/healthcare-api-integration-hipaa-compliant-strategies)

### Audit Logging & PHI Security
- [HIPAA Audit Logs: Complete Requirements for Healthcare Compliance - Kiteworks](https://www.kiteworks.com/hipaa-compliance/hipaa-audit-log-requirements/)
- [HIPAA Audit Logs: Key Requirements for PHI Transfers - Censinet](https://censinet.com/perspectives/hipaa-audit-logs-key-requirements-for-phi-transfers)
- [HIPAA Audit Log Requirements: A Complete Manual - Cayosoft](https://www.cayosoft.com/blog/hipaa-audit-log-requirements/)
- [10 Access Control Tips for Cloud PHI Security - Censinet](https://censinet.com/perspectives/access-control-tips-cloud-phi-security)

### Data Flow & System Design
- [Data Flow Diagrams for Hospital Management - Number Analytics](https://www.numberanalytics.com/blog/data-flow-diagrams-for-hospital-management)
- [Patient Information System - Data Flow - Creately](https://creately.com/diagram/example/ipff7pc04/patient-information-system-data-flow)
- [The Information Flow in a Healthcare Organisation - PMC](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6764182/)

### Spring Boot & FHIR Integration
- [Spring Boot FHIR support - IPF Open eHealth Integration Platform](https://oehf.github.io/ipf-docs/docs/boot-fhir/)
- [Build FHIR Patient Server - NHS Care Connect](https://nhsconnect.github.io/CareConnectAPI/build_patient_server.html)
- [Java FHIR Subscriptions: Real-Time Healthcare Event Updates - Bluepes](https://bluepes.com/blog/java-fhir-subscriptions-healthcare)
- [HAPI FHIR - The Open Source FHIR API for Java](https://hapifhir.io/)

### EMR Integration & Workflow
- [EMR Integration: Enhancing Patient Care - Canvas Medical](https://www.canvasmedical.com/articles/emr-integration)
- [Patient Registration and Management System - PatientERP](https://www.patienterp.com/highlights/patient-registration-emr.html)
- [Seamless Electronic Medical Records Integration - Medical Bill Gurus](https://www.medicalbillgurus.com/electronic-medical-records-integration/)

### Database Design
- [How To Build a Database for Healthcare: The Ultimate Guide - Blaze](https://www.blaze.tech/post/how-to-build-a-database)
- [How to Design a Database for Healthcare Management System - GeeksforGeeks](https://www.geeksforgeeks.org/dbms/how-to-design-a-database-for-healthcare-management-system/)
- [HIPAA Compliant Databases - Dash Solutions](https://www.dashsdk.com/hipaa-compliant-database/)

### API Versioning & Best Practices
- [API Versioning Strategies: Backward Compatibility in REST APIs - Medium](https://medium.com/@fahimad/api-versioning-strategies-backward-compatibility-in-rest-apis-234bafd5388e)
- [API Versioning: Strategies & Best Practices - xMatters](https://www.xmatters.com/blog/api-versioning-strategies)
- [Versioning Best Practices in REST API Design - Speakeasy](https://www.speakeasy.com/api-design/versioning)
- [API Versioning Best Practices for Backward Compatibility - Endgrate](https://endgrate.com/blog/api-versioning-best-practices-for-backward-compatibility)

### RBAC & Access Control
- [HIPAA Compliance: Role Based Access Control Model - GIAC](https://www.giac.org/paper/gsec/1394/hipaa-compliance-role-based-access-control-model/102605)
- [Role-Based Access Control (RBAC) for Secure Healthcare SaaS - Cabot Solutions](https://www.cabotsolutions.com/blog/role-based-access-control-rbac-for-secure-healthcare-saas-applications)
- [How to Implement Role-Based Access Control in Spring Boot - Devōt](https://devot.team/blog/role-based-access-control)
- [Role-Based Access Control in Spring Boot - Auth0](https://developer.auth0.com/resources/guides/web-app/spring/basic-role-based-access-control)

### System Build Order & Dependencies
- [How To Build a Healthcare Technology Platform - Blaze](https://www.blaze.tech/post/how-to-build-a-healthcare-technology-platform)
- [Hospital Management System Project - GeeksforGeeks](https://www.geeksforgeeks.org/websites-apps/hospital-management-system-project-in-software-development/)
- [The Essential Modules of a Hospital Management System - InstaHMS](https://www.instahms.com/blog/the-essential-modules-of-a-hospital-management-system)

---
**Confidence Level: MEDIUM** - Based on multiple industry sources, official documentation, and real-world implementations. Spring Boot and PostgreSQL patterns are HIGH confidence (official docs + wide adoption). FHIR integration patterns are MEDIUM confidence (standard exists but implementation varies). Specific performance numbers are LOW confidence (highly dependent on infrastructure and implementation details).

*Architecture research for: Hospital Patient Management System*
*Researched: 2026-02-19*
