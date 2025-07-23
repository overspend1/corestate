use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use tokio::fs;
use tracing::{info, warn};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DaemonConfig {
    pub grpc: GrpcConfig,
    pub android: AndroidConfig,
    pub backup: BackupConfig,
    pub filesystem: FilesystemConfig,
    pub kernel: KernelConfig,
    pub logging: LoggingConfig,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GrpcConfig {
    pub host: String,
    pub port: u16,
    pub tls_enabled: bool,
    pub cert_path: Option<PathBuf>,
    pub key_path: Option<PathBuf>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AndroidConfig {
    pub bridge_port: u16,
    pub auth_token: String,
    pub max_connections: u16,
    pub heartbeat_interval: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BackupConfig {
    pub backup_root: PathBuf,
    pub chunk_size: usize,
    pub compression_level: u8,
    pub encryption: EncryptionConfig,
    pub retention: RetentionConfig,
    pub services: ServiceEndpoints,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EncryptionConfig {
    pub enabled: bool,
    pub algorithm: String,
    pub key_rotation_days: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RetentionConfig {
    pub full_backup_days: u32,
    pub incremental_backup_days: u32,
    pub max_versions: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServiceEndpoints {
    pub backup_engine: String,
    pub storage_hal: String,
    pub compression_engine: String,
    pub encryption_service: String,
    pub deduplication_service: String,
    pub ml_optimizer: String,
    pub sync_coordinator: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FilesystemConfig {
    pub watch_paths: Vec<PathBuf>,
    pub exclude_patterns: Vec<String>,
    pub scan_interval: u64,
    pub debounce_delay: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KernelConfig {
    pub module_enabled: bool,
    pub module_path: PathBuf,
    pub snapshot_enabled: bool,
    pub cow_enabled: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LoggingConfig {
    pub level: String,
    pub file_path: Option<PathBuf>,
    pub max_file_size: u64,
    pub max_files: u32,
}

impl Default for DaemonConfig {
    fn default() -> Self {
        Self {
            grpc: GrpcConfig {
                host: "127.0.0.1".to_string(),
                port: 50051,
                tls_enabled: false,
                cert_path: None,
                key_path: None,
            },
            android: AndroidConfig {
                bridge_port: 8080,
                auth_token: "default-token".to_string(),
                max_connections: 10,
                heartbeat_interval: 30,
            },
            backup: BackupConfig {
                backup_root: PathBuf::from("/data/backups"),
                chunk_size: 4 * 1024 * 1024, // 4MB
                compression_level: 6,
                encryption: EncryptionConfig {
                    enabled: true,
                    algorithm: "AES-256-GCM".to_string(),
                    key_rotation_days: 30,
                },
                retention: RetentionConfig {
                    full_backup_days: 30,
                    incremental_backup_days: 7,
                    max_versions: 10,
                },
                services: ServiceEndpoints {
                    backup_engine: "http://localhost:8001".to_string(),
                    storage_hal: "http://localhost:8002".to_string(),
                    compression_engine: "http://localhost:8003".to_string(),
                    encryption_service: "http://localhost:8004".to_string(),
                    deduplication_service: "http://localhost:8005".to_string(),
                    ml_optimizer: "http://localhost:8006".to_string(),
                    sync_coordinator: "http://localhost:8007".to_string(),
                },
            },
            filesystem: FilesystemConfig {
                watch_paths: vec![
                    PathBuf::from("/sdcard"),
                    PathBuf::from("/data/data"),
                ],
                exclude_patterns: vec![
                    "*.tmp".to_string(),
                    "*.log".to_string(),
                    ".cache/*".to_string(),
                    "node_modules/*".to_string(),
                ],
                scan_interval: 300, // 5 minutes
                debounce_delay: 5,  // 5 seconds
            },
            kernel: KernelConfig {
                module_enabled: true,
                module_path: PathBuf::from("/system/lib/modules/corestate.ko"),
                snapshot_enabled: true,
                cow_enabled: true,
            },
            logging: LoggingConfig {
                level: "info".to_string(),
                file_path: Some(PathBuf::from("/data/logs/daemon.log")),
                max_file_size: 10 * 1024 * 1024, // 10MB
                max_files: 5,
            },
        }
    }
}

impl DaemonConfig {
    pub async fn load() -> Result<Self, Box<dyn std::error::Error>> {
        let config_paths = vec![
            "/data/local/tmp/corestate/daemon.toml",
            "/system/etc/corestate/daemon.toml", 
            "./daemon.toml",
        ];

        for path in config_paths {
            if let Ok(contents) = fs::read_to_string(path).await {
                info!("Loading configuration from {}", path);
                match toml::from_str(&contents) {
                    Ok(config) => return Ok(config),
                    Err(e) => warn!("Failed to parse config file {}: {}", path, e),
                }
            }
        }

        warn!("No configuration file found, using defaults");
        Ok(Self::default())
    }

    pub async fn save(&self, path: &str) -> Result<(), Box<dyn std::error::Error>> {
        let contents = toml::to_string_pretty(self)?;
        fs::write(path, contents).await?;
        info!("Configuration saved to {}", path);
        Ok(())
    }

    pub fn validate(&self) -> Result<(), String> {
        if self.grpc.port == 0 {
            return Err("gRPC port cannot be 0".to_string());
        }

        if self.android.bridge_port == 0 {
            return Err("Android bridge port cannot be 0".to_string());
        }

        if self.backup.chunk_size == 0 {
            return Err("Backup chunk size cannot be 0".to_string());
        }

        if self.backup.compression_level > 9 {
            return Err("Compression level must be between 0-9".to_string());
        }

        if self.filesystem.watch_paths.is_empty() {
            return Err("At least one filesystem watch path must be configured".to_string());
        }

        Ok(())
    }
}