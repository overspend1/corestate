#!/usr/bin/env python3
"""
Performance and load testing for CoreState services
Tests system behavior under high load
"""

import asyncio
import aiohttp
import time
import statistics
from typing import List, Dict
import pytest

BASE_URL = "http://localhost:8080"

class TestPerformance:
    """Performance and load tests"""
    
    async def make_request(self, session: aiohttp.ClientSession, url: str) -> Dict:
        """Make a single request and measure time"""
        start_time = time.time()
        try:
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=30)) as response:
                status = response.status
                await response.read()
                elapsed = time.time() - start_time
                return {"success": status == 200, "time": elapsed, "status": status}
        except Exception as e:
            elapsed = time.time() - start_time
            return {"success": False, "time": elapsed, "error": str(e)}
    
    @pytest.mark.asyncio
    async def test_concurrent_health_checks(self):
        """Test concurrent health check requests"""
        num_requests = 100
        url = f"{BASE_URL}/actuator/health"
        
        async with aiohttp.ClientSession() as session:
            tasks = [self.make_request(session, url) for _ in range(num_requests)]
            results = await asyncio.gather(*tasks)
        
        # Analyze results
        successful = [r for r in results if r["success"]]
        times = [r["time"] for r in successful]
        
        assert len(successful) >= num_requests * 0.95, "Success rate below 95%"
        
        avg_time = statistics.mean(times)
        p95_time = statistics.quantiles(times, n=20)[18]  # 95th percentile
        p99_time = statistics.quantiles(times, n=100)[98]  # 99th percentile
        
        print(f"""
Performance Results (n={num_requests}):
  Success Rate: {len(successful)/num_requests*100:.1f}%
  Average Time: {avg_time*1000:.2f}ms
  P95 Time: {p95_time*1000:.2f}ms
  P99 Time: {p99_time*1000:.2f}ms
  Min Time: {min(times)*1000:.2f}ms
  Max Time: {max(times)*1000:.2f}ms
        """)
        
        # Performance assertions
        assert avg_time < 0.5, "Average response time too high"
        assert p95_time < 1.0, "P95 response time too high"
    
    @pytest.mark.asyncio
    async def test_backup_creation_load(self):
        """Test creating multiple backup jobs concurrently"""
        num_jobs = 50
        url = f"{BASE_URL}/api/backups"
        
        async def create_backup(session: aiohttp.ClientSession, index: int):
            start_time = time.time()
            payload = {
                "deviceId": f"test-device-{index}",
                "paths": [f"/test/data/{index}"],
                "options": {
                    "compressionAlgorithm": "zstd",
                    "encryptionEnabled": True
                }
            }
            try:
                async with session.post(url, json=payload, timeout=aiohttp.ClientTimeout(total=30)) as response:
                    status = response.status
                    elapsed = time.time() - start_time
                    return {"success": status in [200, 201], "time": elapsed, "status": status}
            except Exception as e:
                elapsed = time.time() - start_time
                return {"success": False, "time": elapsed, "error": str(e)}
        
        async with aiohttp.ClientSession() as session:
            tasks = [create_backup(session, i) for i in range(num_jobs)]
            results = await asyncio.gather(*tasks)
        
        successful = [r for r in results if r["success"]]
        times = [r["time"] for r in successful]
        
        success_rate = len(successful) / num_jobs * 100
        
        print(f"""
Backup Creation Load Test (n={num_jobs}):
  Success Rate: {success_rate:.1f}%
  Average Time: {statistics.mean(times)*1000:.2f}ms
  P95 Time: {statistics.quantiles(times, n=20)[18]*1000:.2f}ms
        """)
        
        assert success_rate >= 80, "Success rate too low under load"
    
    @pytest.mark.asyncio
    async def test_compression_throughput(self):
        """Test compression service throughput"""
        compression_url = "http://localhost:8084/api/compress"
        num_requests = 20
        test_data = "x" * 10000  # 10KB of data
        
        async def compress_data(session: aiohttp.ClientSession):
            start_time = time.time()
            payload = {
                "algorithm": "zstd",
                "level": 3,
                "data": test_data
            }
            try:
                async with session.post(compression_url, json=payload, timeout=aiohttp.ClientTimeout(total=30)) as response:
                    await response.read()
                    elapsed = time.time() - start_time
                    return {"success": response.status == 200, "time": elapsed}
            except Exception as e:
                return {"success": False, "time": 0, "error": str(e)}
        
        async with aiohttp.ClientSession() as session:
            tasks = [compress_data(session) for _ in range(num_requests)]
            results = await asyncio.gather(*tasks)
        
        successful = [r for r in results if r["success"]]
        if successful:
            total_time = sum(r["time"] for r in successful)
            total_data = len(test_data) * len(successful)
            throughput_mbps = (total_data / total_time) / (1024 * 1024)
            
            print(f"""
Compression Throughput Test:
  Requests: {len(successful)}/{num_requests}
  Total Data: {total_data / (1024 * 1024):.2f} MB
  Total Time: {total_time:.2f}s
  Throughput: {throughput_mbps:.2f} MB/s
            """)
            
            assert throughput_mbps > 1.0, "Compression throughput too low"
    
    @pytest.mark.asyncio
    async def test_sustained_load(self):
        """Test system under sustained load"""
        duration_seconds = 30
        requests_per_second = 10
        url = f"{BASE_URL}/actuator/health"
        
        results = []
        start_time = time.time()
        
        async with aiohttp.ClientSession() as session:
            while time.time() - start_time < duration_seconds:
                iteration_start = time.time()
                
                # Send requests
                tasks = [self.make_request(session, url) for _ in range(requests_per_second)]
                batch_results = await asyncio.gather(*tasks)
                results.extend(batch_results)
                
                # Wait for next second
                elapsed = time.time() - iteration_start
                if elapsed < 1.0:
                    await asyncio.sleep(1.0 - elapsed)
        
        # Analyze sustained load results
        successful = [r for r in results if r["success"]]
        success_rate = len(successful) / len(results) * 100
        times = [r["time"] for r in successful]
        
        print(f"""
Sustained Load Test ({duration_seconds}s @ {requests_per_second} req/s):
  Total Requests: {len(results)}
  Success Rate: {success_rate:.1f}%
  Average Response Time: {statistics.mean(times)*1000:.2f}ms
  P95 Response Time: {statistics.quantiles(times, n=20)[18]*1000:.2f}ms
        """)
        
        assert success_rate >= 95, "Success rate degraded under sustained load"

if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
