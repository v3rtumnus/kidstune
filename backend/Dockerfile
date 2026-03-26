# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM gradle:8.12.1-jdk21 AS builder
WORKDIR /app

# Cache dependencies first (layer invalidated only when build files change)
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon --configuration runtimeClasspath 2>/dev/null || true

# Build the application
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

# Non-root user for security
RUN groupadd --system kidstune && useradd --system --gid kidstune kidstune
USER kidstune

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]
