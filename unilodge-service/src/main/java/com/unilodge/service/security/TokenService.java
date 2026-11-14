package com.unilodge.service.security;

import com.unilodge.common.exception.UnauthorizedException;
import com.unilodge.persistence.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.unilodge.service.config.AppProperties;

/**
 * Issues and validates the application's custom JWT tokens.
 *
 * <p>Access tokens identify a user. Purpose tokens are short lived and scoped
 * (for example password reset) so they cannot be reused as access tokens.</p>
 */
@Service
public class TokenService {

    public static final String SCOPE_PASSWORD_RESET = "password_reset";
    public static final String SCOPE_ADMIN_VERIFY = "admin_verify";

    private final SecretKey key;
    private final AppProperties properties;

    public TokenService(AppProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(sha256(properties.getJwt().getSecret()));
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofHours(properties.getJwt().getTtlHours()));
        return Jwts.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("adminVerified", user.isAdminVerified())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /** Validates an access token and returns the user id it belongs to. */
    public UUID parseAccessToken(String token) {
        Claims claims = parse(token);
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Your session is invalid. Please sign in again.");
        }
    }

    /** Creates a short-lived token tied to an email address and a flow scope. */
    public String issuePurposeToken(String email, String scope, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(email.toLowerCase())
                .claim("scope", scope)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Validates a purpose token and returns the email it was issued for. */
    public String parsePurposeToken(String token, String expectedScope) {
        Claims claims = parse(token);
        String scope = claims.get("scope", String.class);
        if (!expectedScope.equals(scope)) {
            throw new UnauthorizedException("This link is no longer valid. Please start again.");
        }
        return claims.getSubject();
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException("Your session has expired. Please sign in again.");
        }
    }

    private static byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
