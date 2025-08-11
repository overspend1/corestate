use crate::android_bridge::BackupOptions;
use crate::config::DaemonConfig;
use crate::filesystem::FileSystemMonitor;
use std::sync::Arc;
use tokio::sync::RwLock;

pub struct BackupManager {
    _config: Arc<DaemonConfig>,
    _fs_monitor: Arc<RwLock<FileSystemMonitor>>,
}

impl BackupManager {
    pub async fn new(config: &Arc<DaemonConfig>, fs_monitor: Arc<RwLock<FileSystemMonitor>>) -> Result<Self, Box<dyn std::error::Error>> {
        Ok(Self {
            _config: config.clone(),
            _fs_monitor: fs_monitor,
        })
    }

    pub async fn start(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        Ok(())
    }

    pub async fn start_backup(&self, _paths: Vec<String>, _options: BackupOptions) -> Result<String, Box<dyn std::error::Error>> {
        Ok(uuid::Uuid::new_v4().to_string())
    }

    pub async fn get_active_job_count(&self) -> u32 {
        0
    }

    pub async fn get_total_files_backed_up(&self) -> u64 {
        0
    }

    pub async fn get_total_backup_size(&self) -> u64 {
        0
    }
}

