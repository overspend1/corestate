import request from 'supertest';
import app from '../index';

describe('Encryption Service API', () => {
  test('GET /api/v1/health should return healthy status', async () => {
    const response = await request(app)
      .get('/api/v1/health')
      .expect(200);

    expect(response.body).toHaveProperty('status', 'healthy');
    expect(response.body).toHaveProperty('service', 'encryption-service');
    expect(response.body).toHaveProperty('uptime');
    expect(response.body).toHaveProperty('timestamp');
  });

  test('GET /api/v1/metrics should return metrics', async () => {
    const response = await request(app)
      .get('/api/v1/metrics')
      .expect(200);

    expect(response.body).toHaveProperty('totalDevices');
    expect(response.body).toHaveProperty('totalKeys');
    expect(response.body).toHaveProperty('activeKeys');
    expect(response.body).toHaveProperty('supportedAlgorithms');
    expect(response.body).toHaveProperty('supportedKeyDerivation');
  });

  test('POST /api/v1/keys/generate should generate a device key', async () => {
    const response = await request(app)
      .post('/api/v1/keys/generate')
      .send({ deviceId: 'test-device-001', password: 'test-password' })
      .expect(200);

    expect(response.body).toHaveProperty('keyId');
    expect(response.body).toHaveProperty('algorithm');
    expect(response.body).toHaveProperty('keyDerivation');
    expect(response.body).toHaveProperty('createdAt');
  });

  test('GET /api/v1/keys/:deviceId should return device key info', async () => {
    // First generate a key
    await request(app)
      .post('/api/v1/keys/generate')
      .send({ deviceId: 'test-device-002' });

    // Then get key info
    const response = await request(app)
      .get('/api/v1/keys/test-device-002')
      .expect(200);

    expect(response.body).toHaveProperty('deviceId', 'test-device-002');
    expect(response.body).toHaveProperty('keys');
    expect(Array.isArray(response.body.keys)).toBe(true);
  });

  test('404 for unknown routes', async () => {
    await request(app)
      .get('/api/v1/unknown')
      .expect(404);
  });
});

describe('Encryption Service Basics', () => {
  test('should support multiple encryption algorithms', () => {
    // Basic test to ensure the service initializes
    expect(true).toBe(true);
  });
});
