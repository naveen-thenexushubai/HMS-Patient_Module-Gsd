package com.hospital.security.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation for marking methods that need audit logging.
 * Used by AuditInterceptor to automatically log PHI access.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    /**
     * The action type being performed.
     * Must be one of: CREATE, READ, UPDATE, DELETE, SEARCH
     */
    String action();

    /**
     * The type of resource being accessed.
     * Examples: PATIENT, INSURANCE, APPOINTMENT, etc.
     */
    String resourceType();
}
