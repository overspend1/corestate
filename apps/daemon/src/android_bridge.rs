use crate::config::DaemonConfig;
use crate::backup::BackupManager;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Instant;
use crate::filesystem::FileSystemMonitor;
use crate::kernel_interface::KernelInterface;

use tokio::net::{TcpListener, TcpStream};
use tokio::sync::{RwLock, mpsc};
use tokio_tungstenite::{accept_async, WebSocketStream};
use tokio_tungstenite::tungstenite::Message;
use futures::{SinkExt, StreamExt};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use std::collections::HashMap;
use tracing::{info, error, warn, debug};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AndroidMessage {
    pub id: String,
    pub message_type: AndroidMessageType,
    pub payload: serde_json::Value,
    pub timestamp: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum AndroidMessageType {
    // Authentication
    Auth { token: String },
    AuthResponse { success: bool, device_id: String },
    
    // Device Management
    RegisterDevice { device_info: DeviceInfo },
    DeviceStatus { status: DeviceStatus },
    
    // Backup Operations
    StartBackup { paths: Vec<String>, options: BackupOptions },
    PauseBackup { job_id: String },
    ResumeBackup { job_id: String },
    CancelBackup { job_id: String },
    BackupProgress { job_id: String, progress: f32, details: String },
    BackupComplete { job_id: String, success: bool, details: String },
    
    // File Operations
    ListFiles { path: String },
    FileList { files: Vec<FileInfo> },
    RestoreFile { file_path: String, restore_path: String },
    RestoreProgress { progress: f32, details: String },
    
    // System Status
    GetSystemStatus,
    SystemStatus { status: SystemStatusInfo },
    GetLogs { level: String, lines: u32 },
    LogData { logs: Vec<String> },
    
    // Configuration
    GetConfig,
    UpdateConfig { config: serde_json::Value },
    ConfigResponse { success: bool, message: String },
    
    // Real-time notifications
    FileChanged { path: String, change_type: String },
    SystemAlert { level: String, message: String },
    
    // Kernel Module
    GetKernelStatus,
    KernelStatus { loaded: bool, version: String, features: Vec<String> },
    
    // Error handling
    Error { code: u32, message: String },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceInfo {
    pub device_id: String,
    pub device_name: String,
    pub os_version: String,
    pub app_version: String,
    pub hardware_info: HashMap<String, String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceStatus {
    pub online: bool,
    pub last_backup: Option<u64>,
    pub storage_usage: StorageInfo,
    pub network_status: NetworkStatus,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StorageInfo {
    pub total_space: u64,
    pub free_space: u64,
    pub backup_usage: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NetworkStatus {
    pub connected: bool,
    pub connection_type: String,
    pub signal_strength: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BackupOptions {
    pub incremental: bool,
    pub compression: bool,
    pub encryption: bool,
    pub priority: u8,
    pub exclude_patterns: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileInfo {
    pub path: String,
    pub size: u64,
    pub modified: u64,
    pub file_type: String,
    pub backed_up: bool,
    pub backup_time: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SystemStatusInfo {
    pub daemon_uptime: u64,
    pub active_backups: u32,
    pub total_files_backed_up: u64,
    pub total_backup_size: u64,
    pub memory_usage: u64,
    pub cpu_usage: f32,
    pub kernel_module_loaded: bool,
    pub services_status: HashMap<String, bool>,
}

pub struct AndroidClient {
    pub device_id: String,
    pub device_info: Option<DeviceInfo>,
    pub websocket: WebSocketStream<TcpStream>,
    pub message_sender: mpsc::UnboundedSender<AndroidMessage>,
    pub authenticated: bool,
    pub last_heartbeat: std::time::Instant,
}

pub struct AndroidBridge {
    config: Arc<DaemonConfig>,
    backup_manager: Arc<RwLock<BackupManager>>,
    fs_monitor: Arc<RwLock<FileSystemMonitor>>,
    kernel_interface: Arc<KernelInterface>,
    clients: Arc<RwLock<HashMap<String, AndroidClient>>>,
    event_sender: mpsc::UnboundedSender<AndroidMessage>,
    start_time: Instant,
}

impl AndroidBridge {
    pub async fn new(
        config: &Arc<DaemonConfig>,
        backup_manager: Arc<RwLock<BackupManager>>,
        fs_monitor: Arc<RwLock<FileSystemMonitor>>,
        kernel_interface: Arc<KernelInterface>,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let (event_sender, _) = mpsc::unbounded_channel();
        
        Ok(Self {
            config: config.clone(),
            backup_manager,
            fs_monitor,
            kernel_interface,
            clients: Arc::new(RwLock::new(HashMap::new())),
            event_sender,
            start_time: Instant::now(),
        })
    }

    pub async fn start(&self) -> Result<(), Box<dyn std::error::Error>> {
        let addr = format!("{}:{}", "0.0.0.0", self.config.android.bridge_port);
        let listener = TcpListener::bind(&addr).await?;
        info!("Android bridge listening on {}", addr);

        // Start heartbeat checker
        let clients = self.clients.clone();
        let heartbeat_interval = self.config.android.heartbeat_interval;
        tokio::spawn(async move {
            let mut interval = tokio::time::interval(
                std::time::Duration::from_secs(heartbeat_interval)
            );
            
            loop {
                interval.tick().await;
                Self::check_client_heartbeats(clients.clone(), heartbeat_interval * 2).await;
            }
        });

        while let Ok((stream, addr)) = listener.accept().await {
            info!("New Android connection from {}", addr);
            
            let clients = self.clients.clone();
            let config = self.config.clone();
            let backup_manager = self.backup_manager.clone();
            let fs_monitor = self.fs_monitor.clone();
            let kernel_interface = self.kernel_interface.clone();
            
            tokio::spawn(async move {
                if let Err(e) = Self::handle_client(
                    stream, clients, config, backup_manager, fs_monitor, kernel_interface
                ).await {
                    error!("Client handler error: {}", e);
                }
            });
        }

        Ok(())
    }

    async fn handle_client(
        stream: TcpStream,
        clients: Arc<RwLock<HashMap<String, AndroidClient>>>,
        config: Arc<DaemonConfig>,
        backup_manager: Arc<RwLock<BackupManager>>,
        fs_monitor: Arc<RwLock<FileSystemMonitor>>,
        kernel_interface: Arc<KernelInterface>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let websocket = accept_async(stream).await?;
        let (mut ws_sender, mut ws_receiver) = websocket.split();
        
        let (msg_sender, mut msg_receiver) = mpsc::unbounded_channel();
        let client_id = Uuid::new_v4().to_string();
        
        // Handle outgoing messages
        let sender_handle = tokio::spawn(async move {
            while let Some(message) = msg_receiver.recv().await {
                let json = serde_json::to_string(&message).unwrap();
                if let Err(e) = ws_sender.send(Message::Text(json)).await {
                    error!("Failed to send message to client: {}", e);
                    break;
                }
            }
        });

        // Handle incoming messages
        while let Some(msg) = ws_receiver.next().await {
            match msg {
                Ok(Message::Text(text)) => {
                    if let Ok(android_msg) = serde_json::from_str::<AndroidMessage>(&text) {
                        Self::process_message(
                            android_msg,
                            &client_id,
                            clients.clone(),
                            config.clone(),
                            backup_manager.clone(),
                            fs_monitor.clone(),
                            kernel_interface.clone(),
                            msg_sender.clone(),
                        ).await;
                    } else {
                        error!("Failed to parse Android message: {}", text);
                    }
                }
                Ok(Message::Close(_)) => {
                    info!("Client {} disconnected", client_id);
                    break;
                }
                Err(e) => {
                    error!("WebSocket error: {}", e);
                    break;
                }
                _ => {}
            }
        }

        // Cleanup
        clients.write().await.remove(&client_id);
        sender_handle.abort();
        
        Ok(())
    }

    async fn process_message(
        message: AndroidMessage,
        client_id: &str,
        clients: Arc<RwLock<HashMap<String, AndroidClient>>>,
        config: Arc<DaemonConfig>,
        backup_manager: Arc<RwLock<BackupManager>>,
        fs_monitor: Arc<RwLock<FileSystemMonitor>>,
        kernel_interface: Arc<KernelInterface>,
        sender: mpsc::UnboundedSender<AndroidMessage>,
    ) {
        debug!("Processing message: {:?}", message.message_type);

        match message.message_type {
            AndroidMessageType::Auth { token } => {
                let success = token == config.android.auth_token;
                let device_id = if success {
                    Uuid::new_v4().to_string()
                } else {
                    "unauthorized".to_string()
                };

                let response = AndroidMessage {
                    id: Uuid::new_v4().to_string(),
                    message_type: AndroidMessageType::AuthResponse { success, device_id: device_id.clone() },
                    payload: serde_json::Value::Null,
                    timestamp: std::time::SystemTime::now()
                        .duration_since(std::time::UNIX_EPOCH)
                        .unwrap()
                        .as_secs(),
                };

                if success {
                    info!("Client {} authenticated as device {}", client_id, device_id);
                }

                let _ = sender.send(response);
            }

            AndroidMessageType::GetSystemStatus => {
                let backup_manager = backup_manager.read().await;
                let uptime = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap()
                    .as_secs();
                    
                let status = SystemStatusInfo {
                    daemon_uptime: uptime,
                    active_backups: backup_manager.get_active_job_count().await,
                    total_files_backed_up: backup_manager.get_total_files_backed_up().await,
                    total_backup_size: backup_manager.get_total_backup_size().await,
                    memory_usage: Self::get_memory_usage().await,
                    cpu_usage: Self::get_cpu_usage().await,
                    kernel_module_loaded: kernel_interface.is_loaded().await,
                    services_status: Self::get_services_status().await,
                };

                let response = AndroidMessage {
                    id: Uuid::new_v4().to_string(),
                    message_type: AndroidMessageType::SystemStatus { status },
                    payload: serde_json::Value::Null,
                    timestamp: std::time::SystemTime::now()
                        .duration_since(std::time::UNIX_EPOCH)
                        .unwrap()
                        .as_secs(),
                };

                let _ = sender.send(response);
            }

            AndroidMessageType::StartBackup { paths, options } => {
                let backup_manager = backup_manager.write().await;
                match backup_manager.start_backup(paths, options).await {
                    Ok(job_id) => {
                        info!("Started backup job: {}", job_id);
                        // Send progress updates will be handled by backup manager
                    }
                    Err(e) => {
                        error!("Failed to start backup: {}", e);
                        let error_response = AndroidMessage {
                            id: Uuid::new_v4().to_string(),
                            message_type: AndroidMessageType::Error { 
                                code: 1001, 
                                message: format!("Failed to start backup: {}", e) 
                            },
                            payload: serde_json::Value::Null,
                            timestamp: std::time::SystemTime::now()
                                .duration_since(std::time::UNIX_EPOCH)
                                .unwrap()
                                .as_secs(),
                        };
                        let _ = sender.send(error_response);
                    }
                }
            }

            AndroidMessageType::ListFiles { path } => {
                match fs_monitor.read().await.list_files(&path).await {
                    Ok(files) => {
                        let response = AndroidMessage {
                            id: Uuid::new_v4().to_string(),
                            message_type: AndroidMessageType::FileList { files },
                            payload: serde_json::Value::Null,
                            timestamp: std::time::SystemTime::now()
                                .duration_since(std::time::UNIX_EPOCH)
                                .unwrap()
                                .as_secs(),
                        };
                        let _ = sender.send(response);
                    }
                    Err(e) => {
                        error!("Failed to list files: {}", e);
                        let error_response = AndroidMessage {
                            id: Uuid::new_v4().to_string(),
                            message_type: AndroidMessageType::Error { 
                                code: 1002, 
                                message: format!("Failed to list files: {}", e) 
                            },
                            payload: serde_json::Value::Null,
                            timestamp: std::time::SystemTime::now()
                                .duration_since(std::time::UNIX_EPOCH)
                                .unwrap()
                                .as_secs(),
                        };
                        let _ = sender.send(error_response);
                    }
                }
            }

            AndroidMessageType::GetKernelStatus => {
                let status = kernel_interface.get_status().await;
                let response = AndroidMessage {
                    id: Uuid::new_v4().to_string(),
                    message_type: AndroidMessageType::KernelStatus { 
                        loaded: status.loaded,
                        version: status.version,
                        features: status.features 
                    },
                    payload: serde_json::Value::Null,
                    timestamp: std::time::SystemTime::now()
                        .duration_since(std::time::UNIX_EPOCH)
                        .unwrap()
                        .as_secs(),
                };
                let _ = sender.send(response);
            }

            _ => {
                warn!("Unhandled message type: {:?}", message.message_type);
            }
        }
    }

    async fn check_client_heartbeats(
        clients: Arc<RwLock<HashMap<String, AndroidClient>>>,
        timeout_seconds: u64,
    ) {
        let mut clients_to_remove = Vec::new();
        let timeout_duration = std::time::Duration::from_secs(timeout_seconds);
        
        {
            let clients_read = clients.read().await;
            for (client_id, client) in clients_read.iter() {
                if client.last_heartbeat.elapsed() > timeout_duration {
                    clients_to_remove.push(client_id.clone());
                }
            }
        }

        if !clients_to_remove.is_empty() {
            let mut clients_write = clients.write().await;
            for client_id in clients_to_remove {
                warn!("Removing inactive client: {}", client_id);
                clients_write.remove(&client_id);
            }
        }
    }

    async fn get_memory_usage() -> u64 {
        // Read memory usage from /proc/self/status on Linux
        #[cfg(target_os = "linux")]
        {
            if let Ok(status) = tokio::fs::read_to_string("/proc/self/status").await {
                for line in status.lines() {
                    if line.starts_with("VmRSS:") {
                        if let Some(value) = line.split_whitespace().nth(1) {
                            if let Ok(kb) = value.parse::<u64>() {
                                return kb * 1024; // Convert KB to bytes
                            }
                        }
                    }
                }
            }
        }
        
        // Fallback for non-Linux or if reading fails
        64 * 1024 * 1024 // 64MB default
    }

    async fn get_cpu_usage() -> f32 {
        // Read CPU usage from /proc/stat on Linux
        #[cfg(target_os = "linux")]
        {
            if let Ok(stat) = tokio::fs::read_to_string("/proc/self/stat").await {
                let parts: Vec<&str> = stat.split_whitespace().collect();
                if parts.len() > 14 {
                    if let (Ok(utime), Ok(stime)) = (parts[13].parse::<f32>(), parts[14].parse::<f32>()) {
                        let total_time = utime + stime;
                        let uptime = std::time::SystemTime::now()
                            .duration_since(std::time::UNIX_EPOCH)
                            .unwrap()
                            .as_secs() as f32;
                        
                        // CPU usage as percentage
                        return (total_time / uptime / 100.0).min(100.0);
                    }
                }
            }
        }
        
        // Fallback
        5.0 // 5% default
    }

    async fn get_services_status() -> HashMap<String, bool> {
        let mut status = HashMap::new();
        let services = vec![
            ("backup_engine", "http://backup-engine:8080/actuator/health"),
            ("storage_hal", "http://storage-hal:8080/health"),
            ("compression_engine", "http://compression-engine:8080/health"),
            ("encryption_service", "http://encryption-service:3000/health"),
            ("ml_optimizer", "http://ml-optimizer:8000/health"),
            ("sync_coordinator", "http://sync-coordinator:3001/health"),
            ("deduplication_service", "http://deduplication-service:8001/health"),
            ("index_service", "http://index-service:8081/actuator/health"),
            ("analytics_engine", "http://analytics-engine:8082/health"),
        ];
        
        let client = reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(2))
            .build()
            .unwrap();
        
        for (service_name, url) in services {
            let is_healthy = client.get(url)
                .send()
                .await
                .map(|resp| resp.status().is_success())
                .unwrap_or(false);
            
            status.insert(service_name.to_string(), is_healthy);
        }
        
        status
    }

    pub async fn broadcast_message(&self, message: AndroidMessage) {
        let clients = self.clients.read().await;
        for (_, client) in clients.iter() {
            let _ = client.message_sender.send(message.clone());
        }
    }

    pub async fn send_to_device(&self, device_id: &str, message: AndroidMessage) -> bool {
        let clients = self.clients.read().await;
        if let Some(client) = clients.get(device_id) {
            client.message_sender.send(message).is_ok()
        } else {
            false
        }
    }
}