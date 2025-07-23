import express from 'express';
import { createServer } from 'http';
import WebSocket from 'ws';
import * as Y from 'yjs';
import { setupWSConnection } from 'y-websocket/bin/utils';
import Redis from 'ioredis';
import { v4 as uuidv4 } from 'uuid';
import winston from 'winston';

const app = express();
const server = createServer(app);
const wss = new WebSocket.Server({ server });

// Configure logging
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.json()
  ),
  transports: [
    new winston.transports.Console(),
    new winston.transports.File({ filename: 'sync-coordinator.log' })
  ]
});

// Redis client for persistence
const redis = new Redis({
  host: process.env.REDIS_HOST || 'localhost',
  port: parseInt(process.env.REDIS_PORT || '6379'),
  retryDelayOnFailover: 100,
  enableReadyCheck: true,
  maxRetriesPerRequest: 3
});

// CRDT document storage
const documents = new Map<string, Y.Doc>();

// Backup state management using CRDT
interface BackupState {
  deviceId: string;
  lastSync: number;
  files: Map<string, FileState>;
  chunks: Map<string, ChunkState>;
}

interface FileState {
  path: string;
  hash: string;
  size: number;
  modified: number;
  chunks: string[];
}

interface ChunkState {
  id: string;
  hash: string;
  size: number;
  references: string[];
}

class BackupStateManager {
  private doc: Y.Doc;
  private deviceId: string;

  constructor(deviceId: string) {
    this.deviceId = deviceId;
    this.doc = new Y.Doc();
    this.setupCRDT();
  }

  private setupCRDT() {
    const files = this.doc.getMap('files');
    const chunks = this.doc.getMap('chunks');
    
    files.observe((event) => {
      logger.info('Files map updated', { 
        deviceId: this.deviceId, 
        changes: event.changes 
      });
    });

    chunks.observe((event) => {
      logger.info('Chunks map updated', { 
        deviceId: this.deviceId, 
        changes: event.changes 
      });
    });
  }

  updateFileState(filePath: string, state: FileState) {
    const files = this.doc.getMap('files');
    files.set(filePath, state);
  }

  updateChunkState(chunkId: string, state: ChunkState) {
    const chunks = this.doc.getMap('chunks');
    chunks.set(chunkId, state);
  }

  getDocument(): Y.Doc {
    return this.doc;
  }
}

// Device management
const connectedDevices = new Map<string, {
  socket: WebSocket;
  deviceId: string;
  backupState: BackupStateManager;
  lastHeartbeat: number;
}>();

// WebSocket connection handling
wss.on('connection', (ws: WebSocket, req) => {
  const deviceId = req.headers['x-device-id'] as string || uuidv4();
  
  logger.info('Device connected', { deviceId });

  const backupState = new BackupStateManager(deviceId);
  
  connectedDevices.set(deviceId, {
    socket: ws,
    deviceId,
    backupState,
    lastHeartbeat: Date.now()
  });

  // Setup Y.js WebSocket connection for CRDT sync
  setupWSConnection(ws, req, {
    docName: `backup-state-${deviceId}`,
    gc: true
  });

  ws.on('message', async (data: Buffer) => {
    try {
      const message = JSON.parse(data.toString());
      await handleMessage(deviceId, message);
    } catch (error) {
      logger.error('Message handling error', { deviceId, error });
    }
  });

  ws.on('close', () => {
    logger.info('Device disconnected', { deviceId });
    connectedDevices.delete(deviceId);
  });

  // Send initial sync message
  ws.send(JSON.stringify({
    type: 'sync_init',
    deviceId,
    timestamp: Date.now()
  }));
});

async function handleMessage(deviceId: string, message: any) {
  const device = connectedDevices.get(deviceId);
  if (!device) return;

  switch (message.type) {
    case 'heartbeat':
      device.lastHeartbeat = Date.now();
      device.socket.send(JSON.stringify({ type: 'heartbeat_ack' }));
      break;

    case 'file_update':
      device.backupState.updateFileState(message.filePath, message.fileState);
      await broadcastUpdate(deviceId, message);
      break;

    case 'chunk_update':
      device.backupState.updateChunkState(message.chunkId, message.chunkState);
      await broadcastUpdate(deviceId, message);
      break;

    case 'sync_request':
      await handleSyncRequest(deviceId, message);
      break;

    default:
      logger.warn('Unknown message type', { deviceId, type: message.type });
  }
}

async function broadcastUpdate(sourceDeviceId: string, message: any) {
  const updateMessage = {
    ...message,
    sourceDevice: sourceDeviceId,
    timestamp: Date.now()
  };

  for (const [deviceId, device] of connectedDevices) {
    if (deviceId !== sourceDeviceId && device.socket.readyState === WebSocket.OPEN) {
      device.socket.send(JSON.stringify(updateMessage));
    }
  }

  // Persist to Redis
  await redis.set(
    `sync:update:${Date.now()}:${sourceDeviceId}`,
    JSON.stringify(updateMessage),
    'EX',
    3600 // 1 hour TTL
  );
}

async function handleSyncRequest(deviceId: string, message: any) {
  const device = connectedDevices.get(deviceId);
  if (!device) return;

  // Get recent updates from Redis
  const keys = await redis.keys(`sync:update:*`);
  const updates = await Promise.all(
    keys.map(key => redis.get(key))
  );

  const syncData = {
    type: 'sync_response',
    updates: updates.filter(Boolean).map(update => JSON.parse(update!)),
    timestamp: Date.now()
  };

  device.socket.send(JSON.stringify(syncData));
}

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({
    status: 'healthy',
    connectedDevices: connectedDevices.size,
    uptime: process.uptime(),
    timestamp: Date.now()
  });
});

// Metrics endpoint
app.get('/metrics', (req, res) => {
  const metrics = {
    connected_devices: connectedDevices.size,
    total_documents: documents.size,
    uptime_seconds: process.uptime(),
    memory_usage: process.memoryUsage()
  };

  res.json(metrics);
});

// Cleanup disconnected devices
setInterval(() => {
  const now = Date.now();
  const timeout = 30000; // 30 seconds

  for (const [deviceId, device] of connectedDevices) {
    if (now - device.lastHeartbeat > timeout) {
      logger.info('Removing stale device', { deviceId });
      device.socket.close();
      connectedDevices.delete(deviceId);
    }
  }
}, 15000);

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  logger.info(`Sync Coordinator listening on port ${PORT}`);
});

export default app;