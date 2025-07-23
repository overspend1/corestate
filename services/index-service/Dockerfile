# Index Service Dockerfile
FROM openjdk:17-jdk-slim as builder

WORKDIR /app

# Install required tools
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy Gradle files
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle/ ./gradle/

# Copy source code
COPY src/ ./src/

# Build the application
RUN ./gradlew build -x test

# Runtime stage
FROM openjdk:17-jre-slim

RUN apt-get update && apt-get install -y \
    ca-certificates \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Create non-root user
RUN useradd -r -s /bin/false indexer && \
    chown -R indexer:indexer /app

USER indexer

EXPOSE 8086

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8086/actuator/health || exit 1

CMD ["java", "-jar", "app.jar"]