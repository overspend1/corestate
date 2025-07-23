import * as Y from 'yjs';
import { v4 as uuidv4 } from 'uuid';

export interface FileMetadata {
  path: string;
  hash: string;
  size: number;
  modified: number;
  chunks: string[];
  deviceId: string;
  backupTime: number;
  isDeleted: boolean;
}

export interface ChunkMetadata {
  id: string;
  hash: string;
  size: number;
  references: string[];
  storageNodes: string[];
  createdTime: number;
  deviceId: string;
}

export interface DeviceState {
  deviceId: string;
  lastSync: number;
  isOnline: boolean;
  backupProgress: number;
  totalFiles: number;
  completedFiles: number;
  syncVersion: number;
}

export interface BackupJob {
  id: string;
  deviceId: string;
  status: 'pending' | 'running' | 'completed' | 'failed' | 'paused';
  startTime: number;
  endTime?: number;
  totalSize: number;
  processedSize: number;
  filesCount: number;
  processedFiles: number;
  errorMessage?: string;
  type: 'full' | 'incremental' | 'differential';
}

export class BackupStateCRDT {
  private doc: Y.Doc;
  private files: Y.Map<FileMetadata>;
  private chunks: Y.Map<ChunkMetadata>;
  private devices: Y.Map<DeviceState>;
  private jobs: Y.Map<BackupJob>;
  private syncLog: Y.Array<any>;

  constructor() {
    this.doc = new Y.Doc();
    this.files = this.doc.getMap('files');
    this.chunks = this.doc.getMap('chunks');
    this.devices = this.doc.getMap('devices');
    this.jobs = this.doc.getMap('jobs');
    this.syncLog = this.doc.getArray('syncLog');

    this.setupObservers();
  }

  private setupObservers() {
    this.files.observe((event) => {
      this.logChange('files', event);
    });

    this.chunks.observe((event) => {
      this.logChange('chunks', event);
    });

    this.devices.observe((event) => {
      this.logChange('devices', event);
    });

    this.jobs.observe((event) => {
      this.logChange('jobs', event);
    });
  }

  private logChange(type: string, event: any) {
    const logEntry = {
      type,
      timestamp: Date.now(),
      changes: event.changes,
      id: uuidv4()
    };
    
    this.syncLog.push([logEntry]);
    
    // Keep only last 1000 log entries
    if (this.syncLog.length > 1000) {
      this.syncLog.delete(0, this.syncLog.length - 1000);
    }
  }

  // File operations
  addFile(file: FileMetadata): void {
    this.files.set(file.path, file);
  }

  removeFile(filePath: string): void {
    const file = this.files.get(filePath);
    if (file) {
      this.files.set(filePath, { ...file, isDeleted: true });
    }
  }

  updateFile(filePath: string, updates: Partial<FileMetadata>): void {
    const existing = this.files.get(filePath);
    if (existing) {
      this.files.set(filePath, { ...existing, ...updates });
    }
  }

  getFile(filePath: string): FileMetadata | undefined {
    return this.files.get(filePath);
  }

  getAllFiles(): Map<string, FileMetadata> {
    return new Map(this.files.entries());
  }

  getFilesByDevice(deviceId: string): FileMetadata[] {
    return Array.from(this.files.values()).filter(
      file => file.deviceId === deviceId && !file.isDeleted
    );
  }

  // Chunk operations
  addChunk(chunk: ChunkMetadata): void {
    this.chunks.set(chunk.id, chunk);
  }

  updateChunk(chunkId: string, updates: Partial<ChunkMetadata>): void {
    const existing = this.chunks.get(chunkId);
    if (existing) {
      this.chunks.set(chunkId, { ...existing, ...updates });
    }
  }

  getChunk(chunkId: string): ChunkMetadata | undefined {
    return this.chunks.get(chunkId);
  }

  getAllChunks(): Map<string, ChunkMetadata> {
    return new Map(this.chunks.entries());
  }

  getChunksByFile(filePath: string): ChunkMetadata[] {
    const file = this.getFile(filePath);
    if (!file) return [];
    
    return file.chunks.map(chunkId => this.chunks.get(chunkId))
                     .filter(chunk => chunk !== undefined) as ChunkMetadata[];
  }

  // Device operations
  registerDevice(device: DeviceState): void {
    this.devices.set(device.deviceId, device);
  }

  updateDeviceState(deviceId: string, updates: Partial<DeviceState>): void {
    const existing = this.devices.get(deviceId);
    if (existing) {
      this.devices.set(deviceId, { 
        ...existing, 
        ...updates, 
        lastSync: Date.now() 
      });
    }
  }

  getDevice(deviceId: string): DeviceState | undefined {
    return this.devices.get(deviceId);
  }

  getAllDevices(): Map<string, DeviceState> {
    return new Map(this.devices.entries());
  }

  getOnlineDevices(): DeviceState[] {
    return Array.from(this.devices.values()).filter(device => device.isOnline);
  }

  // Job operations
  createJob(job: BackupJob): void {
    this.jobs.set(job.id, job);
  }

  updateJob(jobId: string, updates: Partial<BackupJob>): void {
    const existing = this.jobs.get(jobId);
    if (existing) {
      this.jobs.set(jobId, { ...existing, ...updates });
    }
  }

  getJob(jobId: string): BackupJob | undefined {
    return this.jobs.get(jobId);
  }

  getAllJobs(): Map<string, BackupJob> {
    return new Map(this.jobs.entries());
  }

  getJobsByDevice(deviceId: string): BackupJob[] {
    return Array.from(this.jobs.values()).filter(
      job => job.deviceId === deviceId
    );
  }

  getActiveJobs(): BackupJob[] {
    return Array.from(this.jobs.values()).filter(
      job => job.status === 'running' || job.status === 'pending'
    );
  }

  // Sync operations
  getSyncLog(): any[] {
    return this.syncLog.toArray();
  }

  getRecentChanges(since: number): any[] {
    return this.syncLog.toArray().filter(
      entry => entry[0].timestamp > since
    );
  }

  // Statistics
  getStats() {
    const devices = Array.from(this.devices.values());
    const files = Array.from(this.files.values()).filter(f => !f.isDeleted);
    const chunks = Array.from(this.chunks.values());
    const jobs = Array.from(this.jobs.values());

    return {
      totalDevices: devices.length,
      onlineDevices: devices.filter(d => d.isOnline).length,
      totalFiles: files.length,
      totalSize: files.reduce((sum, f) => sum + f.size, 0),
      totalChunks: chunks.length,
      activeJobs: jobs.filter(j => j.status === 'running').length,
      completedJobs: jobs.filter(j => j.status === 'completed').length,
      failedJobs: jobs.filter(j => j.status === 'failed').length,
      lastActivity: Math.max(
        ...devices.map(d => d.lastSync),
        ...files.map(f => f.backupTime)
      )
    };
  }

  // Conflict resolution
  resolveFileConflict(filePath: string, devicePriority: string[]): FileMetadata | null {
    const file = this.getFile(filePath);
    if (!file) return null;

    // Simple conflict resolution: prefer file from higher priority device
    // In a real implementation, you might compare timestamps, hashes, etc.
    const filesByDevice = Array.from(this.files.values())
      .filter(f => f.path === filePath && !f.isDeleted);

    if (filesByDevice.length <= 1) return file;

    // Sort by device priority, then by timestamp
    filesByDevice.sort((a, b) => {
      const priorityA = devicePriority.indexOf(a.deviceId);
      const priorityB = devicePriority.indexOf(b.deviceId);
      
      if (priorityA !== priorityB) {
        return priorityA - priorityB;
      }
      
      return b.backupTime - a.backupTime;
    });

    return filesByDevice[0];
  }

  // Export/Import for persistence
  exportState(): Uint8Array {
    return Y.encodeStateAsUpdate(this.doc);
  }

  importState(update: Uint8Array): void {
    Y.applyUpdate(this.doc, update);
  }

  // Get the underlying Y.Doc for WebSocket sync
  getDocument(): Y.Doc {
    return this.doc;
  }

  // Cleanup old data
  cleanup(olderThanDays: number = 30): void {
    const cutoffTime = Date.now() - (olderThanDays * 24 * 60 * 60 * 1000);

    // Remove old deleted files
    for (const [path, file] of this.files.entries()) {
      if (file.isDeleted && file.backupTime < cutoffTime) {
        this.files.delete(path);
      }
    }

    // Remove old completed/failed jobs
    for (const [jobId, job] of this.jobs.entries()) {
      if ((job.status === 'completed' || job.status === 'failed') && 
          (job.endTime || job.startTime) < cutoffTime) {
        this.jobs.delete(jobId);
      }
    }

    // Clean up orphaned chunks
    const referencedChunks = new Set<string>();
    for (const file of this.files.values()) {
      if (!file.isDeleted) {
        file.chunks.forEach(chunkId => referencedChunks.add(chunkId));
      }
    }

    for (const [chunkId, chunk] of this.chunks.entries()) {
      if (!referencedChunks.has(chunkId) && chunk.createdTime < cutoffTime) {
        this.chunks.delete(chunkId);
      }
    }
  }
}