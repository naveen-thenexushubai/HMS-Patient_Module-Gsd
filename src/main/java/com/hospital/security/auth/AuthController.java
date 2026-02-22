package com.hospital.security.auth;

import com.hospital.security.jwt.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller exposing JWT login endpoint.
 * POST /api/auth/login — public, no token required.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        String token = jwtTokenProvider.generateToken(
            authentication.getName(),
            authentication.getAuthorities()
        );

        // Extract the first role (strip ROLE_ prefix if present)
        String role = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
            .findFirst()
            .orElse("UNKNOWN");

        return ResponseEntity.ok(new LoginResponse(token, role, authentication.getName()));
    }
}
