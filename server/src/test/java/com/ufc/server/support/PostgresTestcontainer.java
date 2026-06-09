package com.ufc.server.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Postgres (Testcontainers) for the integration test suite. A single
 * container is started once for the whole JVM and reused across test classes, so
 * the matching engine is exercised against the same database engine it runs on
 * in production. That matters: the tests cover real JPA behaviour an in-memory DB
 * wouldn't — optimistic-lock ({@code @Version}) conflicts under concurrency, lazy
 * associations, and SQL-level ordering for price/time priority.
 *
 * <p>The container is a JVM-wide singleton (started in a static initialiser, never
 * explicitly stopped — Testcontainers' Ryuk reaps it), which avoids paying the
 * start-up cost once per test class.
 */
public abstract class PostgresTestcontainer {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        "postgres:16-alpine"
    );

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Full-context (@SpringBootTest) startup loads PopulateFightersTask, which
        // reads scraper.base-url and pings it once on ApplicationReady. Point it
        // nowhere reachable — the task catches and swallows the failure.
        registry.add("scraper.base-url", () -> "http://localhost:1");
    }
}
