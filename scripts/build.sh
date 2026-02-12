#!/bin/bash
set -e

# CoreState Build Script
# Builds all CoreState services and creates Docker images

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
DOCKER_REGISTRY="${DOCKER_REGISTRY:-ghcr.io/overspend1}"
VERSION="${VERSION:-$(git describe --tags --always --dirty)}"
PUSH_IMAGES="${PUSH_IMAGES:-false}"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}CoreState v2.0 Build Script${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Registry: $DOCKER_REGISTRY"
echo "Version: $VERSION"
echo "Push Images: $PUSH_IMAGES"
echo ""

# Function to print step
print_step() {
    echo -e "${YELLOW}>>> $1${NC}"
}

# Function to build and tag Docker image
build_image() {
    local service=$1
    local context=$2
    local dockerfile=$3
    
    print_step "Building $service..."
    
    docker build \
        -t $DOCKER_REGISTRY/corestate-$service:$VERSION \
        -t $DOCKER_REGISTRY/corestate-$service:latest \
        -f $dockerfile \
        $context
    
    echo -e "${GREEN}✓ Built $service${NC}"
    
    if [ "$PUSH_IMAGES" = "true" ]; then
        print_step "Pushing $service..."
        docker push $DOCKER_REGISTRY/corestate-$service:$VERSION
        docker push $DOCKER_REGISTRY/corestate-$service:latest
        echo -e "${GREEN}✓ Pushed $service${NC}"
    fi
}

# Build Kotlin/JVM services
print_step "Building Kotlin services..."
./gradlew clean build -x test

build_image "backup-engine" "./services/backup-engine" "./services/backup-engine/Dockerfile"
build_image "index-service" "./services/index-service" "./services/index-service/Dockerfile"

# Build Scala services
print_step "Building Scala services..."
cd services/analytics-engine
sbt clean assembly
cd ../..

build_image "analytics-engine" "./services/analytics-engine" "./services/analytics-engine/Dockerfile"

# Build Rust services
print_step "Building Rust services..."
build_image "storage-hal" "./services/storage-hal" "./services/storage-hal/Dockerfile"
build_image "compression-engine" "./services/compression-engine" "./services/compression-engine/Dockerfile"
build_image "daemon" "./apps/daemon" "./apps/daemon/Dockerfile"

# Build Python services
print_step "Building Python services..."
build_image "ml-optimizer" "./services/ml-optimizer" "./services/ml-optimizer/Dockerfile"
build_image "deduplication-service" "./services/deduplication-service" "./services/deduplication-service/Dockerfile"

# Build Node.js services
print_step "Building Node.js services..."
build_image "encryption-service" "./services/encryption-service" "./services/encryption-service/Dockerfile"
build_image "sync-coordinator" "./services/sync-coordinator" "./services/sync-coordinator/Dockerfile"

# Build Android app
print_step "Building Android app..."
./gradlew :apps:android:androidApp:assembleRelease

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Built images:"
docker images | grep corestate | head -10
