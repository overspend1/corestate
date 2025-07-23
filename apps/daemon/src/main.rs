use tokio;
use tracing::{info, error, warn, debug};
use tracing_subscriber;
use std::sync::Arc;
use tokio::sync::RwLock;

mod backup;
mod filesystem;
mod grpc_server;
mod android_bridge;
mod config;
mod kernel_interface;

use crate::config::DaemonConfig;
use crate::grpc_server::GrpcServer;
use crate::android_bridge::AndroidBridge;
use crate::filesystem::FileSystemMonitor;
use crate::backup::BackupManager;
use crate::kernel_interface::KernelInterface;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize tracing
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .with_target(false)
        .init();

    info!("CoreState Daemon v2.0 starting...");

    // Load configuration
    let config = Arc::new(DaemonConfig::load().await?);
    info!("Configuration loaded successfully");

    // Initialize kernel interface
    let kernel_interface = Arc::new(KernelInterface::new(&config).await?);
    info!("Kernel interface initialized");

    // Initialize file system monitor
    let fs_monitor = Arc::new(RwLock::new(
        FileSystemMonitor::new(&config, kernel_interface.clone()).await?
    ));
    info!("File system monitor initialized");

    // Initialize backup manager
    let backup_manager = Arc::new(RwLock::new(
        BackupManager::new(&config, fs_monitor.clone()).await?
    ));
    info!("Backup manager initialized");

    // Initialize Android bridge
    let android_bridge = Arc::new(AndroidBridge::new(
        &config,
        backup_manager.clone(),
        fs_monitor.clone(),
        kernel_interface.clone()
    ).await?);
    info!("Android bridge initialized");

    // Initialize gRPC server
    let grpc_server = GrpcServer::new(
        &config,
        backup_manager.clone(),
        fs_monitor.clone(),
        android_bridge.clone(),
        kernel_interface.clone()
    ).await?;
    info!("gRPC server initialized");

    // Start all services concurrently
    let fs_monitor_handle = {
        let fs_monitor = fs_monitor.clone();
        tokio::spawn(async move {
            if let Err(e) = fs_monitor.write().await.start().await {
                error!("File system monitor error: {}", e);
            }
        })
    };

    let backup_manager_handle = {
        let backup_manager = backup_manager.clone();
        tokio::spawn(async move {
            if let Err(e) = backup_manager.write().await.start().await {
                error!("Backup manager error: {}", e);
            }
        })
    };

    let android_bridge_handle = {
        let android_bridge = android_bridge.clone();
        tokio::spawn(async move {
            if let Err(e) = android_bridge.start().await {
                error!("Android bridge error: {}", e);
            }
        })
    };

    let grpc_server_handle = tokio::spawn(async move {
        if let Err(e) = grpc_server.serve().await {
            error!("gRPC server error: {}", e);
        }
    });

    info!("All services started successfully");
    
    // Handle graceful shutdown
    tokio::select! {
        _ = tokio::signal::ctrl_c() => {
            info!("Received shutdown signal");
        }
        result = fs_monitor_handle => {
            if let Err(e) = result {
                error!("File system monitor task failed: {}", e);
            }
        }
        result = backup_manager_handle => {
            if let Err(e) = result {
                error!("Backup manager task failed: {}", e);
            }
        }
        result = android_bridge_handle => {
            if let Err(e) = result {
                error!("Android bridge task failed: {}", e);
            }
        }
        result = grpc_server_handle => {
            if let Err(e) = result {
                error!("gRPC server task failed: {}", e);
            }
        }
    }

    info!("CoreState Daemon shutting down...");
    Ok(())
}