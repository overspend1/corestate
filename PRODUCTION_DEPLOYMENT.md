# CoreState v2.0 - Production Deployment Guide

This guide covers complete production deployment of CoreState v2.0 enterprise backup system.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Infrastructure Setup](#infrastructure-setup)
3. [Service Deployment](#service-deployment)
4. [Monitoring Setup](#monitoring-setup)
5. [Security Configuration](#security-configuration)
6. [Backup and Recovery](#backup-and-recovery)
7. [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Tools

- **Kubernetes**: v1.24+ cluster
- **kubectl**: v1.24+
- **Docker**: v20.10+
- **Helm**: v3.10+ (optional)
- **Terraform**: v1.5+ (for infrastructure provisioning)

### Minimum Cluster Requirements

- **Nodes**: 5+ worker nodes
- **CPU**: 32 cores total (recommended: 64 cores)
- **Memory**: 128 GB RAM total (recommended: 256 GB)
- **Storage**: 1 TB+ persistent storage
- **Network**: 10 Gbps internal networking

## Infrastructure Setup

### Option 1: Terraform (AWS EKS)

```bash
cd infrastructure/terraform

# Initialize Terraform
terraform init

# Review planned changes
terraform plan -var="environment=production"

# Apply infrastructure
terraform apply -var="environment=production"

# Get cluster credentials
aws eks update-kubeconfig --name corestate-eks-cluster --region us-east-1
```

### Option 2: Manual Kubernetes Cluster

```bash
# Create namespace
kubectl create namespace corestate

# Apply resource quotas
kubectl apply -f infrastructure/kubernetes/quotas/
```

## Service Deployment

### Step 1: Create Secrets

```bash
# Generate secure secrets
export POSTGRES_PASSWORD=$(openssl rand -base64 32)
export MASTER_KEY=$(openssl rand -base64 32)
export INFLUXDB_TOKEN=$(openssl rand -base64 32)
export ELASTICSEARCH_PASSWORD=$(openssl rand -base64 32)

# Create Kubernetes secrets
kubectl create secret generic postgres-credentials \
  --from-literal=username=corestate \
  --from-literal=password=$POSTGRES_PASSWORD \
  --namespace=corestate

kubectl create secret generic encryption-keys \
  --from-literal=master-key=$MASTER_KEY \
  --namespace=corestate

kubectl create secret generic influxdb-credentials \
  --from-literal=token=$INFLUXDB_TOKEN \
  --namespace=corestate

kubectl create secret generic elasticsearch-credentials \
  --from-literal=username=elastic \
  --from-literal=password=$ELASTICSEARCH_PASSWORD \
  --namespace=corestate

# Save secrets securely
echo "POSTGRES_PASSWORD=$POSTGRES_PASSWORD" >> .env.production
echo "MASTER_KEY=$MASTER_KEY" >> .env.production
echo "INFLUXDB_TOKEN=$INFLUXDB_TOKEN" >> .env.production
echo "ELASTICSEARCH_PASSWORD=$ELASTICSEARCH_PASSWORD" >> .env.production

# Secure the file
chmod 600 .env.production
```

### Step 2: Deploy Infrastructure Services

```bash
# Deploy PostgreSQL
kubectl apply -f infrastructure/kubernetes/services/postgres-statefulset.yaml

# Deploy Redis
kubectl apply -f infrastructure/kubernetes/services/redis-statefulset.yaml

# Deploy Elasticsearch
kubectl apply -f infrastructure/kubernetes/services/elasticsearch-statefulset.yaml

# Deploy Kafka + Zookeeper
kubectl apply -f infrastructure/kubernetes/services/kafka-cluster.yaml

# Deploy InfluxDB
kubectl apply -f infrastructure/kubernetes/services/influxdb-statefulset.yaml

# Wait for all infrastructure to be ready
kubectl wait --for=condition=ready pod -l component=database -n corestate --timeout=600s
kubectl wait --for=condition=ready pod -l component=cache -n corestate --timeout=600s
kubectl wait --for=condition=ready pod -l component=search -n corestate --timeout=600s
```

### Step 3: Build and Push Docker Images

```bash
# Set registry
export DOCKER_REGISTRY=ghcr.io/overspend1
export VERSION=$(git describe --tags --always)

# Login to registry
echo $GITHUB_TOKEN | docker login ghcr.io -u $GITHUB_USERNAME --password-stdin

# Build all images
PUSH_IMAGES=true VERSION=$VERSION ./scripts/build.sh
```

### Step 4: Deploy CoreState Microservices

```bash
# Deploy all services
./scripts/deploy.sh

# Or deploy individually
kubectl apply -f infrastructure/kubernetes/deployments/backup-engine-deployment.yaml
kubectl apply -f infrastructure/kubernetes/deployments/storage-hal-deployment.yaml
kubectl apply -f infrastructure/kubernetes/deployments/compression-engine-deployment.yaml
kubectl apply -f infrastructure/kubernetes/deployments/ml-optimizer-deployment.yaml
kubectl apply -f infrastructure/kubernetes/deployments/encryption-service-deployment.yaml
kubectl apply -f infrastructure/kubernetes/deployments/sync-coordinator-deployment.yaml
kubectl apply -f infrastructure/kubernetes/deployments/deduplication-service-deployment.yaml
kubectl apply -f infrastructure/kubernetes/deployments/index-service-deployment.yaml
kubectl apply -f infrastructure/kubernetes/deployments/analytics-engine-deployment.yaml

# Wait for all services
kubectl wait --for=condition=ready pod -l app=backup-engine -n corestate --timeout=600s
```

### Step 5: Deploy Ingress

```bash
# Install NGINX Ingress Controller (if not already installed)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/cloud/deploy.yaml

# Deploy CoreState ingress
kubectl apply -f infrastructure/kubernetes/ingress/main-ingress.yaml

# Get external IP
kubectl get ingress -n corestate
```

## Monitoring Setup

### Deploy Prometheus and Grafana

```bash
# Add Helm repositories
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

# Install Prometheus
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace corestate \
  --values infrastructure/monitoring/prometheus-values.yaml

# Access Grafana
kubectl port-forward svc/prometheus-grafana 3000:80 -n corestate

# Default credentials: admin / prom-operator
```

### Configure Alerts

```bash
# Apply alert rules
kubectl apply -f infrastructure/monitoring/alerts.yml
```

## Security Configuration

### Enable Pod Security Policies

```bash
kubectl apply -f infrastructure/security/pod-security-policy.yaml
```

### Configure Network Policies

```bash
kubectl apply -f infrastructure/security/network-policies.yaml
```

### Enable TLS

```bash
# Install cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml

# Create ClusterIssuer for Let's Encrypt
kubectl apply -f infrastructure/security/cert-issuer.yaml

# Update ingress to use TLS
kubectl apply -f infrastructure/kubernetes/ingress/tls-ingress.yaml
```

## Backup and Recovery

### Database Backups

```bash
# PostgreSQL backup
kubectl exec -it postgres-0 -n corestate -- pg_dump -U corestate corestate > backup-$(date +%Y%m%d).sql

# Restore
kubectl exec -i postgres-0 -n corestate -- psql -U corestate < backup-20240101.sql
```

### Elasticsearch Snapshots

```bash
# Configure snapshot repository
curl -X PUT "elasticsearch:9200/_snapshot/backup" -H 'Content-Type: application/json' -d'
{
  "type": "s3",
  "settings": {
    "bucket": "corestate-backups",
    "region": "us-east-1"
  }
}'

# Create snapshot
curl -X PUT "elasticsearch:9200/_snapshot/backup/snapshot_1?wait_for_completion=true"
```

## Verification

### Health Checks

```bash
# Check all services
kubectl get pods -n corestate

# Test backup engine
curl http://backup-engine.corestate.svc.cluster.local:8080/actuator/health

# Test all services
for svc in backup-engine storage-hal compression-engine ml-optimizer encryption-service sync-coordinator deduplication-service index-service analytics-engine; do
  echo "Testing $svc..."
  kubectl run test-$svc --rm -it --restart=Never --image=curlimages/curl -- curl -s http://$svc.corestate.svc.cluster.local/health
done
```

### Integration Tests

```bash
# Run integration tests
kubectl apply -f tests/kubernetes/integration-test-job.yaml

# Check results
kubectl logs -f job/integration-tests -n corestate
```

## Scaling

### Horizontal Pod Autoscaling

```bash
# Enable metrics server (if not installed)
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Apply HPA for services
kubectl apply -f infrastructure/kubernetes/autoscaling/
```

### Manual Scaling

```bash
# Scale specific service
kubectl scale deployment backup-engine --replicas=5 -n corestate

# Scale all services
for svc in backup-engine storage-hal compression-engine; do
  kubectl scale deployment $svc --replicas=3 -n corestate
done
```

## Troubleshooting

### Common Issues

**1. Service not starting**
```bash
kubectl describe pod <pod-name> -n corestate
kubectl logs <pod-name> -n corestate --previous
```

**2. Network connectivity issues**
```bash
kubectl exec -it <pod-name> -n corestate -- nslookup <service-name>
kubectl get networkpolicies -n corestate
```

**3. Resource exhaustion**
```bash
kubectl top nodes
kubectl top pods -n corestate
```

**4. Database connection errors**
```bash
kubectl exec -it postgres-0 -n corestate -- psql -U corestate -c "SELECT 1"
```

### Support

- GitHub Issues: https://github.com/overspend1/corestate/issues
- Documentation: https://docs.corestate.io
- Community Forum: https://community.corestate.io

## Maintenance

### Regular Tasks

1. **Daily**: Check service health and metrics
2. **Weekly**: Review logs and alerts
3. **Monthly**: Update dependencies and security patches
4. **Quarterly**: Disaster recovery drill

### Updates

```bash
# Update to new version
export NEW_VERSION=2.1.0

# Build and push new images
VERSION=$NEW_VERSION PUSH_IMAGES=true ./scripts/build.sh

# Rolling update
kubectl set image deployment/backup-engine backup-engine=ghcr.io/overspend1/corestate-backup-engine:$NEW_VERSION -n corestate

# Monitor rollout
kubectl rollout status deployment/backup-engine -n corestate

# Rollback if needed
kubectl rollout undo deployment/backup-engine -n corestate
```

---

**CoreState v2.0** - Enterprise Backup System  
© 2024 Wiktor (overspend1) - MIT License
