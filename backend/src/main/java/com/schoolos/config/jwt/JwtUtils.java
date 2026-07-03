package com.schoolos.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtils {

    // Fixed, environment-provided secret so tokens survive restarts and work
    // across multiple instances (a random per-JVM key silently logs everyone
    // out on every deploy). HS256 requires >= 32 bytes.
    private final SecretKey secretKey;
    private static final long JWT_EXPIRATION_MS = 86400000; // 24 hours
    private static final String INSECURE_DEFAULT_SECRET = "dev-only-insecure-default-change-in-production-please-32bytes-min";

    public JwtUtils(@Value("${app.jwt-secret}") String jwtSecret,
                     @Value("${app.dev-mode:false}") boolean devMode) {
        if (!devMode && INSECURE_DEFAULT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                "app.jwt-secret is using the insecure dev-only default outside of dev mode. " +
                "Set the JWT_SECRET environment variable to a real random secret before starting with app.dev-mode=false."
            );
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        // Add roles to claims
        claims.put("roles", userDetails.getAuthorities());
        return createToken(claims, userDetails.getUsername());
    }

    /**
     * Embeds tenant_id/academic_year_id claims so a standalone-tier deployment
     * can scope/route requests without an extra DB lookup per request.
     */
    public String generateToken(UserDetails userDetails, UUID tenantId, UUID academicYearId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userDetails.getAuthorities());
        if (tenantId != null) claims.put("tenant_id", tenantId.toString());
        if (academicYearId != null) claims.put("academic_year_id", academicYearId.toString());
        return createToken(claims, userDetails.getUsername());
    }

    public UUID extractTenantId(String token) {
        String value = extractClaim(token, claims -> claims.get("tenant_id", String.class));
        return value != null ? UUID.fromString(value) : null;
    }

    public UUID extractAcademicYearId(String token) {
        String value = extractClaim(token, claims -> claims.get("academic_year_id", String.class));
        return value != null ? UUID.fromString(value) : null;
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION_MS))
                .signWith(secretKey)
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}
