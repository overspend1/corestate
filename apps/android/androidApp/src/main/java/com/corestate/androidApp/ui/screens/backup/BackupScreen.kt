package com.corestate.androidApp.ui.screens.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corestate.androidApp.ui.components.BackupProgressCard
import com.corestate.androidApp.ui.components.FileSelectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: BackupViewModel = hiltViewModel()
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
                text = "Backup",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            BackupProgressCard(
                isBackupRunning = uiState.isBackupRunning,
                progress = uiState.backupProgress,
                onStartBackup = viewModel::startBackup,
                onStopBackup = viewModel::stopBackup,
                currentFile = uiState.currentFile,
                estimatedTimeRemaining = uiState.estimatedTimeRemaining
            )
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Backup Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto Backup")
                        Switch(
                            checked = uiState.autoBackupEnabled,
                            onCheckedChange = viewModel::setAutoBackupEnabled
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Include System Files")
                        Switch(
                            checked = uiState.includeSystemFiles,
                            onCheckedChange = viewModel::setIncludeSystemFiles
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Encrypt Backups")
                        Switch(
                            checked = uiState.encryptBackups,
                            onCheckedChange = viewModel::setEncryptBackups
                        )
                    }
                }
            }
        }
        
        item {
            Text(
                text = "Selected Folders",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        items(uiState.selectedFolders) { folder ->
            FileSelectionCard(
                folder = folder,
                onRemove = { viewModel.removeFolder(folder.path) }
            )
        }
        
        item {
            OutlinedButton(
                onClick = viewModel::selectFolders,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Folders")
            }
        }
        
        item {
            Text(
                text = "Backup History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        items(uiState.backupHistory) { backup ->
            BackupHistoryItem(
                backup = backup,
                onRestore = { viewModel.restoreBackup(backup.id) },
                onDelete = { viewModel.deleteBackup(backup.id) }
            )
        }
    }
    
    // Show error snackbar if needed
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show snackbar here
            viewModel.dismissError()
        }
    }
}

@Composable
private fun BackupHistoryItem(
    backup: BackupHistoryModel,
    onRestore: () -> Unit,
    onDelete: () -> Unit
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = backup.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${backup.size} • ${backup.filesCount} files",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = backup.timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row {
                    IconButton(onClick = onRestore) {
                        Icon(Icons.Default.Restore, contentDescription = "Restore")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}