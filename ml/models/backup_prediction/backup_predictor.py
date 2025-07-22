import tensorflow as tf
from tensorflow.keras import layers, models
import numpy as np

class FeatureExtractor:
    def extract(self, user_patterns, system_load):
        # In a real scenario, this would process real data.
        # For now, we return a correctly shaped random tensor.
        print("Extracting features from user patterns and system load...")
        return np.random.rand(1, 168, 15).astype(np.float32)

class BackupPredictor:
    def __init__(self):
        self.model = self._build_model()
        self.feature_extractor = FeatureExtractor()
    
    def _build_model(self):
        """LSTM-based model for predicting optimal backup times"""
        print("Building LSTM model for backup prediction...")
        model = models.Sequential([
            layers.LSTM(128, return_sequences=True, input_shape=(168, 15)),
            layers.Dropout(0.2),
            layers.LSTM(64, return_sequences=True),
            layers.Dropout(0.2),
            layers.LSTM(32),
            layers.Dense(64, activation='relu'),
            layers.Dense(24, activation='sigmoid')
        ])
        
        model.compile(
            optimizer='adam',
            loss='binary_crossentropy',
            metrics=['accuracy', tf.keras.metrics.Precision(), tf.keras.metrics.Recall()]
        )
        print("Model compiled successfully.")
        return model
    
    def _post_process_predictions(self, predictions):
        # Example: Return hours where prediction > 0.7
        optimal_hours = np.where(predictions[0] > 0.7)[0]
        return {"optimal_hours": optimal_hours.tolist(), "raw_predictions": predictions.tolist()}

    def predict_optimal_backup_windows(self, user_patterns, system_load):
        features = self.feature_extractor.extract(user_patterns, system_load)
        print("Predicting optimal backup windows...")
        predictions = self.model.predict(features)
        return self._post_process_predictions(predictions)