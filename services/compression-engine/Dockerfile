# Compression Engine Dockerfile
FROM rust:1.74-slim as builder

WORKDIR /app

# Install build dependencies
RUN apt-get update && apt-get install -y \
    pkg-config \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy Cargo files
COPY Cargo.toml Cargo.lock ./
COPY src/ ./src/
COPY build.rs ./

# Build the application
RUN cargo build --release

# Runtime stage
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the binary from builder stage
COPY --from=builder /app/target/release/compression-engine /app/compression-engine

# Create non-root user
RUN useradd -r -s /bin/false compression && \
    chown -R compression:compression /app

USER compression

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD /app/compression-engine --health-check

CMD ["/app/compression-engine"]