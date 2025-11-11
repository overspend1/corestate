# Build stage
FROM python:3.11-slim as builder

WORKDIR /app

# Install build dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir --user -r requirements.txt

# Runtime stage
FROM python:3.11-slim

WORKDIR /app

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Copy Python dependencies from builder
COPY --from=builder /root/.local /root/.local

# Copy application code
COPY main.py .
COPY models ./models
COPY training ./training

# Create non-root user
RUN useradd -m -u 1000 appuser && \
    mkdir -p /var/lib/ml-optimizer /var/log/ml-optimizer && \
    chown -R appuser:appuser /app /var/lib/ml-optimizer /var/log/ml-optimizer

USER appuser

# Make sure scripts in .local are usable
ENV PATH=/root/.local/bin:$PATH
ENV PYTHONUNBUFFERED=1

# Expose ports
EXPOSE 8000 9090

# Set environment variables
ENV SERVICE_PORT=8000
ENV METRICS_PORT=9090
ENV MODEL_PATH=/var/lib/ml-optimizer/models

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD python -c "import requests; requests.get('http://localhost:8000/health')" || exit 1

# Run the application
CMD ["python", "main.py"]
