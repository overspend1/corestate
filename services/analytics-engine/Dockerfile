# CoreState Analytics Engine Dockerfile
FROM openjdk:11-jre-slim

# Install dependencies
RUN apt-get update && apt-get install -y \
    curl \
    procps \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy application JAR
COPY target/scala-2.12/analytics-engine-assembly-2.0.0.jar /app/analytics-engine.jar

# Copy configuration
COPY src/main/resources/application.conf /app/application.conf

# Set environment variables
ENV SPARK_MASTER=local[*]
ENV HTTP_PORT=8086
ENV PROMETHEUS_PORT=9095
ENV KAFKA_BOOTSTRAP_SERVERS=kafka-cluster:9092
ENV DATA_LAKE_BASE_PATH=s3a://corestate-data-lake
ENV LOG_LEVEL=INFO

# Expose ports
EXPOSE 8086 9095

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8086/health || exit 1

# Run application
ENTRYPOINT ["java", \
    "-Xmx4g", \
    "-Xms2g", \
    "-XX:+UseG1GC", \
    "-XX:+UseStringDeduplication", \
    "-Dconfig.file=/app/application.conf", \
    "-jar", \
    "/app/analytics-engine.jar"]
