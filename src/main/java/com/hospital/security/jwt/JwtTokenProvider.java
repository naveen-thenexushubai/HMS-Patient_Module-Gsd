package com.hospital.security.jwt;

import com.hospital.security.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * JWT token provider for generating and validating JWT tokens.
 * Uses JJWT 0.13.0 API with HS512 signature algorithm.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtTokenProvider(JwtConfig jwtConfig) {
        String secret = jwtConfig.getSecret();

        // Validate secret length (minimum 64 characters for HS512)
        if (secret == null || secret.length() < 64) {
            throw new IllegalArgumentException(
                "JWT_SECRET must be at least 64 characters for HS512 algorithm"
            );
        }

        // Create SecretKey from secret string (JJWT 0.13.0 requirement)
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = jwtConfig.getExpiration();

        log.info("JwtTokenProvider initialized with expiration: {} ms", expiration);
    }

    /**
     * Generate JWT token with username and authorities.
     *
     * @param username the username (subject)
     * @param authorities the granted authorities (roles)
     * @return signed JWT token string
     */
    public String generateToken(String username, Collection<? extends GrantedAuthority> authorities) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        // Extract role names from authorities
        String roles = authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","));

        return Jwts.builder()
            .subject(username)
            .claim("roles", roles)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact();
    }

    /**
     * Validate JWT token.
     *
     * @param token the JWT token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract username from JWT token.
     *
     * @param token the JWT token
     * @return the username (subject)
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }

    /**
     * Extract all claims from JWT token.
     *
     * @param token the JWT token
     * @return the claims object
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

}
