package com.ufc.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * A strict per-IP throttle on account creation, layered on top of the generous
 * global {@code RateLimitInterceptor}. Stops a single host from minting a flood
 * of throwaway accounts (bot signups, abuse) while leaving normal sign-ups
 * untouched.
 *
 * <p>Token bucket: a small burst, refilling slowly. State is per-instance and
 * in-memory — fine for a single backend; a multi-instance deployment would need
 * a shared store.
 */
@Component
public class SignupRateLimiter {

    /** Burst: accounts one IP can create back-to-back. */
    private static final double CAPACITY = 5;
    /** Sustained refill (=> ~5 new accounts per hour per IP). */
    private static final double REFILL_PER_SECOND = 5.0 / 3600.0;
    /** Safety valve so the bucket map can't grow without bound. */
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final ConcurrentHashMap<String, Bucket> buckets =
        new ConcurrentHashMap<>();

    /** True if this request's IP may create an account right now. */
    public boolean tryAcquire(HttpServletRequest request) {
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            buckets.clear();
        }
        return buckets
            .computeIfAbsent(clientIp(request), k -> new Bucket())
            .tryConsume();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** A single token bucket. Synchronized: per-key contention is trivial. */
    private static final class Bucket {

        private double tokens = CAPACITY;
        private long lastRefillNanos = System.nanoTime();

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds <= 0) {
                return;
            }
            tokens = Math.min(
                CAPACITY,
                tokens + elapsedSeconds * REFILL_PER_SECOND
            );
            lastRefillNanos = now;
        }
    }
}
