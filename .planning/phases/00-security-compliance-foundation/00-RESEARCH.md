# Phase 0: Security & Compliance Foundation - Research

**Researched:** 2026-02-19
**Domain:** HIPAA-compliant Spring Boot security architecture
**Confidence:** HIGH

## Summary

This phase establishes HIPAA-compliant security infrastructure for a healthcare application handling Protected Health Information (PHI). The research identifies that HIPAA compliance requires a combination of technical safeguards (encryption, access control, audit logging) and administrative processes (risk assessment, documentation). Spring Boot 3.4.5+ with Spring Security 6.4 provides a mature, production-ready foundation for implementing these requirements.

Key findings: (1) Spring Boot 3.4.5 is mandatory due to CVE-2025-22235 security patch, (2) PostgreSQL with pgAudit extension is the standard approach for HIPAA audit logging with 6-year retention, (3) Field-level encryption via JPA AttributeConverter is preferred over repository-layer encryption for sensitive PHI fields, (4) Object-level authorization using Spring Security's @PreAuthorize with custom PermissionEvaluator is required for "can this user access THIS patient" checks, and (5) Spring Actuator endpoints must be secured or disabled in production to prevent information disclosure.

**Primary recommendation:** Use Spring Security 6.4 with JWT authentication, implement JPA AttributeConverter for field-level encryption of SSN/insurance data, configure PostgreSQL pgAudit for immutable audit logs with 6-year retention, and create custom PermissionEvaluator for object-level patient data authorization.

<phase_requirements>
## Phase Requirements

This phase MUST address the following security and compliance requirements:

| ID | Description | Research Support |
|----|-------------|-----------------|
| SEC-01 | System completes HIPAA Security Risk Assessment documenting all PHI storage and transmission paths | Risk assessment framework research (no standard template exists, must document all ePHI touchpoints) |
| SEC-02 | System implements audit logging to append-only storage with 6-year retention for all PHI access | PostgreSQL pgAudit extension with append-only tables, partitioned by time for retention management |
| SEC-03 | System encrypts patient data at rest using PostgreSQL encryption or disk-level encryption | PostgreSQL Transparent Data Encryption or OS-level full-disk encryption (AES-256) |
| SEC-04 | System encrypts patient data in transit using TLS 1.3 for all API communications | Spring Boot server.ssl configuration with TLSv1.3 protocol and strong cipher suites |
| SEC-05 | System implements field-level encryption for sensitive PHI (SSN, insurance data) using Spring Crypto | JPA AttributeConverter with AES-256 encryption for sensitive columns |
| SEC-06 | System validates JWT tokens and enforces role-based access control on all API endpoints | Spring Security 6.4 JWT filter chain with role-based @PreAuthorize annotations |
| SEC-07 | System implements object-level authorization checking "can this user access THIS patient" on every data access | Custom PermissionEvaluator with @PreAuthorize("hasPermission(#patientId, 'Patient', 'read')") pattern |
| SEC-08 | System stores secrets in environment variables or secrets manager (no hardcoded credentials) | Spring Boot environment variables with optional HashiCorp Vault or AWS Secrets Manager integration |
| SEC-09 | System logs all patient data access with user ID, timestamp, action, resource, and device/IP address | Custom audit logging interceptor combined with pgAudit database-level tracking |
</phase_requirements>

## Standard Stack

### Core Security Libraries

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.4.5+ | Application framework | CVE-2025-22235 fix, includes Spring Security 6.4 |
| Spring Security | 6.4.x | Authentication & authorization | Managed by Spring Boot 3.4.5, production-tested RBAC |
| JJWT (io.jsonwebtoken) | 0.13.0 | JWT creation and validation | Industry-standard Java JWT library with modern API |
| PostgreSQL | 16+ | Database with encryption support | Native encryption support, pgAudit extension for HIPAA |
| pgAudit | 16.0+ | Database audit logging | HIPAA-compliant audit trail, immutable logging |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Jasypt Spring Boot | 4.0.4 | Property encryption | Encrypting application.properties secrets (alternative to vault) |
| Spring Boot Validation | (included) | Input validation | Preventing injection attacks, validating PHI inputs |
| Bouncy Castle | 1.78+ | Advanced crypto operations | Field-level encryption with AES-256, key management |
| Micrometer | (included) | Metrics and monitoring | Security event metrics for audit review |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| PostgreSQL pgAudit | Application-level audit logging | pgAudit provides database-level tamper resistance, captures direct DB access |
| JJWT | Nimbus JOSE+JWT, Auth0 Java-JWT | JJWT has cleaner API, better Spring integration, active maintenance |
| JPA AttributeConverter | Repository-layer encryption | AttributeConverter is transparent to business logic, handles encrypt/decrypt automatically |
| Spring Security | Apache Shiro, JAAS | Spring Security has first-class Spring Boot integration, larger ecosystem |

**Installation:**
```xml
<!-- Spring Boot BOM manages versions -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.5</version>
</parent>

<dependencies>
    <!-- Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.13.0</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.13.0</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.13.0</version>
        <scope>runtime</scope>
    </dependency>

    <!-- Field-level encryption -->
    <dependency>
        <groupId>org.bouncycastle</groupId>
        <artifactId>bcprov-jdk18on</artifactId>
        <version>1.78</version>
    </dependency>

    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
</dependencies>
```

## Architecture Patterns

### Recommended Project Structure (Modular Monolith with Security Layer)

```
src/main/java/com/hospital/
├── security/                    # Security cross-cutting concern
│   ├── config/
│   │   ├── SecurityConfig.java           # Spring Security configuration
│   │   ├── JwtConfig.java                # JWT settings
│   │   └── EncryptionConfig.java         # Encryption keys
│   ├── jwt/
│   │   ├── JwtTokenProvider.java         # JWT creation & validation
│   │   ├── JwtAuthenticationFilter.java  # JWT filter for requests
│   │   └── JwtAuthenticationEntryPoint.java
│   ├── authorization/
│   │   ├── PatientPermissionEvaluator.java   # Object-level authorization
│   │   └── SecurityContextHelper.java        # Current user utilities
│   ├── encryption/
│   │   ├── EncryptionService.java        # AES-256 encryption logic
│   │   └── SensitiveDataConverter.java   # JPA AttributeConverter
│   └── audit/
│       ├── AuditLog.java                 # Audit log entity
│       ├── AuditLogRepository.java       # Append-only repository
│       └── AuditInterceptor.java         # Spring AOP interceptor
├── patient/                     # Patient domain module
│   ├── domain/
│   │   ├── Patient.java                  # Patient entity with encrypted fields
│   │   └── PatientInsurance.java         # Insurance with field encryption
│   ├── application/
│   │   ├── PatientService.java           # Business logic
│   │   └── PatientEventPublisher.java
│   ├── infrastructure/
│   │   ├── PatientRepository.java        # Data access
│   │   └── PatientAuditListener.java     # JPA audit listener
│   └── api/
│       ├── PatientController.java        # REST endpoints with @PreAuthorize
│       └── PatientDto.java
└── shared/                      # Shared infrastructure
    ├── config/
    │   └── ApplicationConfig.java
    └── exception/
        └── SecurityExceptionHandler.java
```

### Pattern 1: Field-Level Encryption with JPA AttributeConverter

**What:** Transparent encryption/decryption of sensitive database columns using JPA 2.1 AttributeConverter
**When to use:** For PHI fields that must be encrypted at rest (SSN, insurance policy numbers) but not searched on
**Example:**
```java
// Source: Multiple Spring Data JPA encryption tutorials verified across sources
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.Base64;

@Converter
public class SensitiveDataConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES";
    private final SecretKeySpec secretKey;

    public SensitiveDataConverter(EncryptionService encryptionService) {
        // Key should come from environment variable or secrets manager
        this.secretKey = encryptionService.getEncryptionKey();
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(attribute.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(dbData)));
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}

// Usage in entity
@Entity
@Table(name = "patients")
public class Patient {
    @Id
    private Long id;

    @Convert(converter = SensitiveDataConverter.class)
    @Column(name = "ssn")
    private String socialSecurityNumber;  // Transparently encrypted

    @Convert(converter = SensitiveDataConverter.class)
    @Column(name = "insurance_policy_number")
    private String insurancePolicyNumber;  // Transparently encrypted
}
```

### Pattern 2: Object-Level Authorization with Custom PermissionEvaluator

**What:** Enforce "can THIS user access THIS patient" using Spring Security's permission framework
**When to use:** For every patient data access - reading, updating, or deleting patient records
**Example:**
```java
// Source: Spring Security ACL and @PreAuthorize patterns from multiple sources
@Component
public class PatientPermissionEvaluator implements PermissionEvaluator {

    @Autowired
    private PatientRepository patientRepository;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String username = authentication.getName();
        Long patientId = (Long) targetDomainObject;
        String permissionName = (String) permission;

        // ADMIN can access all patients
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }

        // DOCTOR can read/write patients assigned to them
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"))) {
            return isPatientAssignedToDoctor(patientId, username);
        }

        // RECEPTIONIST can read all, write based on department
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_RECEPTIONIST"))) {
            return permissionName.equals("read") || isPatientInSameDepartment(patientId, username);
        }

        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        return hasPermission(authentication, targetId, permission);
    }

    private boolean isPatientAssignedToDoctor(Long patientId, String doctorUsername) {
        // Query patient_assignments or similar table
        return patientRepository.isAssignedToDoctor(patientId, doctorUsername);
    }

    private boolean isPatientInSameDepartment(Long patientId, String receptionistUsername) {
        // Query department assignments
        return patientRepository.isInSameDepartment(patientId, receptionistUsername);
    }
}

// Configuration
@Configuration
@EnableMethodSecurity  // Spring Security 6.x
public class MethodSecurityConfig {

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            PatientPermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}

// Usage in service layer
@Service
public class PatientService {

    @PreAuthorize("hasPermission(#patientId, 'Patient', 'read')")
    public PatientDto getPatient(Long patientId) {
        // Authorization checked before method executes
        return patientRepository.findById(patientId)
                .map(this::toDto)
                .orElseThrow(() -> new PatientNotFoundException(patientId));
    }

    @PreAuthorize("hasPermission(#patientId, 'Patient', 'write')")
    public void updatePatient(Long patientId, UpdatePatientRequest request) {
        // Authorization checked before method executes
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));
        // ... update logic
    }
}
```

### Pattern 3: JWT Authentication with Role-Based Access Control

**What:** Stateless authentication using JWT tokens with role and permission claims
**When to use:** For all API endpoint authentication and authorization
**Example:**
```java
// Source: Spring Security 6 JWT patterns from multiple 2024-2026 tutorials
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:3600000}")  // 1 hour default
    private long jwtExpiration;

    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("roles", userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Token validation failed
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }
}

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (jwt != null && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null,
                                userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource()
                        .buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // Log exception but don't block filter chain
            logger.error("Could not set user authentication", e);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // JWT is stateless, CSRF not needed
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/patients/**").hasAnyRole("ADMIN", "DOCTOR", "RECEPTIONIST")
                    .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

### Pattern 4: Append-Only Audit Logging with PostgreSQL

**What:** Immutable audit trail stored in PostgreSQL with pgAudit extension and application-level logging
**When to use:** For all PHI access events to meet HIPAA 6-year retention requirement
**Example:**
```java
// Application-level audit log entity
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String action;  // CREATE, READ, UPDATE, DELETE

    @Column(nullable = false)
    private String resourceType;  // PATIENT, INSURANCE, etc.

    @Column(nullable = false)
    private String resourceId;

    @Column
    private String ipAddress;

    @Column
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String details;  // JSON with additional context

    // Only getter methods - no setters after construction
    // Prevents accidental modification
}

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // No update or delete methods - append-only

    @Query("SELECT a FROM AuditLog a WHERE a.resourceType = :type AND a.resourceId = :id ORDER BY a.timestamp DESC")
    List<AuditLog> findByResource(String type, String id);

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.timestamp >= :since")
    List<AuditLog> findByUserSince(String userId, Instant since);
}

@Component
@Aspect
public class AuditInterceptor {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private HttpServletRequest request;

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void auditAccess(JoinPoint joinPoint, Audited audited, Object result) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return;

        AuditLog log = AuditLog.builder()
                .userId(auth.getName())
                .timestamp(Instant.now())
                .action(audited.action())
                .resourceType(audited.resourceType())
                .resourceId(extractResourceId(joinPoint, result))
                .ipAddress(getClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .details(buildDetails(joinPoint))
                .build();

        auditLogRepository.save(log);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

// Custom annotation for audit tracking
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();
    String resourceType();
}

// Usage
@Service
public class PatientService {

    @Audited(action = "READ", resourceType = "PATIENT")
    @PreAuthorize("hasPermission(#patientId, 'Patient', 'read')")
    public PatientDto getPatient(Long patientId) {
        // Access is automatically logged after method returns
        return patientRepository.findById(patientId)
                .map(this::toDto)
                .orElseThrow(() -> new PatientNotFoundException(patientId));
    }
}
```

### Pattern 5: TLS 1.3 Configuration for Data in Transit

**What:** Configure Spring Boot to enforce TLS 1.3 encryption for all HTTPS connections
**When to use:** In production environments handling PHI
**Example:**
```yaml
# application-prod.yml
server:
  port: 8443
  ssl:
    enabled: true
    protocol: TLS
    enabled-protocols: TLSv1.3,TLSv1.2  # Prefer TLS 1.3, fallback to 1.2
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: hospital-api
    # Strong cipher suites only
    ciphers: >
      TLS_AES_256_GCM_SHA384,
      TLS_AES_128_GCM_SHA256,
      TLS_CHACHA20_POLY1305_SHA256,
      TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
      TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256

# Force HTTPS redirect
  http2:
    enabled: true

# Security headers
spring:
  security:
    require-ssl: true
```

```java
// Programmatic HTTPS redirect configuration
@Configuration
public class HttpsRedirectConfig {

    @Bean
    public TomcatServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                SecurityConstraint securityConstraint = new SecurityConstraint();
                securityConstraint.setUserConstraint("CONFIDENTIAL");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                securityConstraint.addCollection(collection);
                context.addConstraint(securityConstraint);
            }
        };

        // Add HTTP connector that redirects to HTTPS
        tomcat.addAdditionalTomcatConnectors(httpConnector());
        return tomcat;
    }

    private Connector httpConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setPort(8080);
        connector.setSecure(false);
        connector.setRedirectPort(8443);
        return connector;
    }
}
```

### Anti-Patterns to Avoid

- **Storing encryption keys in code or version control:** Use environment variables or secrets manager (HashiCorp Vault, AWS Secrets Manager)
- **Enabling Spring Actuator endpoints in production without security:** Exposes heap dumps, environment variables, and metrics - restrict to /health and /info only
- **Role-based authorization without object-level checks:** "hasRole('DOCTOR')" alone doesn't verify THIS doctor can access THIS patient
- **Encrypting searchable fields:** Field-level encryption breaks database indexes - only encrypt non-searchable PHI (SSN, insurance policy)
- **Using same encryption key for all fields:** Use different keys for different sensitivity levels or implement key rotation
- **Application-level audit logging without database-level tracking:** pgAudit captures direct database access bypassing application layer
- **Short JWT expiration without refresh tokens:** Forces frequent re-authentication - implement refresh token pattern for better UX

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JWT creation and validation | Custom token parser, manual signing | JJWT library (io.jsonwebtoken) | Handles algorithm vulnerabilities, token expiration, signature validation, and edge cases |
| Encryption algorithms | Custom AES implementation | Java Cryptography Architecture (JCA) with Bouncy Castle | Production-tested, handles padding, IV generation, key derivation correctly |
| Password hashing | MD5, SHA-256, or custom hash | Spring Security's BCryptPasswordEncoder | Uses adaptive hashing with salt, configurable work factor, timing-attack resistant |
| Audit log immutability | Application-level "no delete" logic | PostgreSQL pgAudit + table permissions | Database-enforced immutability, captures direct SQL access, tamper-evident |
| HIPAA risk assessment tool | Custom spreadsheet or forms | HHS SRA Tool or commercial HIPAA platform | Ensures comprehensive coverage of HIPAA technical safeguards |
| Secrets management | Encrypted config files | HashiCorp Vault, AWS Secrets Manager, or environment variables | Centralized rotation, access logging, integration with cloud platforms |
| Session management with JWT | Custom token storage in database | Spring Security stateless sessions | Built-in CSRF protection when needed, handles concurrent sessions, token revocation patterns |
| Access control logic | Custom if/else authorization | Spring Security's @PreAuthorize with PermissionEvaluator | Declarative, testable, audit-friendly, supports SpEL expressions |

**Key insight:** Security is uniquely dangerous for custom implementations. Cryptography, authentication, and authorization have subtle edge cases (timing attacks, replay attacks, token fixation, etc.) that are already solved by established libraries. Building custom security code introduces unknown vulnerabilities that won't be discovered until a breach occurs. In healthcare with PHI, the regulatory and reputational cost of security failures is catastrophic.

## Common Pitfalls

### Pitfall 1: CVE-2025-22235 - Spring Boot Actuator Endpoint Misconfiguration

**What goes wrong:** Spring Boot 3.4.0-3.4.4 has a vulnerability where `EndpointRequest.to()` creates an incorrect path matcher (`/null/**`) if the targeted actuator endpoint is disabled or not exposed, potentially allowing unauthorized access to protected resources.

**Why it happens:** Incorrect handling of disabled actuator endpoints in Spring Security configuration causes path matcher to fail to protect certain paths.

**How to avoid:** Upgrade to Spring Boot 3.4.5+ immediately. Do not use Spring Boot 3.4.0-3.4.4 in production for HIPAA applications.

**Warning signs:** Application uses Spring Security with `EndpointRequest.to()`, actuator endpoints are disabled or not exposed via web, application handles requests to `/null` path.

### Pitfall 2: Exposed Spring Boot Actuator Endpoints in Production

**What goes wrong:** Exposing actuator endpoints like `/actuator/heapdump`, `/actuator/env`, or `/actuator/metrics` in production allows attackers to download heap dumps containing plaintext credentials, encryption keys, patient data, and AWS credentials. A Volkswagen breach exposed 9TB of GPS data through an open `/actuator/heapdump` endpoint.

**Why it happens:** Developers enable actuator endpoints for monitoring during development and forget to secure or disable them in production. Default Spring Boot 2.x configuration exposes `/health` and `/info` publicly.

**How to avoid:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info  # Only non-sensitive endpoints
  endpoint:
    health:
      show-details: when-authorized  # Don't leak internal details
```
Add Spring Security rules to protect actuator endpoints:
```java
.requestMatchers("/actuator/**").hasRole("ADMIN")
```

**Warning signs:** `/actuator/` returns JSON with endpoint list, `/actuator/heapdump` downloads a file, monitoring dashboards directly query actuator endpoints without authentication.

### Pitfall 3: Field-Level Encryption on Searchable Fields

**What goes wrong:** Encrypting fields that need to be searched (patient name, email, phone) makes database indexes useless. Queries become full table scans, search performance degrades from milliseconds to seconds, and LIKE queries return no results.

**Why it happens:** Over-application of "encrypt everything" philosophy without considering query patterns. Confusion between data-at-rest encryption (PostgreSQL TDE, disk encryption) and field-level encryption.

**How to avoid:** Only encrypt non-searchable sensitive fields (SSN, insurance policy number, credit card). Use PostgreSQL Transparent Data Encryption or OS-level full-disk encryption (LUKS, BitLocker) for searchable fields - this provides data-at-rest encryption without breaking indexes.

**Warning signs:** Search queries taking >5 seconds on small datasets, database CPU usage spiking during searches, query explain plans showing sequential scans on encrypted columns.

### Pitfall 4: Role-Based Authorization Without Object-Level Checks

**What goes wrong:** Checking only role (`@PreAuthorize("hasRole('DOCTOR')")`) allows any doctor to access any patient record, violating HIPAA minimum necessary standard and object-level authorization requirement (SEC-07). An attacker with stolen doctor credentials can exfiltrate entire patient database.

**Why it happens:** Developers implement authentication and role-based access control (RBAC) but miss the critical object-level authorization layer. Common in applications migrated from internal tools where all users had broad access.

**How to avoid:** Implement custom `PermissionEvaluator` that checks "can THIS user access THIS resource":
```java
@PreAuthorize("hasPermission(#patientId, 'Patient', 'read')")
public PatientDto getPatient(Long patientId) { ... }
```
The PermissionEvaluator should query patient-provider assignments, department associations, or role-specific access rules.

**Warning signs:** Authorization checks only at controller level with role checks, no user-resource relationship tables in database schema, audit logs don't show authorization failures, penetration testing reveals horizontal privilege escalation.

### Pitfall 5: Hardcoded Secrets in Application Code or Configuration Files

**What goes wrong:** Encryption keys, JWT secrets, database passwords, or API keys hardcoded in `application.properties` or Java files get committed to version control (Git), exposed in CI/CD logs, or leaked in Docker images. Anyone with repository access can decrypt patient data or generate valid JWT tokens.

**Why it happens:** Convenience during development, lack of understanding of secrets management, friction of setting up external secrets managers.

**How to avoid:**
- Use environment variables: `${JWT_SECRET}` in application.yml
- Use Spring Cloud Vault for HashiCorp Vault integration
- Use AWS Secrets Manager with Spring Cloud AWS
- Never commit secrets to Git - use `.gitignore` for local override files
- Rotate secrets regularly and audit secret access

**Warning signs:** Secrets visible in `application.properties` or `application.yml` files, no environment variable usage, secrets in Docker image layers (use build args or mount secrets), Git history contains passwords.

### Pitfall 6: Insufficient Audit Log Detail for HIPAA Compliance

**What goes wrong:** Audit logs capture "Patient 123 was accessed" but miss critical HIPAA-required elements: which user, what action, timestamp, source IP, device information. OCR audits fail because logs can't reconstruct who accessed PHI and when.

**Why it happens:** Minimal logging implementation focuses on application debugging rather than compliance requirements. Developers don't understand HIPAA audit trail requirements (164.312(b) and 164.308(a)(1)(ii)(D)).

**How to avoid:** Every audit log entry MUST include:
- User ID (unique identifier, not display name)
- Timestamp (with timezone, ideally UTC)
- Action (CREATE, READ, UPDATE, DELETE, SEARCH)
- Resource type and ID (PATIENT, id=12345)
- Source IP address (use X-Forwarded-For for load balancers)
- User agent / device information
- Result (SUCCESS, FAILURE, reason for failure)

Implement both application-level audit logging (Spring AOP interceptor) and database-level audit logging (pgAudit).

**Warning signs:** Audit logs missing IP address or user agent, timestamps without timezone, no logging of failed access attempts, audit logs only at database level (misses application-layer authorization failures).

### Pitfall 7: Audit Log Retention Policy Not Enforced

**What goes wrong:** HIPAA requires 6-year audit log retention, but logs are deleted after 90 days or rotated out by log aggregation tools. OCR audit fails because historical access logs are unavailable.

**Why it happens:** Database size concerns, default log rotation policies in logging frameworks, lack of retention policy implementation.

**How to avoid:**
- Use PostgreSQL table partitioning by month/year for audit logs
- Implement automated archival to cold storage (AWS S3 Glacier) for logs older than 1 year
- Document retention policy in HIPAA compliance documentation
- Test restoration of archived logs quarterly
- Monitor audit log disk usage and alert before hitting limits

```sql
-- Partition audit logs by year for 6-year retention
CREATE TABLE audit_logs_2026 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

-- Archive to cold storage after 1 year
-- Keep partitions for 6 years before dropping
```

**Warning signs:** Audit logs older than 90 days unavailable, no documented retention policy, logs stored only in application logs (not database), no tested restoration procedure.

### Pitfall 8: JWT Secret Key Too Short or Predictable

**What goes wrong:** Using a weak JWT secret key (< 256 bits) or predictable values ("secret", "password", application name) allows attackers to brute-force the signing key and forge valid JWT tokens, granting unlimited access to patient data.

**Why it happens:** Developers use example keys from tutorials in production, generate keys manually without sufficient entropy.

**How to avoid:**
- Generate JWT secret with cryptographically secure random number generator
- Minimum 512 bits (64 characters) for HS512 algorithm
- Store in environment variable or secrets manager
- Rotate keys periodically (every 90 days)
- Use different keys for different environments (dev, staging, prod)

```bash
# Generate secure JWT secret
openssl rand -base64 64
```

**Warning signs:** JWT secret is short (< 32 characters), secret is a dictionary word, same secret used across environments, secret never rotated since initial deployment.

## Code Examples

Verified patterns from official sources and widely-adopted community practices:

### JWT Token Generation and Validation (JJWT 0.13.0)
```java
// Source: JJWT 0.13.0 documentation and Spring Security 6 integration patterns
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long jwtExpiration;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                           @Value("${jwt.expiration:3600000}") long expiration) {
        // JJWT 0.13.0 requires proper key size for HS512
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.jwtExpiration = expiration;
    }

    public String generateToken(String username, Collection<? extends GrantedAuthority> authorities) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            // Invalid JWT
            return false;
        }
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
```

### PostgreSQL pgAudit Configuration
```sql
-- Source: pgAudit official documentation and HIPAA compliance guides

-- Install pgAudit extension
CREATE EXTENSION IF NOT EXISTS pgaudit;

-- Configure pgAudit to log all operations on patient tables
-- Log: READ, WRITE, DDL on patient-related tables
ALTER DATABASE hospital_db SET pgaudit.log = 'read, write, ddl';
ALTER DATABASE hospital_db SET pgaudit.log_catalog = 'off';  -- Don't log system catalog
ALTER DATABASE hospital_db SET pgaudit.log_relation = 'on';  -- Log relation names
ALTER DATABASE hospital_db SET pgaudit.log_statement_once = 'off';  -- Log every statement
ALTER DATABASE hospital_db SET pgaudit.log_parameter = 'on';  -- Log query parameters

-- Create audit log role for reviewing logs (read-only)
CREATE ROLE audit_reviewer;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO audit_reviewer;

-- Application role should NOT have DELETE access to audit_logs table
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    ip_address INET,
    user_agent TEXT,
    details JSONB
);

-- Prevent updates and deletes on audit logs
CREATE RULE audit_logs_no_update AS ON UPDATE TO audit_logs DO INSTEAD NOTHING;
CREATE RULE audit_logs_no_delete AS ON DELETE TO audit_logs DO INSTEAD NOTHING;

-- Grant only INSERT permission to application role
GRANT INSERT ON audit_logs TO hospital_app;
REVOKE UPDATE, DELETE ON audit_logs FROM hospital_app;

-- Partition audit_logs by year for retention management
CREATE TABLE audit_logs_2026 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-01-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

-- Create index for common queries
CREATE INDEX idx_audit_logs_user_timestamp ON audit_logs_2026(user_id, timestamp DESC);
CREATE INDEX idx_audit_logs_resource ON audit_logs_2026(resource_type, resource_id);
```

### Spring Security Configuration with JWT and Method Security
```java
// Source: Spring Security 6.4 official documentation and Spring Boot 3.4 best practices
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Enables @PreAuthorize, @PostAuthorize
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless API (JWT in Authorization header)
            .csrf(csrf -> csrf.disable())

            // Stateless session management
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Exception handling
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                    // Public endpoints
                    .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                    // Actuator admin endpoints
                    .requestMatchers("/actuator/**").hasRole("ADMIN")

                    // Patient API - role-based + object-level in service layer
                    .requestMatchers("/api/patients/**")
                        .hasAnyRole("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST")

                    // All other requests require authentication
                    .anyRequest().authenticated())

            // Add JWT filter before username/password authentication filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with strength 12 (2^12 iterations)
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
```

### Secrets Management with Environment Variables
```yaml
# application.yml - No secrets here, use placeholders
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/hospital_db
    username: ${DB_USERNAME}  # From environment variable
    password: ${DB_PASSWORD}  # From environment variable

jwt:
  secret: ${JWT_SECRET}  # From environment variable
  expiration: 3600000  # 1 hour

encryption:
  key: ${ENCRYPTION_KEY}  # From environment variable
  algorithm: AES/GCM/NoPadding
```

```bash
# .env file (never commit to git - add to .gitignore)
DB_USERNAME=hospital_app
DB_PASSWORD=<generated secure password>
JWT_SECRET=<64 character base64 encoded random string>
ENCRYPTION_KEY=<32 byte base64 encoded key>
```

```java
// Loading encryption key from environment
@Configuration
public class EncryptionConfig {

    @Value("${encryption.key}")
    private String encryptionKeyBase64;

    @Bean
    public SecretKey encryptionKey() {
        byte[] decodedKey = Base64.getDecoder().decode(encryptionKeyBase64);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Spring Security 5.x `WebSecurityConfigurerAdapter` | Spring Security 6.x component-based configuration with `SecurityFilterChain` bean | Spring Security 6.0 (Nov 2022) | `WebSecurityConfigurerAdapter` deprecated, lambda DSL required |
| `@EnableGlobalMethodSecurity` | `@EnableMethodSecurity` | Spring Security 6.0 (Nov 2022) | Simpler configuration, better performance |
| JJWT legacy dependency (single JAR) | JJWT 0.13.0 modular (jjwt-api, jjwt-impl, jjwt-jackson) | JJWT 0.10.0 (2019) | Better separation of concerns, reduced dependency conflicts |
| TLS 1.2 only | TLS 1.3 preferred with TLS 1.2 fallback | HIPAA updated Jan 2024 | Faster handshakes, removed legacy vulnerabilities |
| Spring Boot 2.x | Spring Boot 3.x with Java 17+ | Spring Boot 3.0 (Nov 2022) | Jakarta EE namespace change, requires Java 17 minimum |
| `@PreAuthorize` with String-based expressions | `@PreAuthorize` with SpEL and custom PermissionEvaluator | Always available, increasingly adopted | More flexible object-level authorization |
| Application-level audit logging only | pgAudit + application-level audit logging | pgAudit 1.0 (2014), widely adopted by 2020 | Captures direct database access, immutable logs |
| Jasypt for all encryption | JPA AttributeConverter with JCA for field-level | JPA 2.1 (2013), patterns matured 2018-2020 | More transparent, better performance, cleaner separation |

**Deprecated/outdated:**
- **`WebSecurityConfigurerAdapter`**: Deprecated in Spring Security 5.7, removed in 6.0 - use `SecurityFilterChain` bean instead
- **H2 database for testing**: Doesn't match PostgreSQL behavior (transactions, constraints, types) - use Testcontainers with PostgreSQL
- **JJWT legacy single-JAR dependency** (`io.jsonwebtoken:jjwt:0.9.1`): Use modular dependencies (jjwt-api, jjwt-impl, jjwt-jackson)
- **TLS 1.0/1.1**: Prohibited by HIPAA and PCI-DSS - minimum TLS 1.2, prefer TLS 1.3
- **MD5, SHA-1 for password hashing**: Cryptographically broken - use BCrypt, Argon2, or PBKDF2
- **Spring Boot 2.7**: Reached end-of-life, no security patches - must use Spring Boot 3.x
- **Storing JWT in localStorage**: XSS vulnerability - use HttpOnly cookies or Authorization header from secure storage

## Open Questions

### 1. Secrets Management Strategy for Production

**What we know:** Application needs to store JWT secret, database password, encryption keys securely. Environment variables work but require secure CI/CD pipeline. HashiCorp Vault and AWS Secrets Manager are production-grade solutions with audit logging and rotation.

**What's unclear:**
- Does the deployment environment already have HashiCorp Vault or AWS Secrets Manager available?
- Is there existing organizational infrastructure for secrets management?
- What is the secrets rotation policy (90 days, 180 days, manual)?

**Recommendation:** Start with environment variables for Phase 0 (documented as temporary solution). Create task to evaluate and integrate HashiCorp Vault or AWS Secrets Manager in Phase 1 after infrastructure decisions are made. Document current approach as "acceptable for development, must be replaced before production deployment."

### 2. HIPAA Risk Assessment Tool and Documentation

**What we know:** HIPAA requires Security Risk Assessment documenting all PHI storage and transmission paths (SEC-01). HHS provides a free SRA tool but it's not comprehensive for all scenarios. No standard template exists.

**What's unclear:**
- Is there existing organizational HIPAA compliance framework or consultant?
- Will Risk Assessment be performed internally or by external compliance firm?
- What format does the Risk Assessment need to be in (PDF, spreadsheet, compliance platform)?

**Recommendation:** Create a Phase 0 task to identify all PHI touchpoints (database tables, API endpoints, log files, backups) and document them in markdown format. This preliminary documentation can later be fed into formal SRA tool or consultant process. Don't block development waiting for perfect compliance tool - start documenting now.

### 3. Database Encryption at Rest Strategy

**What we know:** SEC-03 requires encryption at rest. Options are: (1) PostgreSQL Transparent Data Encryption (Percona extension, 2026), (2) OS-level full-disk encryption (LUKS, BitLocker), (3) Cloud provider encryption (AWS RDS KMS). Field-level encryption (SEC-05) is separate and handled by JPA AttributeConverter.

**What's unclear:**
- What is the deployment environment (on-premise, AWS, Azure, GCP)?
- Is PostgreSQL self-managed or managed service (RDS, Aurora)?
- Does OS-level encryption already exist in infrastructure?

**Recommendation:** If using AWS RDS, enable KMS encryption at rest (single checkbox, FIPS 140-2 validated). If self-managed PostgreSQL, use OS-level full-disk encryption (simpler than TDE extension). Document chosen approach in Phase 0. All options meet HIPAA requirements - choose based on deployment environment.

### 4. Object-Level Authorization Complexity

**What we know:** SEC-07 requires checking "can THIS user access THIS patient" on every data access. Pattern is clear: custom `PermissionEvaluator` with `@PreAuthorize("hasPermission(#patientId, 'Patient', 'read')")`.

**What's unclear:**
- What are the exact authorization rules? (Doctor can access assigned patients only? Department-based access? Care team based?)
- Is there a patient-provider assignment table in the data model?
- Do roles have different read vs write permissions?

**Recommendation:** Create placeholder `PermissionEvaluator` in Phase 0 that implements basic rules: ADMIN can access all, others can access based on simple role check. Document that detailed authorization rules will be refined in Phase 1 when user roles and patient assignment workflows are defined. Critical: framework must be in place, even if rules are simplified initially.

### 5. Audit Log Storage and Archival Strategy

**What we know:** SEC-02 requires 6-year retention of audit logs. PostgreSQL partitioning by year is standard approach. Logs older than 1-2 years should be archived to cold storage to control database size.

**What's unclear:**
- What is the expected audit log volume (depends on user count and activity)?
- Is there cold storage infrastructure (AWS S3 Glacier, tape backup)?
- What is the restoration SLA for archived logs (OCR audit requires access within reasonable timeframe)?

**Recommendation:** Implement PostgreSQL partitioning in Phase 0 with 2026, 2027, 2028 partitions created upfront. Document that archival strategy (after year 1-2) will be implemented based on actual log volume and storage costs. Monitor audit log table size weekly. Plan for approximately 1-5GB per year for small-to-medium deployment (1000s of records daily).

## Sources

### Primary (HIGH confidence)

**Spring Boot and Spring Security:**
- [Spring Boot 3.4 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes) - Version compatibility, managed dependencies
- [Maven Repository: Spring Boot 3.4.5](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-dependencies/3.4.5) - Dependency versions
- [CVE-2025-22235 HeroDevs](https://www.herodevs.com/vulnerability-directory/cve-2025-22235) - Security vulnerability details

**JWT Authentication:**
- [JJWT GitHub Repository](https://github.com/jwtk/jjwt) - Official JJWT documentation and patterns
- [Maven Repository: io.jsonwebtoken](https://mvnrepository.com/artifact/io.jsonwebtoken/jjwt-api) - Latest versions

**HIPAA Compliance:**
- [HIPAA Journal: Encryption Requirements 2026](https://www.hipaajournal.com/hipaa-encryption-requirements/) - Required vs addressable safeguards, algorithms
- [HIPAA Journal: Risk Assessment](https://www.hipaajournal.com/hipaa-risk-assessment/) - Risk assessment requirements and process
- [HHS.gov: Guidance on Risk Analysis](https://www.hhs.gov/hipaa/for-professionals/security/guidance/guidance-risk-analysis/index.html) - Official HIPAA risk analysis guidance

**Audit Logging:**
- [Compliancy Group: HIPAA Audit Log Requirements](https://compliancy-group.com/hipaa-audit-log-requirements/) - Required audit elements
- [Kiteworks: HIPAA Audit Logs 2025](https://www.kiteworks.com/hipaa-compliance/hipaa-audit-log-requirements/) - Comprehensive HIPAA audit requirements
- [Censinet: HIPAA Audit Logs for PHI](https://censinet.com/perspectives/hipaa-audit-logs-key-requirements-for-phi-transfers) - PHI access tracking specifics

**PostgreSQL Security:**
- [PostgreSQL pgAudit Official Site](https://www.pgaudit.org/) - Official extension documentation
- [GitHub: HIPAA PostgreSQL](https://github.com/netreconlab/hipaa-postgres) - HIPAA-compliant PostgreSQL Docker configuration
- [pgAudit Extension GitHub](https://github.com/pgaudit/pgaudit) - Configuration examples

### Secondary (MEDIUM confidence)

**Spring Security Patterns:**
- [Build a Role-based Access Control in Spring Boot 3](https://blog.tericcabrel.com/role-base-access-control-spring-boot/) - RBAC implementation patterns
- [Spring Security @PreAuthorize Annotation](https://www.baeldung.com/spring-security-method-security) - Method security patterns
- [GitHub: Spring Security 6 JWT](https://github.com/daniel-pereira-guimaraes/spring-security6-jwt) - Complete JWT example

**Field-Level Encryption:**
- [Medium: Field Level Encryption in Spring Boot](https://medium.com/@AlexanderObregon/field-level-encryption-in-spring-boot-database-records-6cb56d9aae56) - JPA AttributeConverter implementation
- [GeeksforGeeks: Spring Boot Column Level Encryption](https://www.geeksforgeeks.org/spring-boot-enhancing-data-security-column-level-encryption/) - Encryption patterns
- [GitHub: spring-data-jpa-encryption-example](https://github.com/damienbeaufils/spring-data-jpa-encryption-example) - Working code example

**TLS Configuration:**
- [OneUpTime: TLS 1.3 and mTLS in Spring Boot](https://oneuptime.com/blog/post/2026-01-25-spring-boot-tls-mtls-configuration/view) - TLS 1.3 configuration guide
- [Baeldung: TLS Setup in Spring](https://www.baeldung.com/spring-tls-setup) - Spring TLS patterns

**Secrets Management:**
- [OneUpTime: HashiCorp Vault in Spring Boot](https://oneuptime.com/blog/post/2026-01-25-secrets-hashicorp-vault-spring-boot/view) - Vault integration guide
- [Medium: Spring Secret Starter](https://medium.com/spring-boot-world/spring-secret-starter-managing-secrets-in-your-spring-boot-app-486b72403909) - Secrets management patterns

**Modular Monolith Architecture:**
- [Bell Software: Spring Modulith Guide](https://bell-sw.com/blog/what-is-spring-modulith-introduction-to-modular-monoliths/) - Modular monolith patterns
- [Baeldung: Introduction to Spring Modulith](https://www.baeldung.com/spring-modulith) - Architecture patterns
- [ITNEXT: Securing Modular Monolith with OAuth2](https://itnext.io/securing-modular-monolith-with-oauth2-and-spring-security-43f2504c4e2e) - Security in modular monolith

**Security Best Practices:**
- [Escape Tech: Spring Boot Security Best Practices](https://escape.tech/blog/security-best-practices-for-spring-boot-applications/) - Production security patterns
- [CronJ: Spring Boot Security Audit and Compliance 2025](https://www.cronj.com/blog/spring-boot-security-audit-compliance/) - Compliance patterns
- [SYSCREST: Securing Spring Boot Actuator](https://www.syscrest.com/2025/02/securing-spring-boot-actuator/) - Actuator security configuration

**Audit Logging Implementation:**
- [Medium: Production-Ready Audit Logs in PostgreSQL](https://medium.com/@sehban.alam/lets-build-production-ready-audit-logs-in-postgresql-7125481713d8) - Implementation guide
- [Neon: PostgreSQL Logging vs pgAudit](https://neon.com/blog/postgres-logging-vs-pgaudit) - Comparison of approaches
- [OneUpTime: PostgreSQL pgAudit Track Data Changes](https://oneuptime.com/blog/post/2026-01-25-postgresql-pgaudit-track-data-changes/view) - pgAudit configuration

### Tertiary (LOW confidence - requires validation)

**Security Vulnerabilities:**
- [HIPAA Journal: Spring Vulnerabilities Warning](https://www.hipaajournal.com/warnings-issued-about-vulnerabilities-in-the-spring-application-building-platform-and-ups-devices/) - General security warnings
- [AppSecMaster: Top Spring Boot Security Risks](https://www.appsecmaster.net/blog/top-spring-boot-security-risks-every-developer-should-know/) - Common vulnerabilities

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Verified through official Maven repositories, GitHub releases, and Spring Boot managed dependencies
- Architecture: HIGH - Patterns verified across multiple authoritative sources (Baeldung, official Spring guides, GitHub examples)
- Field-level encryption: HIGH - JPA AttributeConverter pattern verified in multiple implementations
- Object-level authorization: MEDIUM-HIGH - Pattern is established but requires customization based on authorization rules
- HIPAA requirements: HIGH - Official HHS guidance and established healthcare compliance sources
- Audit logging: HIGH - pgAudit official documentation and multiple healthcare implementation guides
- Pitfalls: HIGH - Verified through CVE databases, security advisories, and real-world breach examples (Volkswagen)
- TLS configuration: MEDIUM - Patterns verified but certificate management requires environment-specific implementation
- Secrets management: MEDIUM - Patterns established but choice depends on infrastructure availability

**Research date:** 2026-02-19
**Valid until:** Approximately 2026-05-19 (90 days for security-related research due to evolving threat landscape and frequent security patches)

**Notes:**
- Spring Boot 3.4.5+ is MANDATORY due to CVE-2025-22235 (HIGH severity)
- No standard HIPAA Risk Assessment template exists - must document all PHI touchpoints manually
- Field-level encryption should ONLY be applied to non-searchable fields (SSN, insurance policy)
- Object-level authorization (SEC-07) is critical for HIPAA compliance and requires custom PermissionEvaluator
- Audit logs MUST be append-only with 6-year retention - use PostgreSQL partitioning and pgAudit
- Spring Actuator endpoints are a major security risk - restrict to /health and /info in production
