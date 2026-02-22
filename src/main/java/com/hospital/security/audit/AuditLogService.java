package com.hospital.security.audit;

import com.hospital.security.audit.dto.AuditLogDto;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Paginated audit log query with optional filters.
     * Uses Specifications to avoid PostgreSQL null-type inference issues with JPQL.
     */
    public Page<AuditLogDto> getAuditLogs(
        String userId,
        String action,
        String resourceType,
        String resourceId,
        Instant startDate,
        Instant endDate,
        Pageable pageable
    ) {
        Specification<AuditLog> spec = buildSpec(userId, action, resourceType, resourceId, startDate, endDate);
        return auditLogRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * Audit logs for a specific patient (resourceType=PATIENT, resourceId=businessId).
     */
    public Page<AuditLogDto> getLogsForPatient(UUID businessId, Pageable pageable) {
        Specification<AuditLog> spec = buildSpec(null, null, "PATIENT", businessId.toString(), null, null);
        return auditLogRepository.findAll(spec, pageable).map(this::toDto);
    }

    private Specification<AuditLog> buildSpec(
        String userId, String action, String resourceType,
        String resourceId, Instant startDate, Instant endDate
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null && !userId.isBlank())
                predicates.add(cb.equal(root.get("userId"), userId));
            if (action != null && !action.isBlank())
                predicates.add(cb.equal(root.get("action"), action));
            if (resourceType != null && !resourceType.isBlank())
                predicates.add(cb.equal(root.get("resourceType"), resourceType));
            if (resourceId != null && !resourceId.isBlank())
                predicates.add(cb.equal(root.get("resourceId"), resourceId));
            if (startDate != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), startDate));
            if (endDate != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), endDate));
            // default order: newest first
            if (query.getOrderList().isEmpty()) {
                query.orderBy(cb.desc(root.get("timestamp")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AuditLogDto toDto(AuditLog log) {
        return AuditLogDto.builder()
            .id(log.getId())
            .userId(log.getUserId())
            .timestamp(log.getTimestamp())
            .action(log.getAction())
            .resourceType(log.getResourceType())
            .resourceId(log.getResourceId())
            .ipAddress(log.getIpAddress())
            .details(log.getDetails())
            .build();
    }
}
