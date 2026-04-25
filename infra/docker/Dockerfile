# =============================================================================
# Kotlin/Ktor Backend Dockerfile
# Multi-stage build for Kotlin/Ktor application
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build
# -----------------------------------------------------------------------------
FROM gradle:8.11-jdk21 AS build

WORKDIR /app

# Copy dependency files first for better caching
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Download dependencies (cached if no changes to build files)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build fat JAR — uses Ktor's buildFatJar task configured in build.gradle.kts
# (`ktor { fatJar { archiveFileName = "appmaster-backend.jar" } }`).
RUN gradle buildFatJar --no-daemon

# -----------------------------------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# bash is required by entrypoint.sh (`wait -n` is a bash builtin not in
# BusyBox ash on older Alpine releases). curl handles the local health probe.
RUN apk add --no-cache bash curl ca-certificates

# cloudflared establishes the Cloudflare Tunnel that fronts this app.
ARG CLOUDFLARED_VERSION=2024.10.0
RUN curl -fsSL "https://github.com/cloudflare/cloudflared/releases/download/${CLOUDFLARED_VERSION}/cloudflared-linux-amd64" \
        -o /usr/local/bin/cloudflared \
    && chmod +x /usr/local/bin/cloudflared

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy built artifact (appmaster-backend.jar from ktor.fatJar archiveFileName)
COPY --from=build /app/build/libs/appmaster-backend.jar app.jar
COPY infra/docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Local probe only — Fly's TCP check (fly.toml) is what actually gates traffic.
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=40.0"

ENTRYPOINT ["/app/entrypoint.sh"]
