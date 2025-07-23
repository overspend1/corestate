package com.corestate.androidApp.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.corestate.androidApp.data.model.*
import com.corestate.androidApp.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemAdminScreen(
    viewModel: SystemAdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadSystemStatus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "System Administration",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // System Status Overview
            item {
                SystemStatusCard(
                    systemStatus = uiState.systemStatus,
                    isLoading = uiState.isLoading
                )
            }
            
            // Service Management
            item {
                ServiceManagementCard(
                    services = uiState.services,
                    onServiceAction = viewModel::performServiceAction
                )
            }
            
            // Kernel Module Management
            item {
                KernelModuleCard(
                    kernelStatus = uiState.kernelStatus,
                    onLoadModule = viewModel::loadKernelModule,
                    onUnloadModule = viewModel::unloadKernelModule,
                    isLoading = uiState.kernelOperationInProgress
                )
            }
            
            // Device Management
            item {
                DeviceManagementCard(
                    devices = uiState.connectedDevices,
                    onRefresh = viewModel::refreshDevices
                )
            }
            
            // Configuration Management
            item {
                ConfigurationCard(
                    configuration = uiState.configuration,
                    onUpdateConfig = viewModel::updateConfiguration,
                    onExportConfig = viewModel::exportConfiguration,
                    onImportConfig = viewModel::importConfiguration
                )
            }
            
            // System Logs
            item {
                SystemLogsCard(
                    logs = uiState.systemLogs,
                    onRefreshLogs = viewModel::refreshLogs,
                    onClearLogs = viewModel::clearLogs
                )
            }
            
            // Performance Monitoring
            item {
                PerformanceMonitoringCard(
                    metrics = uiState.performanceMetrics,
                    onRefresh = viewModel::refreshMetrics
                )
            }
        }
    }
}

@Composable
fun SystemStatusCard(
    systemStatus: SystemStatusInfo?,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Status",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    systemStatus?.let { status ->
                        StatusIndicator(
                            isHealthy = status.daemonUptime > 0 && status.servicesStatus.values.all { it },
                            text = if (status.daemonUptime > 0) "Online" else "Offline"
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            systemStatus?.let { status ->
                StatusMetricRow("Daemon Uptime", formatUptime(status.daemonUptime))
                StatusMetricRow("Active Backups", status.activeBackups.toString())
                StatusMetricRow("Total Files Backed Up", formatNumber(status.totalFilesBackedUp))
                StatusMetricRow("Total Backup Size", formatBytes(status.totalBackupSize))
                StatusMetricRow("Memory Usage", formatBytes(status.memoryUsage))
                StatusMetricRow("CPU Usage", "${status.cpuUsage}%")
                StatusMetricRow("Kernel Module", if (status.kernelModuleLoaded) "Loaded" else "Not Loaded")
            }
        }
    }
}

@Composable
fun ServiceManagementCard(
    services: Map<String, ServiceStatus>,
    onServiceAction: (String, ServiceAction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Microservices",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            services.forEach { (serviceName, status) ->
                ServiceRow(
                    serviceName = serviceName,
                    status = status,
                    onAction = { action -> onServiceAction(serviceName, action) }
                )
                
                if (serviceName != services.keys.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
fun ServiceRow(
    serviceName: String,
    status: ServiceStatus,
    onAction: (ServiceAction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatServiceName(serviceName),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Response: ${status.responseTime}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusIndicator(
                isHealthy = status.isHealthy,
                text = if (status.isHealthy) "Healthy" else "Error"
            )
            
            IconButton(
                onClick = { onAction(ServiceAction.RESTART) }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Restart Service")
            }
            
            IconButton(
                onClick = { onAction(ServiceAction.VIEW_LOGS) }
            ) {
                Icon(Icons.Default.Description, contentDescription = "View Logs")
            }
        }
    }
}

@Composable
fun KernelModuleCard(
    kernelStatus: KernelStatusResponse?,
    onLoadModule: () -> Unit,
    onUnloadModule: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Kernel Module",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            kernelStatus?.let { status ->
                StatusMetricRow("Status", if (status.loaded) "Loaded" else "Not Loaded")
                StatusMetricRow("Version", status.version)
                
                if (status.features.isNotEmpty()) {
                    Text(
                        text = "Features:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    status.features.forEach { feature ->
                        Text(
                            text = "• $feature",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (status.loaded) {
                        Button(
                            onClick = onUnloadModule,
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onError
                                )
                            } else {
                                Text("Unload Module")
                            }
                        }
                    } else {
                        Button(
                            onClick = onLoadModule,
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Load Module")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceManagementCard(
    devices: List<ConnectedDevice>,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected Devices",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Devices")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (devices.isEmpty()) {
                Text(
                    text = "No devices connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                devices.forEach { device ->
                    DeviceRow(device)
                    if (device != devices.last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceRow(device: ConnectedDevice) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.deviceName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = device.deviceId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Last seen: ${formatTimestamp(device.lastSeen)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        StatusIndicator(
            isHealthy = device.isOnline,
            text = if (device.isOnline) "Online" else "Offline"
        )
    }
}

@Composable
fun StatusIndicator(
    isHealthy: Boolean,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isHealthy) Color.Green else Color.Red,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isHealthy) Color.Green else Color.Red
        )
    }
}

@Composable
fun StatusMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// Helper functions
private fun formatUptime(uptimeSeconds: Long): String {
    val days = uptimeSeconds / 86400
    val hours = (uptimeSeconds % 86400) / 3600
    val minutes = (uptimeSeconds % 3600) / 60
    
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return "%.1f %s".format(size, units[unitIndex])
}

private fun formatNumber(number: Long): String {
    return when {
        number >= 1_000_000 -> "%.1fM".format(number / 1_000_000.0)
        number >= 1_000 -> "%.1fK".format(number / 1_000.0)
        else -> number.toString()
    }
}

private fun formatServiceName(serviceName: String): String {
    return serviceName.split("-", "_")
        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

enum class ServiceAction {
    RESTART,
    VIEW_LOGS,
    CONFIGURE
}