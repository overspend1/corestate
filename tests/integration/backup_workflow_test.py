#!/usr/bin/env python3
"""
Integration test for complete backup workflow
Tests the entire backup pipeline from file selection to storage
"""

import pytest
import asyncio
import requests
import time
from typing import Dict, Any

BASE_URL = "http://localhost:8080"
COMPRESSION_URL = "http://localhost:8084"
ENCRYPTION_URL = "http://localhost:3000"
DEDUP_URL = "http://localhost:8001"
STORAGE_URL = "http://localhost:8083"

@pytest.fixture
def test_backup_job() -> Dict[str, Any]:
    """Create a test backup job"""
    return {
        "deviceId": "test-device-001",
        "paths": ["/test/data/files"],
        "options": {
            "compressionAlgorithm": "zstd",
            "encryptionEnabled": True,
            "deduplicationEnabled": True,
            "retentionDays": 30
        }
    }

class TestBackupWorkflow:
    """Test complete backup workflow"""
    
    def test_service_health_checks(self):
        """Verify all services are healthy"""
        services = [
            ("Backup Engine", f"{BASE_URL}/actuator/health"),
            ("Compression Engine", f"{COMPRESSION_URL}/health"),
            ("Encryption Service", f"{ENCRYPTION_URL}/health"),
            ("Deduplication Service", f"{DEDUP_URL}/health"),
            ("Storage HAL", f"{STORAGE_URL}/health"),
        ]
        
        for service_name, health_url in services:
            response = requests.get(health_url, timeout=5)
            assert response.status_code == 200, f"{service_name} is not healthy"
            print(f"✓ {service_name} is healthy")
    
    def test_create_backup_job(self, test_backup_job):
        """Test creating a backup job"""
        response = requests.post(
            f"{BASE_URL}/api/backups",
            json=test_backup_job,
            timeout=10
        )
        
        assert response.status_code in [200, 201], "Failed to create backup job"
        data = response.json()
        assert "jobId" in data or "id" in data
        job_id = data.get("jobId") or data.get("id")
        print(f"✓ Created backup job: {job_id}")
        return job_id
    
    def test_backup_job_progress(self):
        """Test monitoring backup job progress"""
        # Create a job first
        job_id = self.test_create_backup_job(test_backup_job())
        
        # Poll for progress
        max_attempts = 30
        for attempt in range(max_attempts):
            response = requests.get(
                f"{BASE_URL}/api/backups/{job_id}/progress",
                timeout=5
            )
            
            if response.status_code == 200:
                progress = response.json()
                print(f"  Progress: {progress.get('progress', 0)}%")
                
                if progress.get("progress") >= 100:
                    print("✓ Backup job completed")
                    return
            
            time.sleep(2)
        
        pytest.fail("Backup job did not complete in time")
    
    def test_compression_service(self):
        """Test compression service directly"""
        test_data = b"Hello World! " * 100
        
        response = requests.post(
            f"{COMPRESSION_URL}/api/compress",
            json={
                "algorithm": "zstd",
                "level": 3,
                "data": test_data.hex()
            },
            timeout=10
        )
        
        assert response.status_code == 200
        result = response.json()
        assert "compressedData" in result
        assert result["originalSize"] > result["compressedSize"]
        print(f"✓ Compression: {result['originalSize']} → {result['compressedSize']} bytes")
    
    def test_deduplication_service(self):
        """Test deduplication service"""
        test_chunk = "chunk_data_" + ("x" * 1000)
        
        # First chunk should be new
        response1 = requests.post(
            f"{DEDUP_URL}/api/chunk",
            json={"data": test_chunk},
            timeout=10
        )
        assert response1.status_code == 200
        result1 = response1.json()
        assert result1.get("isDuplicate") == False
        chunk_id = result1.get("chunkId")
        
        # Second identical chunk should be deduplicated
        response2 = requests.post(
            f"{DEDUP_URL}/api/chunk",
            json={"data": test_chunk},
            timeout=10
        )
        assert response2.status_code == 200
        result2 = response2.json()
        assert result2.get("isDuplicate") == True
        assert result2.get("chunkId") == chunk_id
        print(f"✓ Deduplication working: chunk {chunk_id}")
    
    def test_encryption_service(self):
        """Test encryption service"""
        test_data = "sensitive data"
        
        # Encrypt
        encrypt_response = requests.post(
            f"{ENCRYPTION_URL}/api/encrypt",
            json={
                "data": test_data,
                "algorithm": "aes-256-gcm"
            },
            timeout=10
        )
        assert encrypt_response.status_code == 200
        encrypted = encrypt_response.json()
        assert "encryptedData" in encrypted
        assert "keyId" in encrypted
        
        # Decrypt
        decrypt_response = requests.post(
            f"{ENCRYPTION_URL}/api/decrypt",
            json={
                "encryptedData": encrypted["encryptedData"],
                "keyId": encrypted["keyId"]
            },
            timeout=10
        )
        assert decrypt_response.status_code == 200
        decrypted = decrypt_response.json()
        assert decrypted.get("data") == test_data
        print("✓ Encryption/Decryption working")
    
    def test_storage_hal(self):
        """Test storage HAL service"""
        test_data = b"test storage data" * 100
        
        # Store data
        store_response = requests.post(
            f"{STORAGE_URL}/api/store",
            json={
                "data": test_data.hex(),
                "metadata": {"type": "test"}
            },
            timeout=10
        )
        assert store_response.status_code == 200
        result = store_response.json()
        assert "storageId" in result
        storage_id = result["storageId"]
        
        # Retrieve data
        retrieve_response = requests.get(
            f"{STORAGE_URL}/api/retrieve/{storage_id}",
            timeout=10
        )
        assert retrieve_response.status_code == 200
        retrieved = retrieve_response.json()
        assert bytes.fromhex(retrieved["data"]) == test_data
        print(f"✓ Storage HAL working: {storage_id}")
    
    def test_end_to_end_backup_restore(self, test_backup_job):
        """Test complete backup and restore workflow"""
        # Create backup
        backup_response = requests.post(
            f"{BASE_URL}/api/backups",
            json=test_backup_job,
            timeout=10
        )
        assert backup_response.status_code in [200, 201]
        job_id = backup_response.json().get("jobId") or backup_response.json().get("id")
        
        # Wait for completion
        time.sleep(5)
        
        # List backups
        list_response = requests.get(
            f"{BASE_URL}/api/backups?deviceId={test_backup_job['deviceId']}",
            timeout=10
        )
        assert list_response.status_code == 200
        backups = list_response.json()
        assert len(backups) > 0
        
        # Initiate restore
        restore_response = requests.post(
            f"{BASE_URL}/api/restore",
            json={
                "backupId": job_id,
                "targetPath": "/test/restore",
                "files": ["*"]
            },
            timeout=10
        )
        
        # Should return success or in-progress status
        assert restore_response.status_code in [200, 201, 202]
        print("✓ End-to-end backup and restore initiated successfully")

if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
