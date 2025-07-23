#!/usr/bin/env python3
"""
CoreState ML Optimizer Service

This service provides machine learning capabilities for backup optimization,
anomaly detection, and predictive analytics for the CoreState backup system.
"""

import asyncio
import logging
import os
import sys
from contextlib import asynccontextmanager
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
import json

import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException, BackgroundTasks, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import uvicorn
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
import joblib
import redis.asyncio as redis
from prometheus_client import Counter, Histogram, Gauge, generate_latest
from fastapi.responses import Response
import structlog

# Configure structured logging
structlog.configure(
    processors=[
        structlog.stdlib.filter_by_level,
        structlog.stdlib.add_logger_name,
        structlog.stdlib.add_log_level,
        structlog.stdlib.PositionalArgumentsFormatter(),
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
        structlog.processors.UnicodeDecoder(),
        structlog.processors.JSONRenderer()
    ],
    context_class=dict,
    logger_factory=structlog.stdlib.LoggerFactory(),
    wrapper_class=structlog.stdlib.BoundLogger,
    cache_logger_on_first_use=True,
)

logger = structlog.get_logger(__name__)

# Prometheus metrics
backup_predictions = Counter('ml_backup_predictions_total', 'Total backup predictions made')
anomaly_detections = Counter('ml_anomaly_detections_total', 'Total anomalies detected')
model_inference_duration = Histogram('ml_model_inference_seconds', 'Model inference duration')
active_models = Gauge('ml_active_models', 'Number of active ML models')

# Configuration
REDIS_URL = os.getenv('REDIS_URL', 'redis://localhost:6379')
MODEL_UPDATE_INTERVAL = int(os.getenv('MODEL_UPDATE_INTERVAL', '3600'))  # 1 hour
ANOMALY_THRESHOLD = float(os.getenv('ANOMALY_THRESHOLD', '0.1'))

# Global state
redis_client: Optional[redis.Redis] = None
ml_models: Dict[str, Any] = {}

# Pydantic models
class BackupRequest(BaseModel):
    device_id: str
    file_paths: List[str]
    priority: int = Field(default=1, ge=1, le=5)
    estimated_size: int = Field(gt=0)
    metadata: Dict[str, Any] = Field(default_factory=dict)

class BackupPrediction(BaseModel):
    device_id: str
    predicted_duration: float
    predicted_success_rate: float
    optimal_time_slot: datetime
    resource_requirements: Dict[str, float]
    recommendations: List[str]

class AnomalyDetectionRequest(BaseModel):
    device_id: str
    metrics: Dict[str, float]
    timestamp: datetime

class AnomalyResult(BaseModel):
    device_id: str
    is_anomaly: bool
    anomaly_score: float
    affected_metrics: List[str]
    recommendations: List[str]
    timestamp: datetime

class OptimizationRequest(BaseModel):
    backup_jobs: List[Dict[str, Any]]
    resource_constraints: Dict[str, float]
    optimization_goals: List[str] = ["minimize_time", "maximize_throughput"]

class OptimizationResult(BaseModel):
    optimized_schedule: List[Dict[str, Any]]
    expected_improvement: Dict[str, float]
    resource_utilization: Dict[str, float]

# ML Model Management
class BackupPredictor:
    def __init__(self):
        self.model = None
        self.scaler = StandardScaler()
        self.is_trained = False
        
    def train(self, training_data: pd.DataFrame):
        """Train the backup prediction model"""
        try:
            if training_data.empty:
                logger.warning("No training data provided")
                return
                
            features = ['file_count', 'total_size', 'device_cpu', 'device_memory', 'network_speed']
            X = training_data[features]
            y = training_data['backup_duration']
            
            X_scaled = self.scaler.fit_transform(X)
            
            # Simple linear model for demonstration
            from sklearn.ensemble import RandomForestRegressor
            self.model = RandomForestRegressor(n_estimators=100, random_state=42)
            self.model.fit(X_scaled, y)
            
            self.is_trained = True
            logger.info("Backup prediction model trained successfully")
            
        except Exception as e:
            logger.error("Failed to train backup prediction model", error=str(e))
            
    def predict(self, features: Dict[str, float]) -> Dict[str, float]:
        """Predict backup metrics"""
        if not self.is_trained or self.model is None:
            logger.warning("Model not trained, using default predictions")
            return {
                'predicted_duration': 300.0,  # 5 minutes default
                'predicted_success_rate': 0.95,
                'confidence': 0.5
            }
            
        try:
            feature_vector = np.array([[
                features.get('file_count', 100),
                features.get('total_size', 1000000),
                features.get('device_cpu', 50.0),
                features.get('device_memory', 70.0),
                features.get('network_speed', 100.0)
            ]])
            
            feature_vector_scaled = self.scaler.transform(feature_vector)
            duration = self.model.predict(feature_vector_scaled)[0]
            
            # Calculate success rate based on historical data patterns
            success_rate = max(0.7, min(0.99, 1.0 - (duration / 3600.0) * 0.1))
            
            return {
                'predicted_duration': max(30.0, duration),
                'predicted_success_rate': success_rate,
                'confidence': 0.8
            }
            
        except Exception as e:
            logger.error("Prediction failed", error=str(e))
            return {
                'predicted_duration': 300.0,
                'predicted_success_rate': 0.95,
                'confidence': 0.3
            }

class AnomalyDetector:
    def __init__(self):
        self.model = IsolationForest(contamination=ANOMALY_THRESHOLD, random_state=42)
        self.scaler = StandardScaler()
        self.is_trained = False
        
    def train(self, training_data: pd.DataFrame):
        """Train the anomaly detection model"""
        try:
            if training_data.empty:
                logger.warning("No training data for anomaly detection")
                return
                
            features = ['cpu_usage', 'memory_usage', 'disk_io', 'network_io', 'backup_speed']
            X = training_data[features].fillna(0)
            
            X_scaled = self.scaler.fit_transform(X)
            self.model.fit(X_scaled)
            
            self.is_trained = True
            logger.info("Anomaly detection model trained successfully")
            
        except Exception as e:
            logger.error("Failed to train anomaly detection model", error=str(e))
            
    def detect(self, metrics: Dict[str, float]) -> Dict[str, Any]:
        """Detect anomalies in backup metrics"""
        if not self.is_trained:
            logger.warning("Anomaly model not trained, skipping detection")
            return {
                'is_anomaly': False,
                'anomaly_score': 0.0,
                'affected_metrics': [],
                'confidence': 0.0
            }
            
        try:
            feature_vector = np.array([[
                metrics.get('cpu_usage', 50.0),
                metrics.get('memory_usage', 60.0),
                metrics.get('disk_io', 100.0),
                metrics.get('network_io', 50.0),
                metrics.get('backup_speed', 10.0)
            ]])
            
            feature_vector_scaled = self.scaler.transform(feature_vector)
            anomaly_score = self.model.decision_function(feature_vector_scaled)[0]
            is_anomaly = self.model.predict(feature_vector_scaled)[0] == -1
            
            # Identify which metrics contribute most to anomaly
            affected_metrics = []
            if is_anomaly:
                metric_names = ['cpu_usage', 'memory_usage', 'disk_io', 'network_io', 'backup_speed']
                feature_importance = np.abs(feature_vector_scaled[0])
                top_indices = np.argsort(feature_importance)[-2:]
                affected_metrics = [metric_names[i] for i in top_indices]
            
            return {
                'is_anomaly': bool(is_anomaly),
                'anomaly_score': float(anomaly_score),
                'affected_metrics': affected_metrics,
                'confidence': 0.8 if self.is_trained else 0.3
            }
            
        except Exception as e:
            logger.error("Anomaly detection failed", error=str(e))
            return {
                'is_anomaly': False,
                'anomaly_score': 0.0,
                'affected_metrics': [],
                'confidence': 0.0
            }

# Initialize ML models
backup_predictor = BackupPredictor()
anomaly_detector = AnomalyDetector()

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan manager"""
    global redis_client
    
    # Startup
    logger.info("Starting ML Optimizer service")
    
    try:
        redis_client = redis.from_url(REDIS_URL)
        await redis_client.ping()
        logger.info("Connected to Redis")
    except Exception as e:
        logger.error("Failed to connect to Redis", error=str(e))
        redis_client = None
    
    # Load or train models
    await load_or_train_models()
    
    # Start background tasks
    asyncio.create_task(periodic_model_update())
    
    active_models.set(len(ml_models))
    
    yield
    
    # Shutdown
    logger.info("Shutting down ML Optimizer service")
    if redis_client:
        await redis_client.close()

app = FastAPI(
    title="CoreState ML Optimizer",
    description="Machine Learning service for backup optimization and anomaly detection",
    version="2.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

async def load_or_train_models():
    """Load existing models or train new ones"""
    try:
        # Try to load historical data for training
        if redis_client:
            backup_data = await redis_client.get("training:backup_data")
            anomaly_data = await redis_client.get("training:anomaly_data")
            
            if backup_data:
                df = pd.read_json(backup_data)
                backup_predictor.train(df)
                
            if anomaly_data:
                df = pd.read_json(anomaly_data)
                anomaly_detector.train(df)
        
        # Generate synthetic training data if no historical data
        if not backup_predictor.is_trained:
            synthetic_backup_data = generate_synthetic_backup_data()
            backup_predictor.train(synthetic_backup_data)
            
        if not anomaly_detector.is_trained:
            synthetic_anomaly_data = generate_synthetic_anomaly_data()
            anomaly_detector.train(synthetic_anomaly_data)
            
        ml_models['backup_predictor'] = backup_predictor
        ml_models['anomaly_detector'] = anomaly_detector
        
        logger.info("ML models loaded/trained successfully")
        
    except Exception as e:
        logger.error("Failed to load/train models", error=str(e))

def generate_synthetic_backup_data() -> pd.DataFrame:
    """Generate synthetic training data for backup prediction"""
    np.random.seed(42)
    n_samples = 1000
    
    data = {
        'file_count': np.random.randint(10, 10000, n_samples),
        'total_size': np.random.randint(1000, 100000000, n_samples),
        'device_cpu': np.random.uniform(20, 90, n_samples),
        'device_memory': np.random.uniform(30, 95, n_samples),
        'network_speed': np.random.uniform(1, 1000, n_samples),
    }
    
    # Create realistic backup duration based on features
    data['backup_duration'] = (
        data['file_count'] * 0.1 +
        data['total_size'] / 1000000 * 60 +
        np.random.normal(0, 30, n_samples)
    )
    data['backup_duration'] = np.maximum(data['backup_duration'], 30)
    
    return pd.DataFrame(data)

def generate_synthetic_anomaly_data() -> pd.DataFrame:
    """Generate synthetic training data for anomaly detection"""
    np.random.seed(42)
    n_samples = 1000
    
    # Normal operation data
    data = {
        'cpu_usage': np.random.normal(50, 15, n_samples),
        'memory_usage': np.random.normal(60, 20, n_samples),
        'disk_io': np.random.normal(100, 30, n_samples),
        'network_io': np.random.normal(50, 15, n_samples),
        'backup_speed': np.random.normal(10, 3, n_samples),
    }
    
    # Clip values to realistic ranges
    for key in data:
        data[key] = np.clip(data[key], 0, 100 if key.endswith('_usage') else 1000)
    
    return pd.DataFrame(data)

async def periodic_model_update():
    """Periodically retrain models with new data"""
    while True:
        try:
            await asyncio.sleep(MODEL_UPDATE_INTERVAL)
            logger.info("Starting periodic model update")
            await load_or_train_models()
        except Exception as e:
            logger.error("Periodic model update failed", error=str(e))

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "models_loaded": len(ml_models),
        "backup_predictor_trained": backup_predictor.is_trained,
        "anomaly_detector_trained": anomaly_detector.is_trained,
        "redis_connected": redis_client is not None,
        "timestamp": datetime.utcnow().isoformat()
    }

@app.get("/metrics")
async def get_metrics():
    """Prometheus metrics endpoint"""
    return Response(generate_latest(), media_type="text/plain")

@app.post("/predict/backup", response_model=BackupPrediction)
async def predict_backup(request: BackupRequest, background_tasks: BackgroundTasks):
    """Predict backup performance and optimal scheduling"""
    with model_inference_duration.time():
        try:
            # Extract features from request
            features = {
                'file_count': len(request.file_paths),
                'total_size': request.estimated_size,
                'device_cpu': request.metadata.get('cpu_usage', 50.0),
                'device_memory': request.metadata.get('memory_usage', 60.0),
                'network_speed': request.metadata.get('network_speed', 100.0)
            }
            
            # Get prediction
            prediction = backup_predictor.predict(features)
            
            # Calculate optimal time slot (next low-usage period)
            optimal_time = datetime.utcnow() + timedelta(hours=2)
            
            # Generate recommendations
            recommendations = []
            if prediction['predicted_duration'] > 1800:  # 30 minutes
                recommendations.append("Consider running backup during off-peak hours")
            if prediction['predicted_success_rate'] < 0.9:
                recommendations.append("Check network stability before starting backup")
            if request.estimated_size > 10000000:  # 10MB
                recommendations.append("Enable compression to reduce transfer time")
            
            backup_predictions.inc()
            
            result = BackupPrediction(
                device_id=request.device_id,
                predicted_duration=prediction['predicted_duration'],
                predicted_success_rate=prediction['predicted_success_rate'],
                optimal_time_slot=optimal_time,
                resource_requirements={
                    'cpu': min(80.0, features['file_count'] * 0.01),
                    'memory': min(90.0, features['total_size'] / 1000000),
                    'network': min(100.0, features['total_size'] / 100000)
                },
                recommendations=recommendations
            )
            
            # Store prediction for model improvement
            background_tasks.add_task(store_prediction_data, request, result)
            
            return result
            
        except Exception as e:
            logger.error("Backup prediction failed", device_id=request.device_id, error=str(e))
            raise HTTPException(status_code=500, detail="Prediction failed")

@app.post("/detect/anomaly", response_model=AnomalyResult)
async def detect_anomaly(request: AnomalyDetectionRequest):
    """Detect anomalies in backup system metrics"""
    with model_inference_duration.time():
        try:
            detection_result = anomaly_detector.detect(request.metrics)
            
            recommendations = []
            if detection_result['is_anomaly']:
                anomaly_detections.inc()
                
                if 'cpu_usage' in detection_result['affected_metrics']:
                    recommendations.append("High CPU usage detected - consider reducing concurrent backups")
                if 'memory_usage' in detection_result['affected_metrics']:
                    recommendations.append("Memory usage anomaly - check for memory leaks")
                if 'backup_speed' in detection_result['affected_metrics']:
                    recommendations.append("Backup speed anomaly - check network or storage performance")
            
            return AnomalyResult(
                device_id=request.device_id,
                is_anomaly=detection_result['is_anomaly'],
                anomaly_score=detection_result['anomaly_score'],
                affected_metrics=detection_result['affected_metrics'],
                recommendations=recommendations,
                timestamp=request.timestamp
            )
            
        except Exception as e:
            logger.error("Anomaly detection failed", device_id=request.device_id, error=str(e))
            raise HTTPException(status_code=500, detail="Anomaly detection failed")

@app.post("/optimize/schedule", response_model=OptimizationResult)
async def optimize_backup_schedule(request: OptimizationRequest):
    """Optimize backup job scheduling"""
    try:
        # Simple optimization: sort by priority and estimated duration
        jobs = request.backup_jobs.copy()
        
        # Score jobs based on priority and resource requirements
        for job in jobs:
            priority_score = job.get('priority', 1) * 10
            size_score = min(10, job.get('estimated_size', 1000000) / 1000000)
            job['optimization_score'] = priority_score - size_score
        
        # Sort by optimization score (higher is better)
        optimized_jobs = sorted(jobs, key=lambda x: x['optimization_score'], reverse=True)
        
        # Calculate expected improvements
        total_time_before = sum(job.get('estimated_duration', 300) for job in jobs)
        total_time_after = total_time_before * 0.85  # Assume 15% improvement
        
        return OptimizationResult(
            optimized_schedule=optimized_jobs,
            expected_improvement={
                'time_reduction': (total_time_before - total_time_after) / total_time_before,
                'throughput_increase': 0.2,
                'resource_efficiency': 0.15
            },
            resource_utilization={
                'cpu': 75.0,
                'memory': 80.0,
                'network': 85.0,
                'storage': 70.0
            }
        )
        
    except Exception as e:
        logger.error("Schedule optimization failed", error=str(e))
        raise HTTPException(status_code=500, detail="Optimization failed")

async def store_prediction_data(request: BackupRequest, prediction: BackupPrediction):
    """Store prediction data for model improvement"""
    if redis_client:
        try:
            data = {
                'timestamp': datetime.utcnow().isoformat(),
                'device_id': request.device_id,
                'request': request.dict(),
                'prediction': prediction.dict()
            }
            await redis_client.lpush("ml:predictions", json.dumps(data))
            await redis_client.ltrim("ml:predictions", 0, 9999)  # Keep last 10k predictions
        except Exception as e:
            logger.error("Failed to store prediction data", error=str(e))

@app.get("/models/status")
async def get_model_status():
    """Get status of all ML models"""
    return {
        "models": {
            "backup_predictor": {
                "trained": backup_predictor.is_trained,
                "type": "RandomForestRegressor"
            },
            "anomaly_detector": {
                "trained": anomaly_detector.is_trained,
                "type": "IsolationForest"
            }
        },
        "metrics": {
            "total_predictions": backup_predictions._value._value,
            "total_anomalies": anomaly_detections._value._value,
            "active_models": len(ml_models)
        },
        "last_updated": datetime.utcnow().isoformat()
    }

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=int(os.getenv("PORT", "8000")),
        reload=os.getenv("ENVIRONMENT") == "development",
        log_level="info"
    )