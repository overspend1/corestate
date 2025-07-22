# ml-optimizer/models/anomaly_detector.py
# Note: Correcting path to ml/models as per new structure
import tensorflow as tf
# from tensorflow.keras import layers, models # Correct import path might vary
import numpy as np

class AnomalyDetector:
    def __init__(self):
        self.autoencoder = self._build_autoencoder()
        self.threshold = 0.1 # Example threshold
    
    def _build_autoencoder(self):
        """Autoencoder for detecting backup anomalies"""
        input_dim = 512  # Feature vector size
        encoding_dim = 32
        
        # Encoder
        input_layer = tf.keras.layers.Input(shape=(input_dim,))
        encoded = tf.keras.layers.Dense(256, activation='relu')(input_layer)
        encoded = tf.keras.layers.Dense(128, activation='relu')(encoded)
        encoded = tf.keras.layers.Dense(encoding_dim, activation='relu')(encoded)
        
        # Decoder
        decoded = tf.keras.layers.Dense(128, activation='relu')(encoded)
        decoded = tf.keras.layers.Dense(256, activation='relu')(decoded)
        decoded = tf.keras.layers.Dense(input_dim, activation='sigmoid')(decoded)
        
        autoencoder = tf.keras.models.Model(input_layer, decoded)
        autoencoder.compile(optimizer='adam', loss='mse')
        
        return autoencoder

    def _extract_features(self, backup_metadata):
        # Placeholder for feature extraction
        return np.random.rand(1, 512)

    def detect_corruption(self, backup_metadata):
        """Detect potential data corruption in backups"""
        features = self._extract_features(backup_metadata)
        reconstruction = self.autoencoder.predict(features)
        mse = np.mean(np.power(features - reconstruction, 2), axis=1)
        
        return mse > self.threshold