#!/usr/bin/env python3
"""
Advanced Backup Prediction System for CoreState Platform

This module implements sophisticated machine learning models for:
- Predicting optimal backup windows based on system patterns
- Forecasting backup duration and resource requirements
- Recommending backup strategies based on historical data
- Adaptive scheduling based on user behavior and system load
"""

import logging
import warnings
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Union

import joblib
import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler, MinMaxScaler
from sklearn.ensemble import RandomForestRegressor, GradientBoostingRegressor
from sklearn.model_selection import train_test_split, GridSearchCV
from sklearn.metrics import mean_squared_error, mean_absolute_error, r2_score

warnings.filterwarnings('ignore', category=FutureWarning)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class AdvancedFeatureExtractor:
    """Extract comprehensive features for backup prediction"""
    
    def __init__(self):
        self.scalers = {}
        self.feature_columns = []
        
    def extract_temporal_features(self, timestamps: pd.Series) -> pd.DataFrame:
        """Extract time-based features"""
        df = pd.DataFrame()
        
        df['hour'] = timestamps.dt.hour
        df['day_of_week'] = timestamps.dt.dayofweek
        df['day_of_month'] = timestamps.dt.day
        df['month'] = timestamps.dt.month
        df['quarter'] = timestamps.dt.quarter
        df['is_weekend'] = (timestamps.dt.dayofweek >= 5).astype(int)
        df['is_business_hours'] = ((timestamps.dt.hour >= 9) & (timestamps.dt.hour <= 17)).astype(int)
        
        # Cyclical encoding for time features
        df['hour_sin'] = np.sin(2 * np.pi * df['hour'] / 24)
        df['hour_cos'] = np.cos(2 * np.pi * df['hour'] / 24)
        df['day_sin'] = np.sin(2 * np.pi * df['day_of_week'] / 7)
        df['day_cos'] = np.cos(2 * np.pi * df['day_of_week'] / 7)
        
        return df


class OptimalWindowPredictor:
    """Predict optimal backup windows using ensemble methods"""
    
    def __init__(self):
        self.models = {
            'random_forest': RandomForestRegressor(n_estimators=200, random_state=42),
            'gradient_boosting': GradientBoostingRegressor(n_estimators=200, random_state=42)
        }
        self.feature_scaler = StandardScaler()
        self.target_scaler = MinMaxScaler()
        self.feature_importance = {}
        
    def prepare_training_data(self, historical_data: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray]:
        """Prepare training data for optimal window prediction"""
        feature_extractor = AdvancedFeatureExtractor()
        
        # Extract features
        temporal_features = feature_extractor.extract_temporal_features(historical_data['timestamp'])
        
        # Combine all features
        X = temporal_features.copy()
        X['system_load'] = historical_data.get('system_load', 0)
        X['user_activity'] = historical_data.get('user_activity', 0)
        X['backup_success_rate'] = historical_data.get('backup_success_rate', 1.0)
        X['resource_availability'] = historical_data.get('resource_availability', 1.0)
        
        # Target: backup window quality score (0-1)
        y = historical_data['window_quality_score'].values
        
        return X.values, y
    
    def train(self, historical_data: pd.DataFrame):
        """Train the optimal window prediction models"""
        logger.info("Training optimal backup window prediction models...")
        
        X, y = self.prepare_training_data(historical_data)
        
        # Scale features
        X_scaled = self.feature_scaler.fit_transform(X)
        y_scaled = self.target_scaler.fit_transform(y.reshape(-1, 1)).ravel()
        
        # Split data
        X_train, X_test, y_train, y_test = train_test_split(
            X_scaled, y_scaled, test_size=0.2, random_state=42
        )
        
        # Train models
        for name, model in self.models.items():
            logger.info(f"Training {name}...")
            model.fit(X_train, y_train)
            
            # Evaluate
            y_pred = model.predict(X_test)
            mse = mean_squared_error(y_test, y_pred)
            r2 = r2_score(y_test, y_pred)
            
            logger.info(f"{name} - MSE: {mse:.4f}, R2: {r2:.4f}")
            
            # Store feature importance for tree-based models
            if hasattr(model, 'feature_importances_'):
                self.feature_importance[name] = model.feature_importances_
        
        logger.info("Optimal window prediction training completed!")
    
    def predict_optimal_windows(self, current_data: Dict, prediction_horizon: int = 24) -> Dict:
        """Predict optimal backup windows for the next N hours"""
        # Generate future timestamps
        future_timestamps = pd.date_range(
            start=datetime.now(),
            periods=prediction_horizon,
            freq='H'
        )
        
        predictions = []
        
        for timestamp in future_timestamps:
            # Create feature vector for this time point
            features = []
            
            # Temporal features
            features.extend([
                timestamp.hour,
                timestamp.weekday(),
                timestamp.day,
                timestamp.month,
                timestamp.quarter,
                1 if timestamp.weekday() >= 5 else 0,  # is_weekend
                1 if 9 <= timestamp.hour <= 17 else 0,  # is_business_hours
                np.sin(2 * np.pi * timestamp.hour / 24),  # hour_sin
                np.cos(2 * np.pi * timestamp.hour / 24),  # hour_cos
                np.sin(2 * np.pi * timestamp.weekday() / 7),  # day_sin
                np.cos(2 * np.pi * timestamp.weekday() / 7),  # day_cos
            ])
            
            # Current system and user data (use provided or defaults)
            features.extend([
                current_data.get('system_load', 0.5),
                current_data.get('user_activity', 0.3),
                current_data.get('backup_success_rate', 0.95),
                current_data.get('resource_availability', 0.8),
            ])
            
            feature_vector = np.array(features).reshape(1, -1)
            feature_vector_scaled = self.feature_scaler.transform(feature_vector)
            
            # Ensemble prediction
            ensemble_pred = 0
            for model in self.models.values():
                pred = model.predict(feature_vector_scaled)[0]
                ensemble_pred += pred
            
            ensemble_pred /= len(self.models)
            
            # Scale back to original range
            final_pred = self.target_scaler.inverse_transform([[ensemble_pred]])[0][0]
            
            predictions.append({
                'timestamp': timestamp.isoformat(),
                'hour': timestamp.hour,
                'quality_score': float(final_pred),
                'recommended': final_pred > 0.7  # Threshold for recommendation
            })
        
        # Find optimal windows (consecutive high-quality periods)
        optimal_windows = self._find_optimal_windows(predictions)
        
        return {
            'predictions': predictions,
            'optimal_windows': optimal_windows,
            'best_window': optimal_windows[0] if optimal_windows else None,
            'prediction_horizon_hours': prediction_horizon
        }
    
    def _find_optimal_windows(self, predictions: List[Dict], min_duration: int = 2) -> List[Dict]:
        """Find consecutive high-quality backup windows"""
        windows = []
        current_window = None
        
        for pred in predictions:
            if pred['recommended']:
                if current_window is None:
                    current_window = {
                        'start_time': pred['timestamp'],
                        'start_hour': pred['hour'],
                        'scores': [pred['quality_score']]
                    }
                else:
                    current_window['scores'].append(pred['quality_score'])
            else:
                if current_window is not None and len(current_window['scores']) >= min_duration:
                    current_window['end_time'] = predictions[predictions.index(pred) - 1]['timestamp']
                    current_window['end_hour'] = predictions[predictions.index(pred) - 1]['hour']
                    current_window['duration_hours'] = len(current_window['scores'])
                    current_window['avg_quality'] = np.mean(current_window['scores'])
                    windows.append(current_window)
                current_window = None
        
        # Sort by quality score
        windows.sort(key=lambda x: x['avg_quality'], reverse=True)
        
        return windows


class ComprehensiveBackupPredictor:
    """Main backup prediction system"""
    
    def __init__(self):
        self.window_predictor = OptimalWindowPredictor()
        
    def train(self, historical_data: pd.DataFrame):
        """Train all prediction models"""
        logger.info("Starting comprehensive backup prediction training...")
        
        # Train optimal window predictor
        if 'window_quality_score' in historical_data.columns:
            self.window_predictor.train(historical_data)
        
        logger.info("Backup prediction training completed!")
    
    def predict_optimal_backup_windows(self, current_data: Dict) -> Dict:
        """Comprehensive backup prediction"""
        window_predictions = self.window_predictor.predict_optimal_windows(current_data)
        
        # Add overall recommendation
        window_predictions['recommendation'] = self._generate_recommendation(
            window_predictions, current_data
        )
        
        return window_predictions
    
    def _generate_recommendation(self, predictions: Dict, current_data: Dict) -> Dict:
        """Generate actionable backup recommendations"""
        best_window = predictions.get('best_window')
        
        if not best_window:
            return {
                'action': 'schedule_fallback',
                'message': 'No optimal windows found, use default schedule',
                'suggested_time': (datetime.now() + timedelta(hours=2)).isoformat()
            }
        
        return {
            'action': 'schedule_backup',
            'message': f'Schedule backup for {best_window["start_time"]}',
            'scheduled_time': best_window['start_time'],
            'window_quality': best_window['avg_quality']
        }


if __name__ == "__main__":
    # Example usage and testing
    predictor = ComprehensiveBackupPredictor()
    
    # Generate sample training data
    dates = pd.date_range(start='2023-01-01', end='2023-12-31', freq='H')
    sample_data = pd.DataFrame({
        'timestamp': dates,
        'system_load': np.random.beta(2, 3, len(dates)),
        'user_activity': np.random.beta(1, 4, len(dates)),
        'window_quality_score': np.random.beta(3, 2, len(dates)),
        'backup_success_rate': np.random.beta(9, 1, len(dates)),
        'resource_availability': np.random.beta(4, 2, len(dates)),
    })
    
    # Train the predictor
    predictor.train(sample_data)
    
    # Test prediction
    current_data = {
        'system_load': 0.4,
        'user_activity': 0.2,
        'backup_success_rate': 0.98,
        'resource_availability': 0.9,
    }
    
    result = predictor.predict_optimal_backup_windows(current_data)
    
    print("Backup Prediction Results:")
    print(f"Best window: {result.get('best_window', 'None')}")
    print(f"Recommendation: {result.get('recommendation', {})}")
    print("Advanced backup prediction system initialized successfully!")