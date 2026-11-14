package com.unilodge.service.security;

import com.unilodge.common.exception.BadRequestException;
import com.unilodge.common.exception.TooManyRequestsException;
import com.unilodge.common.enums.OtpPurpose;
import com.unilodge.persistence.entity.OtpToken;
import com.unilodge.persistence.repository.OtpTokenRepository;
import com.unilodge.service.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Generates, stores and verifies one-time password codes.
 *
 * <p>Codes are never stored in plain text: only a keyed HMAC-SHA256 digest is
 * persisted, codes expire after ten minutes, allow five attempts and cannot be
 * requested more often than once every minute.</p>
 */
@Service
public class OtpService {

    public static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final OtpTokenRepository repository;
    private final AppProperties properties;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpTokenRepository repository, AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public record IssuedOtp(String code, Instant expiresAt) {
    }

    @Transactional
    public IssuedOtp issue(String email, OtpPurpose purpose) {
        String normalized = email.trim().toLowerCase();
        Optional<OtpToken> latest = repository
                .findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(normalized, purpose);
        if (latest.isPresent() && latest.get().getConsumedAt() == null
                && latest.get().getCreatedAt().plus(RESEND_COOLDOWN).isAfter(Instant.now())) {
            throw new TooManyRequestsException("Please wait a minute before requesting another code.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        OtpToken token = new OtpToken();
        token.setEmail(normalized);
        token.setPurpose(purpose);
        token.setCodeHash(hash(normalized, code, purpose));
        token.setAttempts(0);
        token.setExpiresAt(Instant.now().plus(CODE_LIFETIME));
        repository.save(token);
        return new IssuedOtp(code, token.getExpiresAt());
    }

    @Transactional
    public void verify(String email, OtpPurpose purpose, String code) {
        String normalized = email.trim().toLowerCase();
        OtpToken token = repository
                .findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(normalized, purpose)
                .orElseThrow(() -> new BadRequestException("OTP_NOT_FOUND",
                        "No verification code was requested. Please request a new code."));

        if (token.getConsumedAt() != null) {
            throw new BadRequestException("OTP_USED", "This code has already been used. Request a new one.");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("OTP_EXPIRED", "This code has expired. Please request a new one.");
        }
        if (token.getAttempts() >= MAX_ATTEMPTS) {
            throw new BadRequestException("OTP_LOCKED", "Too many incorrect attempts. Please request a new code.");
        }

        String expected = hash(normalized, code, purpose);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            token.setAttempts(token.getAttempts() + 1);
            repository.save(token);
            throw new BadRequestException("OTP_INVALID", "That code is not correct. Please try again.");
        }

        token.setConsumedAt(Instant.now());
        repository.save(token);
    }

    private String hash(String email, String code, OtpPurpose purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((email + "|" + purpose.name() + "|" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash OTP code", exception);
        }
    }
}
