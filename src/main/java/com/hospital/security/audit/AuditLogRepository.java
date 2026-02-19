package com.hospital.security.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Append-only repository for audit logs.
 * No update or delete methods - audit logs are immutable.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find all audit logs for a specific resource.
     *
     * @param type the resource type (e.g., PATIENT)
     * @param id the resource ID
     * @return list of audit logs ordered by timestamp descending
     */
    @Query("SELECT a FROM AuditLog a WHERE a.resourceType = :type AND a.resourceId = :id ORDER BY a.timestamp DESC")
    List<AuditLog> findByResource(@Param("type") String type, @Param("id") String id);

    /**
     * Find all audit logs for a user since a specific timestamp.
     *
     * @param userId the user ID
     * @param since the start timestamp
     * @return list of audit logs ordered by timestamp descending
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findByUserSince(@Param("userId") String userId, @Param("since") Instant since);
}
