#!/usr/bin/env python3
"""
Test suite for CoreState ML Optimizer Service
"""

import pytest
import asyncio
import json
from datetime import datetime
from fastapi.testclient import TestClient
from unittest.mock import Mock, patch
import pandas as pd
import numpy as np

from main import (
    app, BackupPredictor, AnomalyDetector, 
    BackupRequest, AnomalyDetectionRequest,
    generate_synthetic_backup_data, generate_synthetic_anomaly_data
)

client = TestClient(app)

class TestMLOptimizer:
    """Test the main ML Optimizer endpoints"""
    
    def test_health_check(self):
        """Test health check endpoint"""
        response = client.get("/health")
        assert response.status_code == 200
        
        data = response.json()
        assert "status" in data
        assert "models_loaded" in data
        assert "timestamp" in data
        
    def test_metrics_endpoint(self):
        """Test metrics endpoint"""
        response = client.get("/metrics")
        assert response.status_code == 200
        # Should return Prometheus metrics format
        assert "text/plain" in response.headers["content-type"]
        
    def test_model_status(self):
        """Test model status endpoint"""
        response = client.get("/models/status")
        assert response.status_code == 200
        
        data = response.json()
        assert "models" in data
        assert "metrics" in data
        assert "last_updated" in data
        
    def test_backup_prediction(self):
        """Test backup prediction endpoint"""
        request_data = {
            "device_id": "test-device-123",
            "file_paths": ["/path/to/file1.txt", "/path/to/file2.txt"],
            "priority": 3,
            "estimated_size": 1000000,
            "metadata": {
                "cpu_usage": 45.0,
                "memory_usage": 60.0,
                "network_speed": 100.0
            }
        }
        
        response = client.post("/predict/backup", json=request_data)
        assert response.status_code == 200
        
        data = response.json()
        assert "device_id" in data
        assert "predicted_duration" in data
        assert "predicted_success_rate" in data
        assert "optimal_time_slot" in data
        assert "resource_requirements" in data
        assert "recommendations" in data
        
        # Validate ranges
        assert data["predicted_duration"] > 0
        assert 0 <= data["predicted_success_rate"] <= 1.0
        
    def test_anomaly_detection(self):
        """Test anomaly detection endpoint"""
        request_data = {
            "device_id": "test-device-123",
            "metrics": {
                "cpu_usage": 85.0,
                "memory_usage": 90.0,
                "disk_io": 150.0,
                "network_io": 80.0,
                "backup_speed": 5.0
            },
            "timestamp": datetime.utcnow().isoformat()
        }
        
        response = client.post("/detect/anomaly", json=request_data)
        assert response.status_code == 200
        
        data = response.json()
        assert "device_id" in data
        assert "is_anomaly" in data
        assert "anomaly_score" in data
        assert "affected_metrics" in data
        assert "recommendations" in data
        assert "timestamp" in data
        
    def test_schedule_optimization(self):
        """Test backup schedule optimization"""
        request_data = {
            "backup_jobs": [
                {
                    "id": "job1",
                    "priority": 5,
                    "estimated_size": 5000000,
                    "estimated_duration": 300
                },
                {
                    "id": "job2", 
                    "priority": 2,
                    "estimated_size": 1000000,
                    "estimated_duration": 120
                }
            ],
            "resource_constraints": {
                "max_concurrent_jobs": 3,
                "max_cpu_usage": 80.0,
                "max_memory_usage": 90.0
            },
            "optimization_goals": ["minimize_time", "maximize_throughput"]
        }
        
        response = client.post("/optimize/schedule", json=request_data)
        assert response.status_code == 200
        
        data = response.json()
        assert "optimized_schedule" in data
        assert "expected_improvement" in data
        assert "resource_utilization" in data
        
        # Verify jobs are reordered (high priority first)
        jobs = data["optimized_schedule"]
        assert len(jobs) == 2
        assert jobs[0]["priority"] >= jobs[1]["priority"]

class TestBackupPredictor:
    """Test the BackupPredictor class"""
    
    def test_initialization(self):
        """Test predictor initialization"""
        predictor = BackupPredictor()
        assert predictor.model is None
        assert predictor.scaler is not None
        assert not predictor.is_trained
        
    def test_training_with_data(self):
        """Test training with synthetic data"""
        predictor = BackupPredictor()
        training_data = generate_synthetic_backup_data()
        
        predictor.train(training_data)
        assert predictor.is_trained
        assert predictor.model is not None
        
    def test_training_with_empty_data(self):
        """Test training with empty data"""
        predictor = BackupPredictor()
        empty_data = pd.DataFrame()
        
        predictor.train(empty_data)
        assert not predictor.is_trained
        
    def test_prediction_untrained(self):
        """Test prediction with untrained model"""
        predictor = BackupPredictor()
        features = {
            'file_count': 100,
            'total_size': 1000000,
            'device_cpu': 50.0,
            'device_memory': 60.0,
            'network_speed': 100.0
        }
        
        result = predictor.predict(features)
        assert 'predicted_duration' in result
        assert 'predicted_success_rate' in result
        assert 'confidence' in result
        assert result['confidence'] == 0.5  # Default for untrained
        
    def test_prediction_trained(self):
        """Test prediction with trained model"""
        predictor = BackupPredictor()
        training_data = generate_synthetic_backup_data()
        predictor.train(training_data)
        
        features = {
            'file_count': 100,
            'total_size': 1000000,
            'device_cpu': 50.0,
            'device_memory': 60.0,
            'network_speed': 100.0
        }
        
        result = predictor.predict(features)
        assert result['predicted_duration'] > 0
        assert 0 <= result['predicted_success_rate'] <= 1
        assert result['confidence'] == 0.8  # Higher for trained model

class TestAnomalyDetector:
    """Test the AnomalyDetector class"""
    
    def test_initialization(self):
        """Test detector initialization"""
        detector = AnomalyDetector()
        assert detector.model is not None
        assert detector.scaler is not None
        assert not detector.is_trained
        
    def test_training_with_data(self):
        """Test training with synthetic data"""
        detector = AnomalyDetector()
        training_data = generate_synthetic_anomaly_data()
        
        detector.train(training_data)
        assert detector.is_trained
        
    def test_detection_untrained(self):
        """Test detection with untrained model"""
        detector = AnomalyDetector()
        metrics = {
            'cpu_usage': 85.0,
            'memory_usage': 90.0,
            'disk_io': 150.0,
            'network_io': 80.0,
            'backup_speed': 5.0
        }
        
        result = detector.detect(metrics)
        assert 'is_anomaly' in result
        assert 'anomaly_score' in result
        assert 'affected_metrics' in result
        assert 'confidence' in result
        assert not result['is_anomaly']  # Default for untrained
        assert result['confidence'] == 0.0
        
    def test_detection_trained(self):
        """Test detection with trained model"""
        detector = AnomalyDetector()
        training_data = generate_synthetic_anomaly_data()
        detector.train(training_data)
        
        # Test with normal metrics
        normal_metrics = {
            'cpu_usage': 50.0,
            'memory_usage': 60.0,
            'disk_io': 100.0,
            'network_io': 50.0,
            'backup_speed': 10.0
        }
        
        result = detector.detect(normal_metrics)
        assert isinstance(result['is_anomaly'], bool)
        assert isinstance(result['anomaly_score'], float)
        assert isinstance(result['affected_metrics'], list)
        assert result['confidence'] == 0.8

class TestDataGeneration:
    """Test synthetic data generation functions"""
    
    def test_backup_data_generation(self):
        """Test synthetic backup data generation"""
        data = generate_synthetic_backup_data()
        
        assert isinstance(data, pd.DataFrame)
        assert len(data) == 1000
        assert 'file_count' in data.columns
        assert 'total_size' in data.columns
        assert 'backup_duration' in data.columns
        
        # Check ranges
        assert data['file_count'].min() >= 10
        assert data['file_count'].max() <= 10000
        assert data['backup_duration'].min() >= 30
        
    def test_anomaly_data_generation(self):
        """Test synthetic anomaly data generation"""
        data = generate_synthetic_anomaly_data()
        
        assert isinstance(data, pd.DataFrame)
        assert len(data) == 1000
        assert 'cpu_usage' in data.columns
        assert 'memory_usage' in data.columns
        assert 'backup_speed' in data.columns
        
        # Check ranges (should be clipped to realistic values)
        assert data['cpu_usage'].min() >= 0
        assert data['cpu_usage'].max() <= 100
        assert data['memory_usage'].min() >= 0
        assert data['memory_usage'].max() <= 100

class TestErrorHandling:
    """Test error handling in various scenarios"""
    
    def test_invalid_backup_request(self):
        """Test backup prediction with invalid data"""
        invalid_request = {
            "device_id": "test-device",
            "file_paths": [],  # Empty paths
            "estimated_size": -1  # Invalid size
        }
        
        response = client.post("/predict/backup", json=invalid_request)
        assert response.status_code == 422  # Validation error
        
    def test_invalid_anomaly_request(self):
        """Test anomaly detection with invalid data"""
        invalid_request = {
            "device_id": "test-device",
            "metrics": {},  # Empty metrics
            "timestamp": "invalid-timestamp"
        }
        
        response = client.post("/detect/anomaly", json=invalid_request)
        assert response.status_code == 422  # Validation error

if __name__ == "__main__":
    pytest.main([__file__, "-v"])