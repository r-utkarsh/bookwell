package com.bookwell.auth;

import com.bookwell.config.AppProperties;
import com.bookwell.user.Role;
import com.bookwell.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final AppProperties properties;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        this.key = buildKey(properties);
    }

    private SecretKey buildKey(AppProperties properties) {
        String configured = properties.security().jwtSecret();
        if (configured == null || configured.isBlank()) {
            if (properties.isProduction()) {
                throw new IllegalStateException(
                        "JWT_SECRET is required in production. Generate one with: openssl rand -base64 48");
            }
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            log.warn("JWT_SECRET is not set. Using an ephemeral development key; tokens will be invalid after restart.");
            return Keys.hmacShaKeyFor(random);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT_SECRET must be Base64-encoded. Generate one with: openssl rand -base64 48", ex);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes (256 bits).");
        }
        return Keys.hmacShaKeyFor(decoded);
    }

    public AccessToken issue(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.security().accessTokenTtl());
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new AccessToken(token, expiresAt, properties.security().accessTokenTtl().toSeconds());
    }

    public AccessTokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AccessTokenClaims(
                UUID.fromString(claims.getSubject()),
                claims.get("email", String.class),
                Role.valueOf(claims.get("role", String.class))
        );
    }

    public record AccessToken(String value, Instant expiresAt, long expiresInSeconds) {
    }

    public record AccessTokenClaims(UUID userId, String email, Role role) {
    }
}
