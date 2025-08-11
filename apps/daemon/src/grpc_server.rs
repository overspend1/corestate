use crate::android_bridge::AndroidBridge;
use crate::backup::BackupManager;
use crate::config::DaemonConfig;
use crate::filesystem::FileSystemMonitor;
use crate::kernel_interface::KernelInterface;
use std::sync::Arc;
use tokio::sync::RwLock;

pub struct GrpcServer;

impl GrpcServer {
    pub async fn new(
        _config: &Arc<DaemonConfig>,
        _backup: Arc<RwLock<BackupManager>>,
        _fs: Arc<RwLock<FileSystemMonitor>>,
        _android: Arc<AndroidBridge>,
        _kernel: Arc<KernelInterface>,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        Ok(Self)
    }

    pub async fn serve(&self) -> Result<(), Box<dyn std::error::Error>> {
        Ok(())
    }
}

