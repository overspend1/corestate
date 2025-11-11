# Build stage
FROM rust:1.75-slim as builder

WORKDIR /usr/src/storage-hal

# Install build dependencies
RUN apt-get update && apt-get install -y \
    pkg-config \
    libssl-dev \
    protobuf-compiler \
    && rm -rf /var/lib/apt/lists/*

# Copy manifests
COPY Cargo.toml Cargo.lock* ./
COPY build.rs ./

# Copy source code
COPY src ./src

# Build for release
RUN cargo build --release

# Runtime stage
FROM debian:bookworm-slim

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    ca-certificates \
    libssl3 \
    && rm -rf /var/lib/apt/lists/*

# Copy the binary from builder
COPY --from=builder /usr/src/storage-hal/target/release/storage-hal /usr/local/bin/storage-hal

# Create non-root user
RUN useradd -m -u 1000 appuser && \
    mkdir -p /var/lib/storage-hal /var/log/storage-hal && \
    chown -R appuser:appuser /var/lib/storage-hal /var/log/storage-hal

USER appuser

# Expose ports
EXPOSE 50051 9090

# Set environment variables
ENV RUST_LOG=info
ENV SERVICE_PORT=50051
ENV METRICS_PORT=9090

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD [ "/usr/local/bin/storage-hal", "--health-check" ] || exit 1

# Run the binary
CMD ["/usr/local/bin/storage-hal"]
