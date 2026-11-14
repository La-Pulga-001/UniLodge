package com.unilodge.service.security;

import com.unilodge.common.exception.TooManyRequestsException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory brute force protection for the sign-in endpoint.
 * After {@value #MAX_FAILURES} failed attempts within the window the account is
 * temporarily locked. Counters reset on a successful sign-in.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempts(int count, Instant firstAttempt) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public void checkAllowed(String email) {
        Attempts entry = attempts.get(normalize(email));
        if (entry == null) {
            return;
        }
        if (entry.firstAttempt().plus(WINDOW).isBefore(Instant.now())) {
            attempts.remove(normalize(email));
            return;
        }
        if (entry.count() >= MAX_FAILURES) {
            throw new TooManyRequestsException(
                    "Too many failed sign-in attempts. Please try again in a few minutes.");
        }
    }

    public void recordFailure(String email) {
        String key = normalize(email);
        attempts.compute(key, (ignored, existing) -> {
            if (existing == null || existing.firstAttempt().plus(WINDOW).isBefore(Instant.now())) {
                return new Attempts(1, Instant.now());
            }
            return new Attempts(existing.count() + 1, existing.firstAttempt());
        });
    }

    public void recordSuccess(String email) {
        attempts.remove(normalize(email));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
