package com.corestate.androidApp.ui.screens.settings

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
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
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Account Section
        item {
            SettingsSection(title = "Account") {
                SettingsItem(
                    title = "Account Info",
                    subtitle = uiState.userEmail,
                    icon = Icons.Default.Person,
                    onClick = { viewModel.openAccountInfo() }
                )
                SettingsItem(
                    title = "Storage",
                    subtitle = "${uiState.storageUsed} of ${uiState.storageLimit} used",
                    icon = Icons.Default.Storage,
                    onClick = { viewModel.openStorageInfo() }
                )
                SettingsItem(
                    title = "Subscription",
                    subtitle = uiState.subscriptionType,
                    icon = Icons.Default.Star,
                    onClick = { viewModel.openSubscription() }
                )
            }
        }
        
        // Backup Settings Section
        item {
            SettingsSection(title = "Backup Settings") {
                SettingsSwitchItem(
                    title = "Auto Backup",
                    subtitle = "Automatically backup when charging",
                    icon = Icons.Default.Backup,
                    checked = uiState.autoBackupEnabled,
                    onCheckedChange = viewModel::setAutoBackupEnabled
                )
                SettingsSwitchItem(
                    title = "WiFi Only",
                    subtitle = "Only backup over WiFi",
                    icon = Icons.Default.Wifi,
                    checked = uiState.wifiOnlyBackup,
                    onCheckedChange = viewModel::setWifiOnlyBackup
                )
                SettingsSwitchItem(
                    title = "Encrypt Backups",
                    subtitle = "End-to-end encryption",
                    icon = Icons.Default.Security,
                    checked = uiState.encryptBackups,
                    onCheckedChange = viewModel::setEncryptBackups
                )
                SettingsItem(
                    title = "Backup Frequency",
                    subtitle = uiState.backupFrequency,
                    icon = Icons.Default.Schedule,
                    onClick = { viewModel.openBackupFrequency() }
                )
            }
        }
        
        // Security Section
        item {
            SettingsSection(title = "Security") {
                SettingsSwitchItem(
                    title = "Biometric Lock",
                    subtitle = "Use fingerprint/face unlock",
                    icon = Icons.Default.Fingerprint,
                    checked = uiState.biometricEnabled,
                    onCheckedChange = viewModel::setBiometricEnabled
                )
                SettingsItem(
                    title = "Change PIN",
                    subtitle = "Update your backup PIN",
                    icon = Icons.Default.Lock,
                    onClick = { viewModel.changePIN() }
                )
                SettingsItem(
                    title = "Two-Factor Authentication",
                    subtitle = if (uiState.twoFactorEnabled) "Enabled" else "Disabled",
                    icon = Icons.Default.Security,
                    onClick = { viewModel.openTwoFactor() }
                )
            }
        }
        
        // Sync Settings
        item {
            SettingsSection(title = "Sync") {
                SettingsSwitchItem(
                    title = "P2P Sync",
                    subtitle = "Sync with other devices",
                    icon = Icons.Default.Sync,
                    checked = uiState.p2pSyncEnabled,
                    onCheckedChange = viewModel::setP2PSyncEnabled
                )
                SettingsItem(
                    title = "Connected Devices",
                    subtitle = "${uiState.connectedDevices} devices",
                    icon = Icons.Default.Devices,
                    onClick = { viewModel.openConnectedDevices() }
                )
            }
        }
        
        // Notifications Section
        item {
            SettingsSection(title = "Notifications") {
                SettingsSwitchItem(
                    title = "Backup Notifications",
                    subtitle = "Get notified about backup status",
                    icon = Icons.Default.Notifications,
                    checked = uiState.backupNotifications,
                    onCheckedChange = viewModel::setBackupNotifications
                )
                SettingsSwitchItem(
                    title = "Security Alerts",
                    subtitle = "Get notified about security events",
                    icon = Icons.Default.Warning,
                    checked = uiState.securityAlerts,
                    onCheckedChange = viewModel::setSecurityAlerts
                )
            }
        }
        
        // Advanced Section
        item {
            SettingsSection(title = "Advanced") {
                SettingsItem(
                    title = "Advanced Settings",
                    subtitle = "Developer and power user options",
                    icon = Icons.Default.Settings,
                    onClick = { viewModel.openAdvancedSettings() }
                )
                SettingsItem(
                    title = "Export Data",
                    subtitle = "Export your backup data",
                    icon = Icons.Default.Download,
                    onClick = { viewModel.exportData() }
                )
                SettingsItem(
                    title = "Import Data",
                    subtitle = "Import from another backup",
                    icon = Icons.Default.Upload,
                    onClick = { viewModel.importData() }
                )
            }
        }
        
        // Support Section
        item {
            SettingsSection(title = "Support") {
                SettingsItem(
                    title = "Help & FAQ",
                    subtitle = "Get help and find answers",
                    icon = Icons.Default.Help,
                    onClick = { viewModel.openHelp() }
                )
                SettingsItem(
                    title = "Contact Support",
                    subtitle = "Get in touch with our team",
                    icon = Icons.Default.ContactSupport,
                    onClick = { viewModel.contactSupport() }
                )
                SettingsItem(
                    title = "App Version",
                    subtitle = uiState.appVersion,
                    icon = Icons.Default.Info,
                    onClick = { viewModel.showAppInfo() }
                )
            }
        }
        
        // Danger Zone
        item {
            SettingsSection(title = "Danger Zone") {
                SettingsItem(
                    title = "Sign Out",
                    subtitle = "Sign out of your account",
                    icon = Icons.Default.Logout,
                    onClick = { viewModel.signOut() },
                    isDestructive = true
                )
                SettingsItem(
                    title = "Delete Account",
                    subtitle = "Permanently delete your account",
                    icon = Icons.Default.Delete,
                    onClick = { viewModel.deleteAccount() },
                    isDestructive = true
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}