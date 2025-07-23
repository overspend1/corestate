import express from 'express';
import { createServer } from 'http';
import helmet from 'helmet';
import cors from 'cors';
import compression from 'compression';
import winston from 'winston';
import crypto from 'crypto';
import { promisify } from 'util';
import * as fs from 'fs/promises';
import { v4 as uuidv4 } from 'uuid';

// Encryption algorithms and configurations
const ALGORITHMS = {
  AES_256_GCM: 'aes-256-gcm',
  AES_256_CBC: 'aes-256-cbc',
  CHACHA20_POLY1305: 'chacha20-poly1305'
} as const;

const KEY_DERIVATION = {
  PBKDF2: 'pbkdf2',
  SCRYPT: 'scrypt',
  ARGON2: 'argon2id'
} as const;

// Types
interface EncryptionRequest {
  data: string; // Base64 encoded data
  deviceId: string;
  algorithm?: keyof typeof ALGORITHMS;
  keyDerivation?: keyof typeof KEY_DERIVATION;
}

interface DecryptionRequest {
  encryptedData: string; // Base64 encoded
  deviceId: string;
  keyId?: string;
  iv?: string;
  authTag?: string;
}

interface KeyRotationRequest {
  deviceId: string;
  newPassword?: string;
  keyDerivation?: keyof typeof KEY_DERIVATION;
}

interface EncryptionResult {
  encryptedData: string; // Base64 encoded
  keyId: string;
  iv: string;
  authTag?: string;
  algorithm: string;
  timestamp: number;
}

interface DecryptionResult {
  data: string; // Base64 encoded
  keyId: string;
  algorithm: string;
  timestamp: number;
}

interface DeviceKey {
  keyId: string;
  deviceId: string;
  encryptedKey: string;
  salt: string;
  iv: string;
  algorithm: string;
  keyDerivation: string;
  iterations: number;
  createdAt: number;
  isActive: boolean;
}

// Configure logging
const logger = winston.createLogger({
  level: process.env.LOG_LEVEL || 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.json()
  ),
  transports: [
    new winston.transports.Console(),
    new winston.transports.File({ 
      filename: '/var/log/encryption-service.log',
      maxsize: 10 * 1024 * 1024, // 10MB
      maxFiles: 5
    })
  ]
});

class EncryptionService {
  private deviceKeys: Map<string, DeviceKey[]> = new Map();
  private masterKey: Buffer;

  constructor() {
    this.masterKey = this.loadOrGenerateMasterKey();
    this.loadDeviceKeys();
  }

  private loadOrGenerateMasterKey(): Buffer {
    try {
      const keyPath = process.env.MASTER_KEY_PATH || '/etc/corestate/master.key';
      const keyData = require('fs').readFileSync(keyPath);
      logger.info('Master key loaded from file');
      return keyData;
    } catch (error) {
      logger.warn('Master key not found, generating new one');
      const newKey = crypto.randomBytes(32);
      
      try {
        const keyPath = process.env.MASTER_KEY_PATH || '/etc/corestate/master.key';
        require('fs').writeFileSync(keyPath, newKey, { mode: 0o600 });
        logger.info('Master key saved to file');
      } catch (writeError) {
        logger.error('Failed to save master key:', writeError);
      }
      
      return newKey;
    }
  }

  private async loadDeviceKeys(): Promise<void> {
    try {
      const keysPath = process.env.DEVICE_KEYS_PATH || '/var/lib/corestate/device-keys.json';
      const keysData = await fs.readFile(keysPath, 'utf8');
      const deviceKeysArray: DeviceKey[] = JSON.parse(keysData);
      
      deviceKeysArray.forEach(key => {
        if (!this.deviceKeys.has(key.deviceId)) {
          this.deviceKeys.set(key.deviceId, []);
        }
        this.deviceKeys.get(key.deviceId)!.push(key);
      });
      
      logger.info(`Loaded keys for ${this.deviceKeys.size} devices`);
    } catch (error) {
      logger.info('No existing device keys found, starting fresh');
    }
  }

  private async saveDeviceKeys(): Promise<void> {
    try {
      const keysPath = process.env.DEVICE_KEYS_PATH || '/var/lib/corestate/device-keys.json';
      const allKeys: DeviceKey[] = [];
      
      this.deviceKeys.forEach(keys => {
        allKeys.push(...keys);
      });
      
      await fs.writeFile(keysPath, JSON.stringify(allKeys, null, 2), 'utf8');
      logger.debug('Device keys saved to file');
    } catch (error) {
      logger.error('Failed to save device keys:', error);
    }
  }

  async generateDeviceKey(deviceId: string, password?: string): Promise<DeviceKey> {
    const keyId = uuidv4();
    const algorithm = ALGORITHMS.AES_256_GCM;
    const keyDerivation = KEY_DERIVATION.SCRYPT;
    
    // Generate salt and device key
    const salt = crypto.randomBytes(32);
    const devicePassword = password || crypto.randomBytes(64).toString('hex');
    
    // Derive encryption key from password
    const derivedKey = await this.deriveKey(devicePassword, salt, keyDerivation);
    
    // Encrypt the derived key with master key
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipher('aes-256-gcm', this.masterKey);
    cipher.setAAD(Buffer.from(deviceId));
    
    let encryptedKey = cipher.update(derivedKey);
    encryptedKey = Buffer.concat([encryptedKey, cipher.final()]);
    const authTag = cipher.getAuthTag();
    
    const deviceKey: DeviceKey = {
      keyId,
      deviceId,
      encryptedKey: Buffer.concat([encryptedKey, authTag]).toString('base64'),
      salt: salt.toString('base64'),
      iv: iv.toString('base64'),
      algorithm,
      keyDerivation,
      iterations: keyDerivation === KEY_DERIVATION.SCRYPT ? 16384 : 100000,
      createdAt: Date.now(),
      isActive: true
    };

    // Deactivate previous keys
    const existingKeys = this.deviceKeys.get(deviceId) || [];
    existingKeys.forEach(key => key.isActive = false);
    
    // Add new key
    if (!this.deviceKeys.has(deviceId)) {
      this.deviceKeys.set(deviceId, []);
    }
    this.deviceKeys.get(deviceId)!.push(deviceKey);
    
    await this.saveDeviceKeys();
    
    logger.info(`Generated new key for device: ${deviceId}`);
    return deviceKey;
  }

  private async deriveKey(password: string, salt: Buffer, method: string): Promise<Buffer> {
    switch (method) {
      case KEY_DERIVATION.SCRYPT:
        return promisify(crypto.scrypt)(password, salt, 32) as Promise<Buffer>;
      
      case KEY_DERIVATION.PBKDF2:
        return promisify(crypto.pbkdf2)(password, salt, 100000, 32, 'sha256') as Promise<Buffer>;
      
      default:
        throw new Error(`Unsupported key derivation method: ${method}`);
    }
  }

  async getDeviceKey(deviceId: string, keyId?: string): Promise<DeviceKey | null> {
    const keys = this.deviceKeys.get(deviceId);
    if (!keys || keys.length === 0) {
      return null;
    }

    if (keyId) {
      return keys.find(key => key.keyId === keyId) || null;
    }

    // Return the active key
    return keys.find(key => key.isActive) || keys[keys.length - 1];
  }

  async decryptDeviceKey(deviceKey: DeviceKey): Promise<Buffer> {
    const encryptedData = Buffer.from(deviceKey.encryptedKey, 'base64');
    const encryptedKey = encryptedData.slice(0, -16);
    const authTag = encryptedData.slice(-16);
    const iv = Buffer.from(deviceKey.iv, 'base64');
    
    const decipher = crypto.createDecipher('aes-256-gcm', this.masterKey);
    decipher.setAAD(Buffer.from(deviceKey.deviceId));
    decipher.setAuthTag(authTag);
    
    let decryptedKey = decipher.update(encryptedKey);
    decryptedKey = Buffer.concat([decryptedKey, decipher.final()]);
    
    return decryptedKey;
  }

  async encryptData(request: EncryptionRequest): Promise<EncryptionResult> {
    const algorithm = request.algorithm || 'AES_256_GCM';
    const data = Buffer.from(request.data, 'base64');
    
    // Get or generate device key
    let deviceKey = await this.getDeviceKey(request.deviceId);
    if (!deviceKey) {
      deviceKey = await this.generateDeviceKey(request.deviceId);
    }
    
    const key = await this.decryptDeviceKey(deviceKey);
    const iv = crypto.randomBytes(12);
    
    const cipher = crypto.createCipher(ALGORITHMS[algorithm], key);
    
    let encrypted = cipher.update(data);
    encrypted = Buffer.concat([encrypted, cipher.final()]);
    
    let authTag: Buffer | undefined;
    if (algorithm === 'AES_256_GCM') {
      authTag = (cipher as any).getAuthTag();
    }
    
    logger.info(`Encrypted data for device: ${request.deviceId}`);
    
    return {
      encryptedData: encrypted.toString('base64'),
      keyId: deviceKey.keyId,
      iv: iv.toString('base64'),
      authTag: authTag?.toString('base64'),
      algorithm: ALGORITHMS[algorithm],
      timestamp: Date.now()
    };
  }

  async decryptData(request: DecryptionRequest): Promise<DecryptionResult> {
    const deviceKey = await this.getDeviceKey(request.deviceId, request.keyId);
    if (!deviceKey) {
      throw new Error(`No encryption key found for device: ${request.deviceId}`);
    }
    
    const key = await this.decryptDeviceKey(deviceKey);
    const encryptedData = Buffer.from(request.encryptedData, 'base64');
    const iv = Buffer.from(request.iv || deviceKey.iv, 'base64');
    
    const decipher = crypto.createDecipher(deviceKey.algorithm as any, key);
    
    if (request.authTag) {
      (decipher as any).setAuthTag(Buffer.from(request.authTag, 'base64'));
    }
    
    let decrypted = decipher.update(encryptedData);
    decrypted = Buffer.concat([decrypted, decipher.final()]);
    
    logger.info(`Decrypted data for device: ${request.deviceId}`);
    
    return {
      data: decrypted.toString('base64'),
      keyId: deviceKey.keyId,
      algorithm: deviceKey.algorithm,
      timestamp: Date.now()
    };
  }

  async rotateDeviceKey(request: KeyRotationRequest): Promise<DeviceKey> {
    logger.info(`Rotating key for device: ${request.deviceId}`);
    
    // Deactivate current keys
    const existingKeys = this.deviceKeys.get(request.deviceId) || [];
    existingKeys.forEach(key => key.isActive = false);
    
    // Generate new key
    return await this.generateDeviceKey(request.deviceId, request.newPassword);
  }

  getDeviceKeyInfo(deviceId: string): any {
    const keys = this.deviceKeys.get(deviceId) || [];
    return keys.map(key => ({
      keyId: key.keyId,
      algorithm: key.algorithm,
      keyDerivation: key.keyDerivation,
      createdAt: new Date(key.createdAt).toISOString(),
      isActive: key.isActive
    }));
  }

  getMetrics() {
    const totalDevices = this.deviceKeys.size;
    let totalKeys = 0;
    let activeKeys = 0;
    
    this.deviceKeys.forEach(keys => {
      totalKeys += keys.length;
      activeKeys += keys.filter(key => key.isActive).length;
    });
    
    return {
      totalDevices,
      totalKeys,
      activeKeys,
      supportedAlgorithms: Object.values(ALGORITHMS),
      supportedKeyDerivation: Object.values(KEY_DERIVATION),
      masterKeyPresent: !!this.masterKey,
      uptime: process.uptime()
    };
  }
}

// Initialize service
const encryptionService = new EncryptionService();
const app = express();

// Middleware
app.use(helmet());
app.use(cors());
app.use(compression());
app.use(express.json({ limit: '100mb' }));
app.use(express.urlencoded({ extended: true }));

// Request logging
app.use((req, res, next) => {
  logger.info(`${req.method} ${req.path}`, {
    ip: req.ip,
    userAgent: req.get('User-Agent')
  });
  next();
});

// Routes
app.post('/api/v1/encrypt', async (req, res) => {
  try {
    const result = await encryptionService.encryptData(req.body);
    res.json(result);
  } catch (error) {
    logger.error('Encryption error:', error);
    res.status(500).json({ error: 'Encryption failed', message: error.message });
  }
});

app.post('/api/v1/decrypt', async (req, res) => {
  try {
    const result = await encryptionService.decryptData(req.body);
    res.json(result);
  } catch (error) {
    logger.error('Decryption error:', error);
    res.status(500).json({ error: 'Decryption failed', message: error.message });
  }
});

app.post('/api/v1/keys/generate', async (req, res) => {
  try {
    const { deviceId, password, keyDerivation } = req.body;
    const deviceKey = await encryptionService.generateDeviceKey(deviceId, password);
    
    res.json({
      keyId: deviceKey.keyId,
      algorithm: deviceKey.algorithm,
      keyDerivation: deviceKey.keyDerivation,
      createdAt: new Date(deviceKey.createdAt).toISOString()
    });
  } catch (error) {
    logger.error('Key generation error:', error);
    res.status(500).json({ error: 'Key generation failed', message: error.message });
  }
});

app.post('/api/v1/keys/rotate', async (req, res) => {
  try {
    const deviceKey = await encryptionService.rotateDeviceKey(req.body);
    
    res.json({
      keyId: deviceKey.keyId,
      algorithm: deviceKey.algorithm,
      keyDerivation: deviceKey.keyDerivation,
      createdAt: new Date(deviceKey.createdAt).toISOString()
    });
  } catch (error) {
    logger.error('Key rotation error:', error);
    res.status(500).json({ error: 'Key rotation failed', message: error.message });
  }
});

app.get('/api/v1/keys/:deviceId', async (req, res) => {
  try {
    const keyInfo = encryptionService.getDeviceKeyInfo(req.params.deviceId);
    res.json({ deviceId: req.params.deviceId, keys: keyInfo });
  } catch (error) {
    logger.error('Key info error:', error);
    res.status(500).json({ error: 'Failed to get key info', message: error.message });
  }
});

app.get('/api/v1/health', (req, res) => {
  res.json({
    status: 'healthy',
    service: 'encryption-service',
    version: '2.0.0',
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
});

app.get('/api/v1/metrics', (req, res) => {
  const metrics = encryptionService.getMetrics();
  res.json(metrics);
});

// Error handling
app.use((error: any, req: any, res: any, next: any) => {
  logger.error('Unhandled error:', error);
  res.status(500).json({
    error: 'Internal server error',
    message: process.env.NODE_ENV === 'production' ? 'Something went wrong' : error.message
  });
});

// 404 handler
app.use('*', (req, res) => {
  res.status(404).json({ error: 'Not found', path: req.originalUrl });
});

const PORT = process.env.PORT || 3004;
const server = createServer(app);

server.listen(PORT, () => {
  logger.info(`Encryption Service listening on port ${PORT}`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  logger.info('SIGTERM received, shutting down gracefully');
  server.close(() => {
    logger.info('Process terminated');
    process.exit(0);
  });
});

export default app;