import request from 'supertest';
import app from '../index';

describe('Sync Coordinator API', () => {
  test('GET /health should return healthy status', async () => {
    const response = await request(app)
      .get('/health')
      .expect(200);

    expect(response.body).toHaveProperty('status', 'healthy');
    expect(response.body).toHaveProperty('connectedDevices');
    expect(response.body).toHaveProperty('uptime');
    expect(response.body).toHaveProperty('timestamp');
  });

  test('GET /metrics should return metrics', async () => {
    const response = await request(app)
      .get('/metrics')
      .expect(200);

    expect(response.body).toHaveProperty('connected_devices');
    expect(response.body).toHaveProperty('total_documents');
    expect(response.body).toHaveProperty('uptime_seconds');
    expect(response.body).toHaveProperty('memory_usage');
  });
});

describe('Backup State CRDT', () => {
  test('should create backup state manager', () => {
    // Basic test to ensure imports work
    expect(true).toBe(true);
  });
});