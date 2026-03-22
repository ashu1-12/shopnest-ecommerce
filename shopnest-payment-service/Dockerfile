# ╔════════════════════════════════════════════════════════════════╗
# ║         MULTI-STAGE DOCKERFILE — PAYMENT SERVICE              ║
# ║                                                                ║
# ║  Stage 1 (builder): compile + package with Maven               ║
# ║  Stage 2 (runtime): minimal JRE image with just the JAR        ║
# ║                                                                ║
# ║  Why multi-stage?                                              ║
# ║    Builder image is ~500MB (JDK + Maven + source).             ║
# ║    Runtime image is ~200MB (JRE + JAR only).                   ║
# ║    Final container is 60% smaller → faster pull, less attack   ║
# ║    surface, cheaper storage.                                   ║
# ╚════════════════════════════════════════════════════════════════╝

# ── STAGE 1: BUILD ────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy parent POM first — allows Docker to cache the Maven dependency
# download layer separately from source code changes.
# If only source changes (not POM), Docker reuses the cached layer.
COPY pom.xml .
COPY shopnest-payment-service/pom.xml shopnest-payment-service/

# Download all dependencies (cached layer — only re-runs if POM changes)
RUN mvn dependency:go-offline -pl shopnest-payment-service -am -q

# Copy source code (this layer re-runs on every source change)
COPY shopnest-payment-service/src shopnest-payment-service/src

# Build the JAR, skipping tests (tests run in CI pipeline separately)
RUN mvn package -pl shopnest-payment-service -am -DskipTests -q

# ── STAGE 2: RUNTIME ──────────────────────────────────────────────
# eclipse-temurin:21-jre-alpine = Java 21 JRE on Alpine Linux
# Alpine = tiny Linux distro (~5MB base) — ideal for containers
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user — never run production containers as root
RUN addgroup -S shopnest && adduser -S shopnest -G shopnest
USER shopnest

# Copy only the fat JAR from builder stage
COPY --from=builder /app/shopnest-payment-service/target/*.jar app.jar

# Expose the service port (documentation only — not a network rule)
EXPOSE 8084

# JVM tuning flags:
#   -XX:+UseContainerSupport  = respect Docker memory limits
#   -XX:MaxRAMPercentage=75.0 = use 75% of container memory for heap
#   -Djava.security.egd=...   = faster SecureRandom (fixes slow startup)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
