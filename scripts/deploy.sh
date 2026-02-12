#!/bin/bash
set -e

# CoreState Deployment Script
# Automates deployment of all CoreState services

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE="${NAMESPACE:-corestate}"
ENVIRONMENT="${ENVIRONMENT:-production}"
DOCKER_REGISTRY="${DOCKER_REGISTRY:-ghcr.io/overspend1}"
VERSION="${VERSION:-latest}"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}CoreState v2.0 Deployment Script${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Environment: $ENVIRONMENT"
echo "Namespace: $NAMESPACE"
echo "Registry: $DOCKER_REGISTRY"
echo "Version: $VERSION"
echo ""

# Function to print step
print_step() {
    echo -e "${YELLOW}>>> $1${NC}"
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
print_step "Checking prerequisites..."

if ! command_exists kubectl; then
    echo -e "${RED}ERROR: kubectl is not installed${NC}"
    exit 1
fi

if ! command_exists docker; then
    echo -e "${RED}ERROR: docker is not installed${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Prerequisites check passed${NC}"

# Create namespace if it doesn't exist
print_step "Creating namespace..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
echo -e "${GREEN}✓ Namespace ready${NC}"

# Create secrets
print_step "Creating secrets..."
kubectl create secret generic postgres-credentials \
    --from-literal=username=corestate \
    --from-literal=password=$(openssl rand -base64 32) \
    --namespace=$NAMESPACE \
    --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic elasticsearch-credentials \
    --from-literal=username=elastic \
    --from-literal=password=$(openssl rand -base64 32) \
    --namespace=$NAMESPACE \
    --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic encryption-keys \
    --from-literal=master-key=$(openssl rand -base64 32) \
    --namespace=$NAMESPACE \
    --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic influxdb-credentials \
    --from-literal=token=$(openssl rand -base64 32) \
    --namespace=$NAMESPACE \
    --dry-run=client -o yaml | kubectl apply -f -

echo -e "${GREEN}✓ Secrets created${NC}"

# Deploy infrastructure services
print_step "Deploying infrastructure services..."
kubectl apply -f infrastructure/kubernetes/services/ -n $NAMESPACE
echo -e "${GREEN}✓ Infrastructure services deployed${NC}"

# Wait for infrastructure to be ready
print_step "Waiting for infrastructure services..."
kubectl wait --for=condition=ready pod -l component=database -n $NAMESPACE --timeout=300s || true
kubectl wait --for=condition=ready pod -l component=cache -n $NAMESPACE --timeout=300s || true
echo -e "${GREEN}✓ Infrastructure services ready${NC}"

# Deploy CoreState microservices
print_step "Deploying CoreState microservices..."

services=(
    "backup-engine"
    "storage-hal"
    "compression-engine"
    "ml-optimizer"
    "encryption-service"
    "sync-coordinator"
    "deduplication-service"
    "index-service"
    "analytics-engine"
)

for service in "${services[@]}"; do
    echo "Deploying $service..."
    kubectl apply -f infrastructure/kubernetes/deployments/${service}-deployment.yaml -n $NAMESPACE
done

echo -e "${GREEN}✓ Microservices deployed${NC}"

# Deploy ingress
print_step "Deploying ingress..."
kubectl apply -f infrastructure/kubernetes/ingress/ -n $NAMESPACE
echo -e "${GREEN}✓ Ingress deployed${NC}"

# Wait for all services to be ready
print_step "Waiting for services to be ready..."
for service in "${services[@]}"; do
    echo "Waiting for $service..."
    kubectl wait --for=condition=ready pod -l app=$service -n $NAMESPACE --timeout=300s || echo "Warning: $service may not be ready"
done

echo -e "${GREEN}✓ All services deployed${NC}"

# Display service status
print_step "Service Status:"
kubectl get pods -n $NAMESPACE

echo ""
print_step "Service URLs:"
kubectl get ingress -n $NAMESPACE

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Deployment completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "To monitor the deployment:"
echo "  kubectl get pods -n $NAMESPACE -w"
echo ""
echo "To view logs:"
echo "  kubectl logs -f deployment/<service-name> -n $NAMESPACE"
echo ""
echo "To access services:"
echo "  kubectl port-forward svc/<service-name> <local-port>:<service-port> -n $NAMESPACE"
