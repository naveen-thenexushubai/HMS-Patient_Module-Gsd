package com.hospital.security.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Audit log entity for HIPAA-compliant tracking of all PHI access.
 * Immutable after creation — no setters, and @Immutable prevents Hibernate UPDATE.
 * The JSONB details column can cause dirty-check false positives; @Immutable prevents the
 * resulting UPDATE from hitting PostgreSQL's immutability trigger.
 */
@Entity
@Table(name = "audit_logs")
@org.hibernate.annotations.Immutable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String action;  // CREATE, READ, UPDATE, DELETE, SEARCH

    @Column(nullable = false)
    private String resourceType;

    @Column(nullable = false)
    private String resourceId;

    @Column(name = "ip_address")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.INET)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> details;
}
