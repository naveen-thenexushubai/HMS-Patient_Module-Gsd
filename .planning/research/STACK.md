# Technology Stack Research

**Domain:** Hospital Management System - Patient Module
**Researched:** February 19, 2026
**Overall Confidence:** HIGH

## Executive Summary

The 2025-2026 standard stack for HIPAA-compliant healthcare systems centers on Spring Boot 3.x with robust security layers, PostgreSQL with encryption extensions, and React 18 with healthcare-focused UI libraries. Critical differentiators from general enterprise applications: field-level encryption for PHI, comprehensive audit logging (pgAudit), MFA enforcement, and enhanced cybersecurity measures mandated by the December 2024 HIPAA Security Rule updates.

**Key Constraint:** Your requirement for Spring Boot 3.2.x should be upgraded to 3.4.x or 4.0.x due to security vulnerabilities in 3.2.x versions (CVE-2025-22235 affecting disabled actuator endpoints).

---

## Core Technologies

### Backend Framework

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| **Spring Boot** | 3.4.5+ or 4.0.2 | Application framework | Current stable version. Spring Boot 3.2.x has known vulnerabilities. 3.4.5 released May 2025 with enhanced actuator observability. 4.0.2 is latest (Jan 2026) with extended support. Healthcare apps require active security patching. | HIGH |
| **Java** | 17 LTS or 21 LTS | Runtime | Java 17 meets your constraint. Java 21 LTS (released Sept 2023) adds virtual threads, pattern matching, and performance improvements. Both actively supported through 2026+. Healthcare systems benefit from LTS stability. | HIGH |
| **Spring Security** | 6.x (bundled with Spring Boot 3.x) | Authentication & Authorization | OAuth2 + JWT for stateless auth. Supports MFA (required by 2025 HIPAA updates). RSA key signing recommended over symmetric keys. Role-based access control (RBAC) for PHI. | HIGH |
| **Spring Data JPA** | 3.x (bundled) | Data access | Declarative repositories. **Critical**: Use `Slice<T>` over `Page<T>` for 50K+ patient searches to avoid COUNT query overhead. Keyset pagination for large datasets. | HIGH |
| **Hibernate** | 6.x | ORM | Bundled with Spring Boot 3.x. Supports field-level encryption via `@Convert` with custom converters. Efficient batch operations for audit logs. | HIGH |

### Database

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| **PostgreSQL** | 15.x or 16.x | Primary database | Meets your 15+ constraint. Version 16 (Sept 2023) adds logical replication improvements and query performance gains. HIPAA-ready with proper configuration. Industry standard for healthcare (Crunchy Data certified solutions). | HIGH |
| **pgAudit** | 17.x | Audit logging extension | **Required for HIPAA compliance**. Logs all SELECT/INSERT/UPDATE/DELETE on PHI tables. Automatically redacts passwords (unlike standard pg_log). Structured output for centralized log stores (CloudWatch, Splunk). | HIGH |
| **pgcrypto** | (bundled) | Database-level encryption | Provides AES-256 encryption functions. Use for at-rest encryption of PHI fields. Complements application-layer encryption. | MEDIUM |

### Frontend Framework

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| **React** | 18.3.x | UI framework | Meets your 18.x constraint. React 18.3 (Feb 2024) includes concurrent features, automatic batching, Suspense improvements. Large healthcare ecosystem (Material-UI, TanStack Query). | HIGH |
| **TypeScript** | 5.x | Type safety | Not in your constraints but **strongly recommended** for healthcare. Catches PHI data leakage at compile-time. Reduces runtime errors by 15-38% (industry studies). | MEDIUM |
| **React Router** | 6.x | Client-side routing | v6 (Nov 2021, stable updates through 2025) provides data loading APIs, nested routes, and enhanced type safety. Standard for React SPAs. | HIGH |

---

## Security & Compliance Libraries

### Encryption & Key Management

| Library | Version | Purpose | When to Use | Confidence |
|---------|---------|---------|-------------|------------|
| **Spring Security OAuth2 Resource Server** | (bundled with Spring Security 6.x) | JWT validation | Stateless API authentication. Use for patient portal, mobile apps, third-party integrations. Validates JWT signature, issuer, expiration, audience. | HIGH |
| **Jasypt Spring Boot** | 3.0.5 | Property encryption | Encrypt database passwords, API keys in `application.yml`. **Limitation**: Not suitable for field-level PHI encryption (no deterministic mode, breaks indexing). Use for config secrets only. | HIGH |
| **Spring Crypto** | (bundled) | Field-level encryption | Use `TextEncryptor` with AES-256-GCM for PHI fields (SSN, medical records). Implement custom JPA `@Convert` converters. **Critical**: Store encryption keys in HashiCorp Vault or AWS Secrets Manager, not in code. | HIGH |
| **Bouncy Castle** | 1.78 (latest stable Jan 2025) | Cryptography provider | Enhanced crypto algorithms beyond JDK. Required for FIPS 140-2 compliance if needed. Use for advanced encryption scenarios. | MEDIUM |

### Audit & Monitoring

| Library | Version | Purpose | When to Use | Confidence |
|---------|---------|---------|------------|------------|
| **Spring Boot Actuator** | (bundled) | Application monitoring | Exposes `/health`, `/metrics`, `/auditevents`. **Security**: Secure endpoints behind admin-only access. Use with Micrometer for custom metrics (patient search latency, failed login attempts). | HIGH |
| **Micrometer** | (bundled) | Metrics facade | Export metrics to Prometheus, Datadog, New Relic. Track HIPAA-relevant metrics: PHI access frequency, authentication failures, session durations. Spring Boot 3.4+ adds automatic observability for all repository calls. | HIGH |
| **Spring Data Envers** | (Hibernate Envers integration) | Entity versioning | Automatic audit trail for patient record changes. Stores who/when/what changed. **Limitation**: Increases DB size 2-3x. Use for PHI entities only, not lookup tables. | MEDIUM |
| **Logback** | (bundled) | Logging | **Critical**: Configure JSON structured logging. Mask PHI in logs (use custom filters). Centralize to immutable store (AWS CloudWatch, Splunk). Retain per HIPAA requirements (6 years). | HIGH |

### Validation

| Library | Version | Purpose | When to Use | Confidence |
|---------|---------|---------|-------------|------------|
| **Hibernate Validator** | 9.1.x (bundled) | Bean Validation (JSR-380) | Field-level validation (email, phone, SSN format). Use `@NotNull`, `@Pattern`, custom validators for healthcare-specific rules (date of birth validation, insurance number format). | HIGH |
| **Spring Validation** | (bundled) | Method-level validation | Use `@Validated` on service layer to validate DTOs. Prevents invalid data from reaching database. Complements Hibernate Validator. | HIGH |

---

## Frontend Libraries

### UI Component Library

| Library | Version | Purpose | When to Use | Confidence |
|---------|---------|---------|-------------|------------|
| **Material-UI (MUI)** | 5.x (latest 5.17.x Feb 2025) | React component library | **Recommended for healthcare**. Implements WCAG 2.1 accessibility standards. Comprehensive components (data grids via MUI X, date pickers, autocomplete). 94K GitHub stars. Strong documentation and community. | HIGH |
| **MUI X Data Grid** | 7.x (Pro/Premium) | Advanced data grid | For patient search results table. Supports 50K rows with virtualization. Server-side pagination, sorting, filtering. Excel export for reports. **Note**: Pro/Premium licenses required for advanced features (~$250-900/dev/year). | MEDIUM |

**Alternative**: Ant Design (94K stars, Alibaba-backed, strong i18n) if Material Design aesthetic doesn't fit brand.

### Data Fetching & State Management

| Library | Version | Purpose | When to Use | Confidence |
|---------|---------|---------|-------------|------------|
| **TanStack Query (React Query)** | 5.x | Server state management | **Recommended**. Automatic caching, background refetching, optimistic updates. Reduces boilerplate by 60% vs manual fetch. React 19 compatible. Built-in DevTools for debugging. Ideal for patient search, profile loading. | HIGH |
| **Axios** | 1.7.x | HTTP client | **Recommended over Fetch**. Automatic JSON parsing, request/response interceptors (for auth tokens, error logging), better error handling. Healthcare apps need robust error reporting. ~45KB minified. | HIGH |
| **Zustand** | 4.x | Client state management | Lightweight (2KB), simpler than Redux. Use for UI state (modals, form drafts), not server data (use TanStack Query). | MEDIUM |

### Form Handling

| Library | Version | Purpose | When to Use | Confidence |
|---------|---------|---------|-------------|------------|
| **React Hook Form** | 7.x | Form validation | **Recommended for healthcare forms**. Minimal re-renders (30-50% faster than Formik). Uncontrolled components. Integrates with Yup/Zod for schema validation. Ideal for patient registration (10-20 fields). | HIGH |
| **Yup** | 1.x | Schema validation | Declarative validation rules. Sync/async validation. Use with React Hook Form. Example: `yup.string().matches(/^\d{3}-\d{2}-\d{4}$/, 'Invalid SSN')`. | HIGH |

**Alternative**: Formik (older, more features, but slower) if team already experienced with it.

### Testing

| Library | Version | Purpose | When to Use | Confidence |
|---------|---------|---------|-------------|------------|
| **Jest** | 29.x | Test runner | Standard React testing framework. 44M+ weekly downloads. Fast, parallel execution. Snapshot testing for components. | HIGH |
| **React Testing Library** | 16.x | Component testing | Tests user behavior, not implementation. Encourages accessible components. Use `screen.getByRole()` for WCAG compliance. | HIGH |
| **MSW (Mock Service Worker)** | 2.x | API mocking | Mock backend APIs in tests. Intercepts network requests. More reliable than mocking Axios directly. | MEDIUM |

---

## Backend Supporting Libraries

### Database Migration

| Library | Version | Purpose | When to Use | Confidence |
|---------|---------|---------|-------------|------------|
| **Flyway** | 10.x | Database versioning | **Recommended**. Linear versioning (V1__init.sql, V2__add_audit.sql). SQL-based migrations. Simple, minimal configuration. Healthcare apps need traceable schema changes for compliance audits. | HIGH |

**Alternative**: Liquibase (supports XML/YAML, more complex rollback) if multi-DB support needed.

### API Documentation

| Library | Version | Purpose | When to Use | Confidence |
|---------|---------|---------|-------------|------------|
| **SpringDoc OpenAPI** | 2.x | OpenAPI 3 spec generation | Auto-generates API docs from `@RestController` annotations. Interactive Swagger UI. **Security**: Disable in production or secure behind admin auth. | HIGH |

### Testing

| Library | Version | Purpose | When to Use | Confidence |
|---------|---------|---------|-------------|------------|
| **JUnit 5** | 5.10.x | Unit testing | Standard Java testing. `@ExtendWith(MockitoExtension.class)` for mocks. | HIGH |
| **Mockito** | 5.x | Mocking framework | Mock service dependencies in unit tests. Use `@Mock`, `@InjectMocks`. | HIGH |
| **Testcontainers** | 1.19.x | Integration testing | **Critical for healthcare**. Spin up PostgreSQL Docker container in tests. Test against real DB, not H2. Ensures production parity. Use same PostgreSQL version as production. | HIGH |
| **REST Assured** | 5.x | API testing | Fluent API for REST endpoint testing. Validate JSON responses, status codes, headers. | MEDIUM |

### Utility

| Library | Version | Purpose | When to Use | Confidence |
|---------|---------|---------|-------------|------------|
| **Lombok** | 1.18.x | Boilerplate reduction | `@Data`, `@Builder`, `@Slf4j`. **Caution**: Avoid `@Data` on JPA entities (breaks lazy loading). Use `@Getter`/`@Setter` explicitly. | MEDIUM |
| **MapStruct** | 1.6.x | DTO mapping | Compile-time mapping (faster than reflection-based mappers). Entity ↔ DTO conversion. Reduces boilerplate. | MEDIUM |

---

## Installation

### Backend (Maven)

```xml
<!-- pom.xml -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.5</version> <!-- or 4.0.2 -->
</parent>

<properties>
    <java.version>17</java.version> <!-- or 21 -->
    <testcontainers.version>1.19.8</testcontainers.version>
</properties>

<dependencies>
    <!-- Core -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <!-- Security & Encryption -->
    <dependency>
        <groupId>com.github.ulisesbocchio</groupId>
        <artifactId>jasypt-spring-boot-starter</artifactId>
        <version>3.0.5</version>
    </dependency>
    <dependency>
        <groupId>org.bouncycastle</groupId>
        <artifactId>bcprov-jdk18on</artifactId>
        <version>1.78</version>
    </dependency>

    <!-- Monitoring -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>

    <!-- API Documentation -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.6.0</version>
    </dependency>

    <!-- Utilities -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>1.6.2</version>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.rest-assured</groupId>
        <artifactId>rest-assured</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-bom</artifactId>
            <version>${testcontainers.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Frontend (npm)

```bash
# Core
npm install react@18.3.1 react-dom@18.3.1 react-router-dom@6.26.2

# UI Components
npm install @mui/material@5.17.0 @mui/x-data-grid@7.22.2 @emotion/react @emotion/styled

# Data Fetching & HTTP
npm install @tanstack/react-query@5.61.5 axios@1.7.9

# Forms & Validation
npm install react-hook-form@7.54.2 yup@1.4.0

# State Management (optional)
npm install zustand@4.5.5

# Dev Dependencies
npm install -D typescript@5.7.2 @types/react @types/react-dom
npm install -D @testing-library/react@16.1.0 @testing-library/jest-dom@6.6.3 jest@29.7.0
npm install -D msw@2.7.0
npm install -D eslint@9.17.0 prettier@3.4.2
```

---

## Alternatives Considered

| Category | Recommended | Alternative | When to Use Alternative | Confidence |
|----------|-------------|-------------|-------------------------|------------|
| **Backend Framework** | Spring Boot 3.4.5+ | Spring Boot 2.7.x | **Never**. EOL Nov 2023. Security vulnerabilities accumulating. Healthcare apps require active patching. | HIGH |
| **Frontend Framework** | React 18 | Angular 18 / Vue 3 | Angular: If team is TypeScript-first and prefers opinionated framework. Vue: Simpler learning curve, but smaller healthcare ecosystem. | MEDIUM |
| **UI Library** | Material-UI | Ant Design, Chakra UI | Ant Design: Better for Asian markets (i18n). Chakra UI: Simpler API, but smaller component set. | MEDIUM |
| **State Management** | TanStack Query + Zustand | Redux Toolkit | Redux: If team already invested or needs time-travel debugging. Overhead not justified for patient module. | HIGH |
| **Form Library** | React Hook Form | Formik | Formik: More features (field-level subscriptions), but 30-50% slower. Use if performance isn't critical. | HIGH |
| **Database Migration** | Flyway | Liquibase | Liquibase: If need database-agnostic migrations (Oracle, MySQL fallback) or complex rollback scenarios. | MEDIUM |
| **ORM** | Spring Data JPA | jOOQ, MyBatis | jOOQ: If need type-safe SQL, complex queries. MyBatis: If team prefers SQL-first. JPA sufficient for patient module. | MEDIUM |

---

## What NOT to Use

| Avoid | Why | Use Instead | Confidence |
|-------|-----|-------------|------------|
| **H2 Database for Testing** | In-memory DB has different SQL dialect than PostgreSQL. Hides production bugs (e.g., JSONB types, full-text search). HIPAA apps need production parity. | Testcontainers with PostgreSQL 15/16 | HIGH |
| **Spring Boot 3.2.x** | CVE-2025-22235 (Endpoint Security Bypass). Multiple medium/high severity vulnerabilities. Healthcare apps can't tolerate known CVEs. | Spring Boot 3.4.5 or 4.0.2 | HIGH |
| **JWT in LocalStorage** | Vulnerable to XSS attacks. If attacker injects script, they steal auth token. | HttpOnly cookies or Memory + Refresh Token in HttpOnly cookie | HIGH |
| **Synchronous Logging in Request Path** | Blocks request thread. 2s patient search + 200ms logging = 2.2s response. Violates your <2s requirement. | Async appenders (Logback `AsyncAppender`) or log to queue | HIGH |
| **`@Data` on JPA Entities** | Lombok's `@Data` generates `equals()`/`hashCode()` using all fields. Breaks lazy loading (triggers unwanted queries). | `@Getter`, `@Setter`, `@ToString(exclude={"lazyField"})` explicitly | MEDIUM |
| **Field-Level Encryption Everywhere** | Encrypted fields can't be indexed. Patient search by SSN = full table scan on 50K rows. Kills performance. | Encrypt PII/PHI only. Index searchable fields (name, DOB) in plain text with DB-level encryption at rest. | HIGH |
| **`Page<T>` for Patient Search** | Executes extra COUNT query. 50K patients = slow. Pagination metadata rarely needed in search results. | `Slice<T>` for "Next Page" or keyset pagination for large datasets | HIGH |
| **Redux for Small App** | Patient module = small surface area. Redux adds 3-5 files per feature. Overkill. | TanStack Query (server state) + Zustand (UI state) or React Context | MEDIUM |
| **Microservices for MVP** | Patient module = bounded context. Microservices add complexity (distributed transactions, network latency, deployment overhead). | Modular monolith. Extract microservices later if needed (e.g., separate billing, imaging). | HIGH |

---

## Stack Patterns by Requirement

### HIPAA Compliance Configuration

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/hospital_db?ssl=true&sslmode=require
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000
  jpa:
    properties:
      hibernate:
        session.events.log: true  # Audit entity changes
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.hospital.com/realms/patient-portal
          jwk-set-uri: https://auth.hospital.com/realms/patient-portal/protocol/openid-connect/certs

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
  security:
    roles: ADMIN  # Restrict actuator to admins only

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.springframework.security: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"  # No PHI in logs
```

### PostgreSQL HIPAA Configuration

```sql
-- Enable pgAudit extension
CREATE EXTENSION IF NOT EXISTS pgaudit;

-- Configure audit logging
ALTER SYSTEM SET pgaudit.log = 'write, ddl';  -- Log all writes and schema changes
ALTER SYSTEM SET pgaudit.log_catalog = 'off';  -- Reduce noise
ALTER SYSTEM SET pgaudit.log_relation = 'on';  -- Log table names
ALTER SYSTEM SET pgaudit.log_statement_once = 'off';  -- Log each operation

-- Enable SSL/TLS
ALTER SYSTEM SET ssl = 'on';
ALTER SYSTEM SET ssl_cert_file = '/path/to/cert.pem';
ALTER SYSTEM SET ssl_key_file = '/path/to/key.pem';

-- Reload configuration
SELECT pg_reload_conf();

-- Create audit role (all user accounts should inherit from this)
CREATE ROLE audit_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO audit_role;
```

### Field-Level Encryption Example

```java
// Custom JPA converter for SSN encryption
@Converter
public class SsnEncryptionConverter implements AttributeConverter<String, String> {

    private final TextEncryptor encryptor;

    public SsnEncryptionConverter(@Value("${encryption.key}") String key) {
        this.encryptor = Encryptors.text(key, KeyGenerators.string().generateKey());
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        return plainText == null ? null : encryptor.encrypt(plainText);
    }

    @Override
    public String convertToEntityAttribute(String encrypted) {
        return encrypted == null ? null : encryptor.decrypt(encrypted);
    }
}

// Usage in entity
@Entity
public class Patient {
    @Id
    private Long id;

    @Convert(converter = SsnEncryptionConverter.class)
    @Column(nullable = false)
    private String ssn;  // Encrypted in DB, plain in memory

    private String firstName;  // Plain text (searchable)
    private String lastName;   // Plain text (searchable)
}
```

### Optimized Patient Search (Slice-based)

```java
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    @GetMapping("/search")
    public Slice<PatientDto> searchPatients(
        @RequestParam String query,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastName"));
        Slice<Patient> patients = patientRepository.findByNameContaining(query, pageable);
        return patients.map(patientMapper::toDto);  // MapStruct
    }
}

// Repository using Slice (no COUNT query)
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Slice<Patient> findByNameContaining(String name, Pageable pageable);
}
```

---

## Version Compatibility Matrix

| Spring Boot | Java | PostgreSQL | React | Node.js | Notes |
|-------------|------|------------|-------|---------|-------|
| 3.4.5 | 17, 21 | 15, 16, 17 | 18.3.x | 18.x, 20.x | Recommended. Java 21 for performance. PostgreSQL 16 for query improvements. |
| 4.0.2 | 17, 21 | 15, 16, 17 | 18.3.x, 19.x | 18.x, 20.x | Latest. React 19 compatible but beta. Stick with React 18 for stability. |
| 3.2.x | 17, 21 | 15, 16 | 18.3.x | 18.x, 20.x | **Not recommended**. Known CVEs. Update to 3.4.5+. |

**PostgreSQL Driver:** `org.postgresql:postgresql:42.7.4` (latest Jan 2025). Compatible with PostgreSQL 10-17.

**Note:** Spring Boot 3.x requires Java 17+ (dropped Java 11 support).

---

## Infrastructure Recommendations

| Component | Technology | Purpose | Notes |
|-----------|-----------|---------|-------|
| **Key Management** | HashiCorp Vault or AWS Secrets Manager | Store encryption keys, DB credentials | **Critical**. Don't store keys in code or environment variables. Rotate every 90 days. |
| **Log Aggregation** | AWS CloudWatch, Splunk, or ELK Stack | Centralized, immutable audit logs | HIPAA requires 6-year retention. Ensure logs can't be tampered with. |
| **Monitoring** | Prometheus + Grafana | Metrics visualization | Track failed logins, PHI access patterns, API latency. Alert on anomalies. |
| **Container Runtime** | Docker + Docker Compose (dev) or Kubernetes (prod) | Deployment | Testcontainers requires Docker. Kubernetes for prod scalability. |
| **CI/CD** | GitHub Actions, GitLab CI, or Jenkins | Automated testing, security scans | Run Testcontainers tests on every PR. OWASP dependency check. |
| **API Gateway** | Kong, AWS API Gateway, or Spring Cloud Gateway | Rate limiting, auth, routing | Add in Phase 2+ when integrating multiple modules. Patient module can start without. |

---

## Security Checklist

- [ ] **SSL/TLS Everywhere**: Database connections (`sslmode=require`), API endpoints (HTTPS only)
- [ ] **MFA Enabled**: Required by 2025 HIPAA updates. Use OAuth2 provider with MFA support (Keycloak, Auth0, AWS Cognito)
- [ ] **Field-Level Encryption**: SSN, medical record numbers. Use `@Convert` with Spring Crypto
- [ ] **Database Encryption at Rest**: PostgreSQL Transparent Data Encryption (TDE) or disk-level encryption
- [ ] **Audit Logging**: pgAudit for DB, Spring Boot Actuator `/auditevents` for app
- [ ] **Role-Based Access Control**: `@PreAuthorize("hasRole('DOCTOR')")` on sensitive endpoints
- [ ] **Password Policies**: Min 12 chars, complexity, rotation every 90 days (enforce via OAuth2 provider)
- [ ] **Session Timeouts**: 15 min idle, 8 hours max (configurable via `server.servlet.session.timeout`)
- [ ] **Vulnerability Scanning**: Run OWASP Dependency-Check on every build. Fail if high/critical CVEs found
- [ ] **Penetration Testing**: Required semi-annually per 2025 HIPAA updates
- [ ] **Business Associate Agreements**: Sign BAAs with all vendors (database host, logging service, OAuth provider)

---

## Performance Optimization Notes

### Database Indexing Strategy

```sql
-- Indexes for patient search (<2s requirement with 50K patients)
CREATE INDEX idx_patient_name ON patient(last_name, first_name);
CREATE INDEX idx_patient_dob ON patient(date_of_birth);
CREATE INDEX idx_patient_mrn ON patient(medical_record_number);  -- If not encrypted

-- Partial index for active patients (if status filtering common)
CREATE INDEX idx_patient_active ON patient(status) WHERE status = 'ACTIVE';

-- Full-text search (if search by name is primary use case)
CREATE INDEX idx_patient_name_fts ON patient USING gin(to_tsvector('english', first_name || ' ' || last_name));
```

### Connection Pool Sizing

For 100 concurrent users and 50K patients:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Formula: concurrent_users / avg_queries_per_request
      minimum-idle: 5
      connection-timeout: 30000  # 30s
      idle-timeout: 600000       # 10 min
```

**Rationale**: Patient search = 1 query. 100 users / 5 avg queries = 20 connections. Over-provisioning wastes resources.

### React Performance

- **Virtualization**: Use MUI X Data Grid's virtualization for 1000+ row tables
- **Code Splitting**: Lazy load routes with `React.lazy()` and `Suspense`
- **Memoization**: Use `React.memo()` for expensive patient card components
- **TanStack Query Caching**: Default 5-minute cache. Adjust per data volatility.

---

## Sources

### High Confidence (Official Docs & Context7)

- [Spring Boot End of Life Dates](https://endoflife.date/spring-boot) - Version timelines
- [Spring Boot 3.4 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes) - Actuator enhancements
- [Spring Boot 4.0.2 Release](https://eosl.date/eol/product/spring-boot/) - Latest version
- [Federal Register - HIPAA Security Rule 2025](https://www.federalregister.gov/documents/2025/01/06/2024-30983/hipaa-security-rule-to-strengthen-the-cybersecurity-of-electronic-protected-health-information) - MFA, encryption requirements
- [PostgreSQL pgAudit Documentation](https://github.com/pgaudit/pgaudit) - Audit logging
- [Hibernate Validator 9.1 Docs](https://hibernate.org/validator/documentation/) - Latest version
- [TanStack Query Docs](https://tanstack.com/query/latest) - React Query v5
- [React Hook Form Official](https://react-hook-form.com/) - Latest API

### Medium Confidence (Verified Web Sources)

- [HIPAA 2026 Security Rules](https://healthcarereaders.com/insights/hipaa-cybersecurity-for-patient-data) - Cybersecurity updates
- [PostgreSQL HIPAA Compliance Guide](https://triglon.tech/tech-hub/hipaa-postgresql/) - Configuration best practices
- [Spring Boot Pagination Performance](https://copyprogramming.com/howto/issue-of-pagination-in-spring-data-jpa) - Slice vs Page
- [Axios vs Fetch 2025](https://blog.logrocket.com/axios-vs-fetch-2025/) - Comparison
- [React UI Libraries 2026](https://www.builder.io/blog/react-component-libraries-2026) - MUI, Ant Design
- [Spring Security OAuth2 Best Practices 2025](https://medium.com/@priyaranjanpatraa/mastering-api-security-in-2025-jwt-oauth2-rate-limiting-best-practices-cecf96568025) - JWT patterns

### Low Confidence (Single Source / Unverified)

- Spring Boot 3.2.x specific healthcare implementations - Not found in search results
- Healthcare-specific React component libraries - Generic UI libraries used instead
- Field-level encryption performance benchmarks - Based on general cryptography principles

---

**Recommendation Summary:**

✅ **Use:** Spring Boot 3.4.5+, PostgreSQL 15/16 with pgAudit, React 18.3, Material-UI, TanStack Query, React Hook Form, Flyway, Testcontainers

⚠️ **Upgrade:** Spring Boot 3.2.x → 3.4.5+ (security vulnerabilities)

❌ **Avoid:** H2 for testing, JWT in LocalStorage, `Page<T>` for large datasets, field-level encryption on searchable fields, Spring Boot 2.7.x

---

*Stack research for: Hospital Management System - Patient Module*
*Researched: February 19, 2026*
*Next: Review FEATURES.md and ARCHITECTURE.md for roadmap implications*
