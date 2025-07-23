package com.corestate.androidApp.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val userRepository: UserRepository,
    private val securityRepository: SecurityRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                combine(
                    settingsRepository.getSettings(),
                    userRepository.getUserInfo(),
                    securityRepository.getSecuritySettings()
                ) { settings, userInfo, securitySettings ->
                    Triple(settings, userInfo, securitySettings)
                }.collect { (settings, userInfo, securitySettings) ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            userEmail = userInfo.email,
                            storageUsed = formatStorageSize(userInfo.storageUsedBytes),
                            storageLimit = formatStorageSize(userInfo.storageLimitBytes),
                            subscriptionType = userInfo.subscriptionType,
                            autoBackupEnabled = settings.autoBackupEnabled,
                            wifiOnlyBackup = settings.wifiOnlyBackup,
                            encryptBackups = settings.encryptBackups,
                            backupFrequency = settings.backupFrequency,
                            biometricEnabled = securitySettings.biometricEnabled,
                            twoFactorEnabled = securitySettings.twoFactorEnabled,
                            p2pSyncEnabled = settings.p2pSyncEnabled,
                            connectedDevices = settings.connectedDevicesCount,
                            backupNotifications = settings.backupNotifications,
                            securityAlerts = settings.securityAlerts,
                            appVersion = "2.0.0"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    // Account actions
    fun openAccountInfo() {
        // Navigate to account info screen
    }
    
    fun openStorageInfo() {
        // Navigate to storage info screen
    }
    
    fun openSubscription() {
        // Navigate to subscription screen
    }
    
    // Backup settings
    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupEnabled(enabled)
        }
    }
    
    fun setWifiOnlyBackup(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWifiOnlyBackup(enabled)
        }
    }
    
    fun setEncryptBackups(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEncryptBackups(enabled)
        }
    }
    
    fun openBackupFrequency() {
        // Open backup frequency selection dialog
    }
    
    // Security settings
    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityRepository.setBiometricEnabled(enabled)
        }
    }
    
    fun changePIN() {
        // Navigate to PIN change screen
    }
    
    fun openTwoFactor() {
        // Navigate to 2FA settings
    }
    
    // Sync settings
    fun setP2PSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setP2PSyncEnabled(enabled)
        }
    }
    
    fun openConnectedDevices() {
        // Navigate to connected devices screen
    }
    
    // Notification settings
    fun setBackupNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBackupNotifications(enabled)
        }
    }
    
    fun setSecurityAlerts(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSecurityAlerts(enabled)
        }
    }
    
    // Advanced actions
    fun openAdvancedSettings() {
        // Navigate to advanced settings
    }
    
    fun exportData() {
        viewModelScope.launch {
            try {
                // Implement data export
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun importData() {
        viewModelScope.launch {
            try {
                // Implement data import
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    // Support actions
    fun openHelp() {
        // Open help screen or external link
    }
    
    fun contactSupport() {
        // Open support contact options
    }
    
    fun showAppInfo() {
        // Show app information dialog
    }
    
    // Danger zone actions
    fun signOut() {
        viewModelScope.launch {
            try {
                userRepository.signOut()
                // Navigate to login screen
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun deleteAccount() {
        viewModelScope.launch {
            try {
                // Show confirmation dialog first
                userRepository.deleteAccount()
                // Navigate to login screen
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
    
    private fun formatStorageSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return "%.1f %s".format(size, units[unitIndex])
    }
}

data class SettingsUiState(
    val userEmail: String = "",
    val storageUsed: String = "0 B",
    val storageLimit: String = "0 B",
    val subscriptionType: String = "Free",
    val autoBackupEnabled: Boolean = false,
    val wifiOnlyBackup: Boolean = true,
    val encryptBackups: Boolean = true,
    val backupFrequency: String = "Daily",
    val biometricEnabled: Boolean = false,
    val twoFactorEnabled: Boolean = false,
    val p2pSyncEnabled: Boolean = false,
    val connectedDevices: Int = 0,
    val backupNotifications: Boolean = true,
    val securityAlerts: Boolean = true,
    val appVersion: String = "2.0.0",
    val error: String? = null
)

// Repository interfaces
interface SettingsRepository {
    suspend fun getSettings(): Flow<AppSettings>
    suspend fun setAutoBackupEnabled(enabled: Boolean)
    suspend fun setWifiOnlyBackup(enabled: Boolean)
    suspend fun setEncryptBackups(enabled: Boolean)
    suspend fun setP2PSyncEnabled(enabled: Boolean)
    suspend fun setBackupNotifications(enabled: Boolean)
    suspend fun setSecurityAlerts(enabled: Boolean)
}

interface UserRepository {
    suspend fun getUserInfo(): UserInfo
    suspend fun signOut()
    suspend fun deleteAccount()
}

interface SecurityRepository {
    suspend fun getSecuritySettings(): SecuritySettings
    suspend fun setBiometricEnabled(enabled: Boolean)
}

data class AppSettings(
    val autoBackupEnabled: Boolean,
    val wifiOnlyBackup: Boolean,
    val encryptBackups: Boolean,
    val backupFrequency: String,
    val p2pSyncEnabled: Boolean,
    val connectedDevicesCount: Int,
    val backupNotifications: Boolean,
    val securityAlerts: Boolean
)

data class UserInfo(
    val email: String,
    val storageUsedBytes: Long,
    val storageLimitBytes: Long,
    val subscriptionType: String
)

data class SecuritySettings(
    val biometricEnabled: Boolean,
    val twoFactorEnabled: Boolean
)