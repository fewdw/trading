# syntax=docker/dockerfile:1.7

# ── Build stage ───────────────────────────────────────────────────────────
# Full JDK on Debian: the Maven wrapper is "only-script" and shells out to
# wget/unzip to fetch the distribution, which the slim alpine image lacks.
# Builder size is irrelevant — nothing from this stage ships in the final image.
FROM eclipse-temurin:26-jdk AS builder
WORKDIR /app

# Resolve dependencies first so this layer is cached until pom.xml changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q -DskipTests package

# ── Runtime stage ─────────────────────────────────────────────────────────
# Alpine JRE (musl) — pure-JVM bytecode runs identically and the image is
# roughly half the size of the Debian JRE. Runs as an unprivileged user.
FROM eclipse-temurin:26-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /app/target/*.jar app.jar
USER spring:spring

EXPOSE 8080
# MaxRAMPercentage lets the heap scale with the container memory limit;
# ExitOnOutOfMemoryError fails fast so the orchestrator can restart cleanly.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
