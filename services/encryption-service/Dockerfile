# Encryption Service Dockerfile  
FROM node:18-alpine as builder

WORKDIR /app

# Copy package files
COPY package*.json ./
RUN npm ci --only=production

# Runtime stage
FROM node:18-alpine

RUN apk add --no-cache \
    ca-certificates \
    tini

WORKDIR /app

# Copy node_modules from builder
COPY --from=builder /app/node_modules ./node_modules

# Copy application code
COPY . .

# Create non-root user
RUN addgroup -g 1001 -S nodejs && \
    adduser -S encryption -u 1001 -G nodejs && \
    chown -R encryption:nodejs /app

USER encryption

EXPOSE 8085

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD node healthcheck.js

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "dist/index.js"]