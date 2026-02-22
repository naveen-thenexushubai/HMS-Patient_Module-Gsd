package com.hospital.security.auth;

public record LoginResponse(
    String token,
    String role,
    String username
) {}
