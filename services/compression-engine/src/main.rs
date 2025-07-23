use anyhow::Result;
use std::env;
use tokio::signal;
use tracing::{info, warn};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

mod compression;
mod config;
mod metrics;
mod server;

use crate::config::Config;
use crate::server::CompressionServer;

#[tokio::main]
async fn main() -> Result<()> {
    // Initialize tracing
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "compression_engine=info".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    // Check for health check flag
    let args: Vec<String> = env::args().collect();
    if args.len() > 1 && args[1] == "--health-check" {
        return health_check().await;
    }

    info!("Starting CoreState Compression Engine v2.0.0");

    // Load configuration
    let config = Config::load()?;
    info!("Configuration loaded successfully");

    // Initialize metrics
    metrics::init_metrics();

    // Start the compression server
    let server = CompressionServer::new(config).await?;
    let server_task = tokio::spawn(async move {
        if let Err(e) = server.serve().await {
            warn!("Server error: {}", e);
        }
    });

    // Wait for shutdown signal
    tokio::select! {
        _ = signal::ctrl_c() => {
            info!("Received shutdown signal");
        }
        _ = server_task => {
            info!("Server task completed");
        }
    }

    info!("Compression Engine shutting down");
    Ok(())
}

async fn health_check() -> Result<()> {
    // Simple health check - verify the service can start
    let config = Config::load()?;
    info!("Health check: Configuration loaded successfully");
    
    // Test compression functionality
    let test_data = b"Hello, World! This is a test compression string.";
    let compressed = compression::compress_data(test_data, compression::CompressionType::Zstd)?;
    let decompressed = compression::decompress_data(&compressed, compression::CompressionType::Zstd)?;
    
    if test_data == decompressed.as_slice() {
        info!("Health check: Compression/decompression test passed");
        std::process::exit(0);
    } else {
        warn!("Health check: Compression/decompression test failed");
        std::process::exit(1);
    }
}