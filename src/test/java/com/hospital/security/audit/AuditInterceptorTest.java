package com.hospital.security.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuditInterceptor.
 * Verifies that @Audited methods are automatically logged.
 */
@SpringBootTest
@Transactional
@org.springframework.test.context.ActiveProfiles("test")
class AuditInterceptorTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TestAuditedService testService;

    @BeforeEach
    void setUp() {
        // Set up authenticated user for tests
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test_user",
                        "password",
                        List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))
                )
        );
    }

    @Test
    void auditLog_capturesReadOperation() {
        // Call audited method
        testService.readPatient(123L);

        // Verify audit log was created
        List<AuditLog> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty(), "Audit log should be created");

        AuditLog log = logs.get(0);
        assertEquals("test_user", log.getUserId());
        assertNotNull(log.getTimestamp());
        assertEquals("READ", log.getAction());
        assertEquals("PATIENT", log.getResourceType());
        assertEquals("123", log.getResourceId());
        assertNotNull(log.getDetails(), "Details should not be null");
        if (log.getDetails() != null) {
            assertEquals("readPatient", log.getDetails().get("method"));
        }
    }

    @Test
    void auditLog_capturesCreateOperation() {
        // Clear any existing logs first
        auditLogRepository.deleteAll();

        // Call audited method that returns an object with ID
        TestPatient patient = testService.createPatient("John Doe");

        // Verify audit log was created
        List<AuditLog> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty(), "Audit log should be created");

        AuditLog log = logs.get(0);
        assertEquals("test_user", log.getUserId());
        assertEquals("CREATE", log.getAction());
        assertEquals("PATIENT", log.getResourceType());
        assertEquals("999", log.getResourceId());  // Mock ID from TestPatient
    }

    @Test
    void auditLog_notCreatedForUnauthenticatedUser() {
        // Clear any existing logs first
        auditLogRepository.deleteAll();

        // Clear authentication
        SecurityContextHolder.clearContext();

        // Call audited method
        testService.readPatient(123L);

        // Verify no audit log was created
        List<AuditLog> logs = auditLogRepository.findAll();
        assertTrue(logs.isEmpty(), "No audit log should be created for unauthenticated users");
    }

    @Test
    void findByResource_returnsCorrectLogs() {
        // Clear any existing logs first
        auditLogRepository.deleteAll();

        // Create multiple audit logs
        testService.readPatient(123L);
        testService.readPatient(123L);
        testService.readPatient(456L);

        // Find logs for specific resource
        List<AuditLog> logs = auditLogRepository.findByResource("PATIENT", "123");
        assertEquals(2, logs.size(), "Should find 2 logs for patient 123");
    }

    @Test
    void findByUserSince_returnsRecentLogs() {
        // Create audit log
        testService.readPatient(123L);

        // Find logs since 1 hour ago
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        List<AuditLog> logs = auditLogRepository.findByUserSince("test_user", oneHourAgo);

        assertFalse(logs.isEmpty(), "Should find recent logs");
        assertEquals("test_user", logs.get(0).getUserId());
    }
}

/**
 * Test service with @Audited methods.
 */
@org.springframework.stereotype.Service
class TestAuditedService {

    @Audited(action = "READ", resourceType = "PATIENT")
    public void readPatient(Long patientId) {
        // Mock method for testing
    }

    @Audited(action = "CREATE", resourceType = "PATIENT")
    public TestPatient createPatient(String name) {
        return new TestPatient(999L, name);
    }
}

/**
 * Test patient entity for testing.
 */
class TestPatient {
    private final Long id;
    private final String name;

    public TestPatient(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
