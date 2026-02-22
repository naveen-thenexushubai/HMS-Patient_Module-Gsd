package com.hospital.security.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spring AOP interceptor for audit logging.
 * Automatically logs all @Audited method calls with full context.
 */
@Slf4j
@Component
@Aspect
@RequiredArgsConstructor
public class AuditInterceptor {

    private final AuditLogRepository auditLogRepository;

    @Autowired(required = false)
    private HttpServletRequest request;

    /**
     * Intercepts all methods annotated with @Audited.
     * Logs the access event after successful method execution.
     *
     * @param joinPoint the join point providing method context
     * @param audited the @Audited annotation instance
     * @param result the method return value
     */
    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void auditAccess(JoinPoint joinPoint, Audited audited, Object result) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Don't log unauthenticated or anonymous access
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return;
        }

        // Extract resource ID from method arguments or return value
        String resourceId = extractResourceId(joinPoint, result);

        // Build audit log entry
        AuditLog auditLog = AuditLog.builder()
                .userId(auth.getName())
                .timestamp(Instant.now())
                .action(audited.action())
                .resourceType(audited.resourceType())
                .resourceId(resourceId)
                .ipAddress(getClientIp())
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .details(buildDetails(joinPoint))
                .build();

        // Save to database
        auditLogRepository.save(auditLog);

        log.debug("Audit log created: user={}, action={}, resource={}/{}",
                auth.getName(), audited.action(), audited.resourceType(), resourceId);
    }

    /**
     * Get the client IP address from the request.
     * Checks X-Forwarded-For header for proxy scenarios.
     *
     * @return the client IP address, or null if request not available
     */
    private String getClientIp() {
        if (request == null) {
            return null;
        }

        // Check for X-Forwarded-For header (proxy/load balancer)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, get the first one
            return xForwardedFor.split(",")[0].trim();
        }

        // Fall back to remote address
        return request.getRemoteAddr();
    }

    /**
     * Extract resource ID from method arguments or return value.
     *
     * Resolution order:
     * 1. First UUID arg (patient businessId — most meaningful for HIPAA audit)
     * 2. First Long arg (sub-resource IDs: contactId, insuranceId, etc.)
     * 3. First non-null String arg (search query, username, etc.)
     * 4. Unwrap ResponseEntity body, then try getBusinessId / getPatientId / getId
     * 5. Fallback to method name — ensures every authenticated access is always logged
     *
     * @param joinPoint the join point
     * @param result the method return value
     * @return the resource ID, never null
     */
    private String extractResourceId(JoinPoint joinPoint, Object result) {
        Object[] args = joinPoint.getArgs();

        // 1. UUID first — patient businessId is the canonical HIPAA audit identifier
        for (Object arg : args) {
            if (arg instanceof UUID) {
                return arg.toString();
            }
        }

        // 2. Long — sub-resource IDs (contactId, photoId, etc.)
        for (Object arg : args) {
            if (arg instanceof Long) {
                return arg.toString();
            }
        }

        // 3. Non-null String — search queries, usernames, etc.
        for (Object arg : args) {
            if (arg instanceof String) {
                return (String) arg;
            }
        }

        // 4. Unwrap ResponseEntity, then try common ID getter names
        Object body = result;
        if (result instanceof ResponseEntity) {
            body = ((ResponseEntity<?>) result).getBody();
        }
        if (body != null) {
            for (String getter : List.of("getBusinessId", "getPatientId", "getId")) {
                try {
                    Object id = body.getClass().getMethod(getter).invoke(body);
                    if (id != null) return id.toString();
                } catch (Exception ignored) {}
            }
        }

        // 5. Fallback — method name ensures the log entry is always written
        return joinPoint.getSignature().getName();
    }

    /**
     * Build details map with method and class information.
     *
     * @param joinPoint the join point
     * @return map of detail information
     */
    private Map<String, Object> buildDetails(JoinPoint joinPoint) {
        Map<String, Object> details = new HashMap<>();
        try {
            if (joinPoint != null && joinPoint.getSignature() != null) {
                details.put("method", joinPoint.getSignature().getName());
            }
            if (joinPoint != null && joinPoint.getTarget() != null) {
                details.put("class", joinPoint.getTarget().getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.warn("Error building audit details: {}", e.getMessage());
        }
        // Always return non-null map (empty if errors)
        return details;
    }
}
