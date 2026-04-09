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

# Install curl for healthcheck
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy built artifact (appmaster-backend.jar from ktor.fatJar archiveFileName)
COPY --from=build /app/build/libs/appmaster-backend.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Health check endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Expose application port
EXPOSE 8080

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
