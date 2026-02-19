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

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

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
        if (resourceId == null) {
            log.warn("Cannot log audit event - no resource ID found for method: {}",
                    joinPoint.getSignature().getName());
            return;
        }

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
     * Tries method arguments first (Long/String), then tries getId() on result.
     *
     * @param joinPoint the join point
     * @param result the method return value
     * @return the resource ID, or null if not found
     */
    private String extractResourceId(JoinPoint joinPoint, Object result) {
        // Try to find ID in method arguments (first Long or String)
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof Long || arg instanceof String) {
                return arg.toString();
            }
        }

        // Try to extract ID from result using reflection (getId() pattern)
        if (result != null) {
            try {
                Method getId = result.getClass().getMethod("getId");
                Object id = getId.invoke(result);
                return id != null ? id.toString() : null;
            } catch (Exception e) {
                // No getId method or invocation failed
                log.trace("Could not extract ID from result: {}", e.getMessage());
            }
        }

        return null;
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
