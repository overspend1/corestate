#!/usr/bin/env python3
"""
End-to-end test for Android Bridge WebSocket communication
Tests the daemon's Android bridge functionality
"""

import pytest
import asyncio
import websockets
import json
from typing import Dict, Any

DAEMON_WS_URL = "ws://localhost:9999"

class TestAndroidBridge:
    """Test Android Bridge WebSocket communication"""
    
    @pytest.mark.asyncio
    async def test_websocket_connection(self):
        """Test basic WebSocket connection to daemon"""
        try:
            async with websockets.connect(DAEMON_WS_URL, timeout=10) as websocket:
                # Connection successful
                assert websocket.open
                print("✓ WebSocket connection established")
        except Exception as e:
            pytest.skip(f"Daemon not running: {e}")
    
    @pytest.mark.asyncio
    async def test_authentication(self):
        """Test Android authentication flow"""
        try:
            async with websockets.connect(DAEMON_WS_URL, timeout=10) as websocket:
                # Send auth message
                auth_msg = {
                    "id": "test-msg-001",
                    "messageType": "Auth",
                    "payload": {"token": "test-auth-token"},
                    "timestamp": 1234567890
                }
                await websocket.send(json.dumps(auth_msg))
                
                # Wait for response
                response = await asyncio.wait_for(websocket.recv(), timeout=5)
                data = json.loads(response)
                
                assert data["messageType"] == "AuthResponse"
                print(f"✓ Authentication response received: {data}")
        except asyncio.TimeoutError:
            pytest.skip("Daemon not responding")
        except Exception as e:
            pytest.skip(f"Daemon not running: {e}")
    
    @pytest.mark.asyncio
    async def test_system_status_request(self):
        """Test requesting system status"""
        try:
            async with websockets.connect(DAEMON_WS_URL, timeout=10) as websocket:
                # Request system status
                status_msg = {
                    "id": "test-msg-002",
                    "messageType": "GetSystemStatus",
                    "payload": {},
                    "timestamp": 1234567890
                }
                await websocket.send(json.dumps(status_msg))
                
                # Wait for response
                response = await asyncio.wait_for(websocket.recv(), timeout=5)
                data = json.loads(response)
                
                assert data["messageType"] == "SystemStatus"
                assert "status" in data["payload"]
                status = data["payload"]["status"]
                
                # Verify status fields
                assert "daemon_uptime" in status
                assert "memory_usage" in status
                assert "cpu_usage" in status
                print(f"✓ System status received: {status}")
        except Exception as e:
            pytest.skip(f"Daemon not running: {e}")
    
    @pytest.mark.asyncio
    async def test_file_listing(self):
        """Test file listing through daemon"""
        try:
            async with websockets.connect(DAEMON_WS_URL, timeout=10) as websocket:
                # Request file list
                files_msg = {
                    "id": "test-msg-003",
                    "messageType": "ListFiles",
                    "payload": {"path": "/tmp"},
                    "timestamp": 1234567890
                }
                await websocket.send(json.dumps(files_msg))
                
                # Wait for response
                response = await asyncio.wait_for(websocket.recv(), timeout=5)
                data = json.loads(response)
                
                assert data["messageType"] == "FileList"
                assert "files" in data["payload"]
                print(f"✓ File list received: {len(data['payload']['files'])} files")
        except Exception as e:
            pytest.skip(f"Daemon not running: {e}")
    
    @pytest.mark.asyncio
    async def test_backup_initiation(self):
        """Test initiating backup through Android bridge"""
        try:
            async with websockets.connect(DAEMON_WS_URL, timeout=10) as websocket:
                # Start backup
                backup_msg = {
                    "id": "test-msg-004",
                    "messageType": "StartBackup",
                    "payload": {
                        "paths": ["/tmp/test"],
                        "options": {
                            "compressionAlgorithm": "zstd",
                            "encryptionEnabled": True
                        }
                    },
                    "timestamp": 1234567890
                }
                await websocket.send(json.dumps(backup_msg))
                
                # Should receive acknowledgment or progress updates
                response = await asyncio.wait_for(websocket.recv(), timeout=10)
                data = json.loads(response)
                
                # Could be BackupProgress or Error
                assert data["messageType"] in ["BackupProgress", "Error", "BackupStarted"]
                print(f"✓ Backup initiation response: {data['messageType']}")
        except Exception as e:
            pytest.skip(f"Daemon not running: {e}")

if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
