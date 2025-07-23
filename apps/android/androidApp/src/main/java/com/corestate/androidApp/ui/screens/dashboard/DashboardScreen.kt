package com.corestate.androidApp.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corestate.androidApp.R
import com.corestate.androidApp.ui.components.BackupProgressCard
import com.corestate.androidApp.ui.components.QuickActionCard
import com.corestate.androidApp.ui.components.StatCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToBackup: () -> Unit,
    onNavigateToFiles: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            BackupProgressCard(
                isBackupRunning = uiState.isBackupRunning,
                progress = uiState.backupProgress,
                onStartBackup = viewModel::startBackup,
                onStopBackup = viewModel::stopBackup
            )
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Backups",
                    value = uiState.totalBackups.toString(),
                    icon = Icons.Default.Backup
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Storage Used",
                    value = uiState.storageUsed,
                    icon = Icons.Default.Storage
                )
            }
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Files Protected",
                    value = uiState.filesProtected.toString(),
                    icon = Icons.Default.Shield
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Last Backup",
                    value = uiState.lastBackupTime,
                    icon = Icons.Default.Schedule
                )
            }
        }
        
        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Start Backup",
                    description = "Begin backup process",
                    icon = Icons.Default.PlayArrow,
                    onClick = onNavigateToBackup
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Browse Files",
                    description = "View backed up files",
                    icon = Icons.Default.Folder,
                    onClick = onNavigateToFiles
                )
            }
        }
        
        item {
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        items(uiState.recentActivities) { activity ->
            ActivityItem(activity = activity)
        }
    }
}

@Composable
private fun ActivityItem(
    activity: ActivityModel
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (activity.type) {
                    ActivityType.BACKUP_COMPLETED -> Icons.Default.CheckCircle
                    ActivityType.BACKUP_FAILED -> Icons.Default.Error
                    ActivityType.FILE_RESTORED -> Icons.Default.Restore
                    ActivityType.SYNC_COMPLETED -> Icons.Default.Sync
                },
                contentDescription = null,
                tint = when (activity.type) {
                    ActivityType.BACKUP_COMPLETED, ActivityType.FILE_RESTORED, ActivityType.SYNC_COMPLETED -> 
                        MaterialTheme.colorScheme.primary
                    ActivityType.BACKUP_FAILED -> MaterialTheme.colorScheme.error
                }
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = activity.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = activity.timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}