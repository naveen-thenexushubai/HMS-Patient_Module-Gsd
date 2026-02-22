package com.hospital.security.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class AuditLogDto {
    private Long id;
    private String userId;
    private Instant timestamp;
    private String action;
    private String resourceType;
    private String resourceId;
    private String ipAddress;
    private Map<String, Object> details;
}
