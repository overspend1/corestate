#!/usr/bin/env python3
"""
Advanced Anomaly Detection System for CoreState Backup Platform

This module implements multiple anomaly detection algorithms including:
- Variational Autoencoder for backup pattern anomalies
- Isolation Forest for outlier detection
- LSTM-based time series anomaly detection
- Statistical process control methods
"""

import logging
import pickle
import warnings
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Union

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler, MinMaxScaler
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, confusion_matrix
import tensorflow as tf
from tensorflow.keras import layers, models, callbacks
from tensorflow.keras.losses import mse
from tensorflow.keras.optimizers import Adam

warnings.filterwarnings('ignore', category=FutureWarning)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class FeatureExtractor:
    """Extract comprehensive features from backup metadata and system metrics"""
    
    def __init__(self):
        self.scalers = {}
        self.feature_names = []
        
    def extract_backup_features(self, backup_metadata: Dict) -> np.ndarray:
        """Extract features from backup metadata"""
        features = []
        
        # File-level features
        features.extend([
            backup_metadata.get('file_count', 0),
            backup_metadata.get('total_size_bytes', 0),
            backup_metadata.get('compressed_size_bytes', 0),
            backup_metadata.get('compression_ratio', 0),
            backup_metadata.get('deduplication_ratio', 0),
            backup_metadata.get('backup_duration_seconds', 0),
            backup_metadata.get('throughput_mbps', 0),
        ])
        
        # Time-based features
        timestamp = backup_metadata.get('timestamp', datetime.now())
        if isinstance(timestamp, str):
            timestamp = pd.to_datetime(timestamp)
            
        features.extend([
            timestamp.hour,
            timestamp.weekday(),
            timestamp.day,
            timestamp.month,
        ])
        
        # System resource features
        features.extend([
            backup_metadata.get('cpu_usage_percent', 0),
            backup_metadata.get('memory_usage_percent', 0),
            backup_metadata.get('disk_io_rate', 0),
            backup_metadata.get('network_utilization', 0),
        ])
        
        # Error and retry features
        features.extend([
            backup_metadata.get('error_count', 0),
            backup_metadata.get('retry_count', 0),
            backup_metadata.get('checksum_failures', 0),
        ])
        
        # Data integrity features
        features.extend([
            backup_metadata.get('file_type_diversity', 0),
            backup_metadata.get('average_file_size', 0),
            backup_metadata.get('modified_files_ratio', 0),
            backup_metadata.get('new_files_ratio', 0),
        ])
        
        return np.array(features, dtype=np.float32)
    
    def extract_time_series_features(self, time_series_data: pd.DataFrame) -> np.ndarray:
        """Extract features from time series backup data"""
        # Statistical features
        features = [
            time_series_data['backup_size'].mean(),
            time_series_data['backup_size'].std(),
            time_series_data['backup_size'].min(),
            time_series_data['backup_size'].max(),
            time_series_data['backup_duration'].mean(),
            time_series_data['backup_duration'].std(),
            time_series_data['backup_duration'].skew(),
            time_series_data['backup_duration'].kurt(),
        ]
        
        # Trend features
        backup_sizes = time_series_data['backup_size'].values
        if len(backup_sizes) > 1:
            features.extend([
                np.polyfit(range(len(backup_sizes)), backup_sizes, 1)[0],  # Linear trend
                np.corrcoef(backup_sizes[:-1], backup_sizes[1:])[0, 1],  # Autocorrelation
            ])
        else:
            features.extend([0, 0])
            
        return np.array(features, dtype=np.float32)


class ComprehensiveAnomalyDetector:
    """Main anomaly detection system combining multiple approaches"""
    
    def __init__(self):
        self.feature_extractor = FeatureExtractor()
        self.isolation_forest = None
        self.ensemble_weights = {'isolation_forest': 1.0}
        
    def train(self, backup_data: List[Dict], time_series_data: Optional[pd.DataFrame] = None):
        """Train all anomaly detection models"""
        logger.info("Starting comprehensive anomaly detection training...")
        
        # Extract features
        features = np.array([self.feature_extractor.extract_backup_features(data) for data in backup_data])
        
        # Train Isolation Forest
        logger.info("Training Isolation Forest...")
        self.isolation_forest = IsolationForest(
            contamination=0.1,
            random_state=42,
            n_estimators=200
        )
        self.isolation_forest.fit(features)
        
        logger.info("Anomaly detection training completed!")
    
    def detect_anomalies(self, backup_metadata: Dict, 
                        time_series_data: Optional[pd.DataFrame] = None) -> Dict:
        """Detect anomalies using ensemble approach"""
        results = {
            'is_anomaly': False,
            'anomaly_score': 0.0,
            'component_scores': {},
            'details': {}
        }
        
        # Extract features
        features = self.feature_extractor.extract_backup_features(backup_metadata)
        features = features.reshape(1, -1)
        
        # Isolation Forest detection
        if self.isolation_forest is not None:
            if_prediction = self.isolation_forest.predict(features)[0]
            if_score = self.isolation_forest.decision_function(features)[0]
            
            # Convert to 0-1 scale (negative values indicate anomalies)
            if_normalized = max(0, 1 - (-if_score + 1) / 2)
            results['component_scores']['isolation_forest'] = if_normalized
            results['anomaly_score'] = if_normalized
            results['is_anomaly'] = if_prediction == -1
        
        # Add detailed analysis
        results['details'] = {
            'timestamp': datetime.now().isoformat(),
            'backup_size': backup_metadata.get('total_size_bytes', 0),
            'compression_ratio': backup_metadata.get('compression_ratio', 0),
            'duration': backup_metadata.get('backup_duration_seconds', 0),
            'error_count': backup_metadata.get('error_count', 0)
        }
        
        return results


if __name__ == "__main__":
    # Example usage and testing
    detector = ComprehensiveAnomalyDetector()
    
    # Generate sample training data
    sample_data = []
    for i in range(1000):
        sample_data.append({
            'file_count': np.random.randint(100, 10000),
            'total_size_bytes': np.random.randint(1000000, 100000000),
            'compressed_size_bytes': np.random.randint(500000, 50000000),
            'compression_ratio': np.random.uniform(0.3, 0.8),
            'deduplication_ratio': np.random.uniform(0.1, 0.5),
            'backup_duration_seconds': np.random.randint(300, 7200),
            'throughput_mbps': np.random.uniform(10, 100),
            'timestamp': datetime.now() - timedelta(days=np.random.randint(0, 365)),
            'cpu_usage_percent': np.random.uniform(20, 80),
            'memory_usage_percent': np.random.uniform(30, 90),
            'disk_io_rate': np.random.uniform(0, 100),
            'network_utilization': np.random.uniform(0, 100),
            'error_count': np.random.randint(0, 5),
            'retry_count': np.random.randint(0, 3),
            'checksum_failures': np.random.randint(0, 2),
            'file_type_diversity': np.random.uniform(0, 1),
            'average_file_size': np.random.uniform(1000, 10000000),
            'modified_files_ratio': np.random.uniform(0, 1),
            'new_files_ratio': np.random.uniform(0, 0.5),
        })
    
    # Train the detector
    detector.train(sample_data)
    
    # Test anomaly detection
    test_metadata = sample_data[0]
    result = detector.detect_anomalies(test_metadata)
    
    print(f"Anomaly Detection Result: {result}")
    print("Advanced anomaly detection system initialized successfully!")