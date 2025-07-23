#!/usr/bin/env python3
"""
CoreState Deduplication Service
High-performance data deduplication service using content-defined chunking
and multiple hashing algorithms for optimal storage efficiency.
"""

import asyncio
import logging
import signal
import sys
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI, HTTPException, BackgroundTasks
from prometheus_client import make_asgi_app
import structlog

from deduplication import DeduplicationEngine
from models import ChunkRequest, ChunkResponse, DeduplicationStats
from config import Settings


# Configure structured logging
structlog.configure(
    processors=[
        structlog.stdlib.filter_by_level,
        structlog.stdlib.add_logger_name,
        structlog.stdlib.add_log_level,
        structlog.stdlib.PositionalArgumentsFormatter(),
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
        structlog.processors.UnicodeDecoder(),
        structlog.processors.JSONRenderer()
    ],
    context_class=dict,
    logger_factory=structlog.stdlib.LoggerFactory(),
    wrapper_class=structlog.stdlib.BoundLogger,
    cache_logger_on_first_use=True,
)

logger = structlog.get_logger()


# Global deduplication engine
dedup_engine = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan manager"""
    global dedup_engine
    
    logger.info("Starting CoreState Deduplication Service")
    
    # Initialize deduplication engine
    settings = Settings()
    dedup_engine = DeduplicationEngine(settings)
    await dedup_engine.initialize()
    
    # Start background tasks
    cleanup_task = asyncio.create_task(dedup_engine.cleanup_expired_chunks())
    
    yield
    
    # Cleanup on shutdown
    logger.info("Shutting down Deduplication Service")
    cleanup_task.cancel()
    await dedup_engine.close()


# Create FastAPI app
app = FastAPI(
    title="CoreState Deduplication Service",
    description="High-performance data deduplication service",
    version="2.0.0",
    lifespan=lifespan
)

# Add Prometheus metrics endpoint
metrics_app = make_asgi_app()
app.mount("/metrics", metrics_app)


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    try:
        stats = await dedup_engine.get_stats()
        return {
            "status": "healthy",
            "service": "deduplication-service",
            "version": "2.0.0",
            "stats": stats
        }
    except Exception as e:
        logger.error("Health check failed", error=str(e))
        raise HTTPException(status_code=503, detail="Service unhealthy")


@app.post("/deduplicate", response_model=ChunkResponse)
async def deduplicate_chunk(request: ChunkRequest, background_tasks: BackgroundTasks):
    """
    Process a data chunk for deduplication
    """
    try:
        logger.info("Processing chunk", chunk_id=request.chunk_id, size=len(request.data))
        
        result = await dedup_engine.process_chunk(
            chunk_id=request.chunk_id,
            data=request.data,
            metadata=request.metadata
        )
        
        # Schedule background cleanup if needed
        if result.is_duplicate:
            background_tasks.add_task(dedup_engine.update_reference_count, result.hash_value, 1)
        
        logger.info(
            "Chunk processed",
            chunk_id=request.chunk_id,
            is_duplicate=result.is_duplicate,
            hash_value=result.hash_value
        )
        
        return result
        
    except Exception as e:
        logger.error("Failed to process chunk", chunk_id=request.chunk_id, error=str(e))
        raise HTTPException(status_code=500, detail=f"Deduplication failed: {str(e)}")


@app.get("/chunk/{hash_value}")
async def get_chunk(hash_value: str):
    """
    Retrieve a chunk by its hash value
    """
    try:
        chunk_data = await dedup_engine.get_chunk(hash_value)
        if chunk_data is None:
            raise HTTPException(status_code=404, detail="Chunk not found")
        
        return {
            "hash_value": hash_value,
            "data": chunk_data,
            "size": len(chunk_data)
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Failed to retrieve chunk", hash_value=hash_value, error=str(e))
        raise HTTPException(status_code=500, detail=f"Retrieval failed: {str(e)}")


@app.delete("/chunk/{hash_value}")
async def delete_chunk(hash_value: str):
    """
    Delete a chunk and update reference counts
    """
    try:
        success = await dedup_engine.delete_chunk(hash_value)
        if not success:
            raise HTTPException(status_code=404, detail="Chunk not found")
        
        logger.info("Chunk deleted", hash_value=hash_value)
        return {"message": "Chunk deleted successfully"}
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Failed to delete chunk", hash_value=hash_value, error=str(e))
        raise HTTPException(status_code=500, detail=f"Deletion failed: {str(e)}")


@app.get("/stats", response_model=DeduplicationStats)
async def get_stats():
    """
    Get deduplication statistics
    """
    try:
        stats = await dedup_engine.get_stats()
        return stats
        
    except Exception as e:
        logger.error("Failed to get stats", error=str(e))
        raise HTTPException(status_code=500, detail=f"Stats retrieval failed: {str(e)}")


@app.post("/compact")
async def compact_storage(background_tasks: BackgroundTasks):
    """
    Trigger storage compaction to remove unreferenced chunks
    """
    try:
        background_tasks.add_task(dedup_engine.compact_storage)
        return {"message": "Compaction started"}
        
    except Exception as e:
        logger.error("Failed to start compaction", error=str(e))
        raise HTTPException(status_code=500, detail=f"Compaction failed: {str(e)}")


def signal_handler(signum, frame):
    """Handle shutdown signals"""
    logger.info("Received shutdown signal", signal=signum)
    sys.exit(0)


if __name__ == "__main__":
    # Register signal handlers
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    # Load settings
    settings = Settings()
    
    # Run the server
    uvicorn.run(
        "main:app",
        host=settings.host,
        port=settings.port,
        log_level=settings.log_level.lower(),
        access_log=True,
        reload=settings.debug
    )