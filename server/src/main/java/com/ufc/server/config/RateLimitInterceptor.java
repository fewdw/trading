package com.ufc.server.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * A small in-memory token-bucket rate limiter applied to every {@code /api/**}
 * request. Deliberately generous: it exists to blunt runaway clients and abuse,
 * not to meter normal use. Clients are bucketed by session token when
 * authenticated, otherwise by IP.
 *
 * <p>State is per-instance and in-memory — fine for a single backend. A
 * multi-instance deployment would need a shared store (e.g. Redis).
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** Max burst: tokens one client can spend back-to-back. */
    private static final double CAPACITY = 300;
    /** Sustained refill rate per second (=> ~600 requests/minute). */
    private static final double REFILL_PER_SECOND = 10;
    /** Safety valve so the bucket map can't grow without bound. */
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final ConcurrentHashMap<String, Bucket> buckets =
        new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) throws Exception {
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            buckets.clear(); // crude eviction; everyone gets a fresh bucket
        }
        Bucket bucket = buckets.computeIfAbsent(
            clientKey(request),
            k -> new Bucket()
        );
        if (bucket.tryConsume()) {
            return true;
        }

        long retryAfter = bucket.secondsUntilNextToken();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response
            .getWriter()
            .write(
                "{\"error\":\"rate limit exceeded\",\"retryAfterSeconds\":" +
                retryAfter +
                "}"
            );
        return false;
    }

    /** Authenticated clients are bucketed by session token; everyone else by IP. */
    private String clientKey(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return "tok:" + auth.substring("Bearer ".length()).trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
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

        synchronized long secondsUntilNextToken() {
            refill();
            if (tokens >= 1) {
                return 0;
            }
            return (long) Math.ceil((1 - tokens) / REFILL_PER_SECOND);
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
