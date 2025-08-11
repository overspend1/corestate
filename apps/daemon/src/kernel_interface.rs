use crate::config::DaemonConfig;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tokio::fs;
use tokio::process::Command;
use tracing::warn;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KernelStatus {
    pub loaded: bool,
    pub version: String,
    pub features: Vec<String>,
}

pub struct KernelInterface {
    config: Arc<DaemonConfig>,
}

impl KernelInterface {
    pub async fn new(config: &Arc<DaemonConfig>) -> Result<Self, Box<dyn std::error::Error>> {
        Ok(Self {
            config: config.clone(),
        })
    }

    pub async fn is_loaded(&self) -> bool {
        fs::metadata("/proc/corestate").await.is_ok()
    }

    pub async fn load(&self) -> Result<(), Box<dyn std::error::Error>> {
        let module_path = self.config.kernel.module_path.clone();
        let cmd = format!("insmod {}", module_path.display());
        let status = Command::new("su").arg("-c").arg(&cmd).status().await;
        if let Err(e) = status {
            warn!("Failed to load kernel module: {}", e);
        }
        Ok(())
    }

    pub async fn unload(&self) -> Result<(), Box<dyn std::error::Error>> {
        let status = Command::new("su").arg("-c").arg("rmmod corestate").status().await;
        if let Err(e) = status {
            warn!("Failed to unload kernel module: {}", e);
        }
        Ok(())
    }

    pub async fn get_status(&self) -> KernelStatus {
        let loaded = self.is_loaded().await;
        let version = if loaded {
            "2.0.0".to_string()
        } else {
            "unloaded".to_string()
        };
        let mut features = Vec::new();
        if self.config.kernel.cow_enabled {
            features.push("cow".to_string());
        }
        if self.config.kernel.snapshot_enabled {
            features.push("snapshots".to_string());
        }
        KernelStatus {
            loaded,
            version,
            features,
        }
    }
}

