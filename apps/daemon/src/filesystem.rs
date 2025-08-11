use crate::android_bridge::FileInfo;
use crate::config::DaemonConfig;
use crate::kernel_interface::KernelInterface;
use std::sync::Arc;

pub struct FileSystemMonitor {
    _config: Arc<DaemonConfig>,
    _kernel: Arc<KernelInterface>,
}

impl FileSystemMonitor {
    pub async fn new(config: &Arc<DaemonConfig>, kernel: Arc<KernelInterface>) -> Result<Self, Box<dyn std::error::Error>> {
        Ok(Self {
            _config: config.clone(),
            _kernel: kernel,
        })
    }

    pub async fn start(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        Ok(())
    }

    pub async fn list_files(&self, _path: &str) -> Result<Vec<FileInfo>, Box<dyn std::error::Error>> {
        Ok(Vec::new())
    }
}

