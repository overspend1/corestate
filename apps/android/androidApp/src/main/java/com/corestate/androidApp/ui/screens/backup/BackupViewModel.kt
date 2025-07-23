package com.corestate.androidApp.ui.screens.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()
    
    init {
        loadBackupData()
        observeBackupStatus()
        observeSettings()
    }
    
    private fun loadBackupData() {
        viewModelScope.launch {
            try {
                val folders = fileRepository.getSelectedFolders()
                val history = backupRepository.getBackupHistory()
                
                _uiState.update { currentState ->
                    currentState.copy(
                        selectedFolders = folders,
                        backupHistory = history,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    private fun observeBackupStatus() {
        viewModelScope.launch {
            backupRepository.backupStatus.collect { status ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isBackupRunning = status.isRunning,
                        backupProgress = status.progress,
                        currentFile = status.currentFile,
                        estimatedTimeRemaining = status.estimatedTimeRemaining
                    )
                }
            }
        }
    }
    
    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.backupSettings.collect { settings ->
                _uiState.update { currentState ->
                    currentState.copy(
                        autoBackupEnabled = settings.autoBackupEnabled,
                        includeSystemFiles = settings.includeSystemFiles,
                        encryptBackups = settings.encryptBackups
                    )
                }
            }
        }
    }
    
    fun startBackup() {
        viewModelScope.launch {
            try {
                backupRepository.startBackup()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun stopBackup() {
        viewModelScope.launch {
            try {
                backupRepository.stopBackup()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupEnabled(enabled)
        }
    }
    
    fun setIncludeSystemFiles(include: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIncludeSystemFiles(include)
        }
    }
    
    fun setEncryptBackups(encrypt: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEncryptBackups(encrypt)
        }
    }
    
    fun selectFolders() {
        viewModelScope.launch {
            try {
                fileRepository.selectFolders()
                loadBackupData() // Reload to get updated folders
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun removeFolder(path: String) {
        viewModelScope.launch {
            try {
                fileRepository.removeFolder(path)
                loadBackupData() // Reload to get updated folders
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun restoreBackup(backupId: String) {
        viewModelScope.launch {
            try {
                backupRepository.restoreBackup(backupId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun deleteBackup(backupId: String) {
        viewModelScope.launch {
            try {
                backupRepository.deleteBackup(backupId)
                loadBackupData() // Reload to get updated history
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class BackupUiState(
    val isLoading: Boolean = true,
    val isBackupRunning: Boolean = false,
    val backupProgress: Float = 0f,
    val currentFile: String? = null,
    val estimatedTimeRemaining: String? = null,
    val autoBackupEnabled: Boolean = false,
    val includeSystemFiles: Boolean = false,
    val encryptBackups: Boolean = true,
    val selectedFolders: List<FolderModel> = emptyList(),
    val backupHistory: List<BackupHistoryModel> = emptyList(),
    val error: String? = null
)

data class FolderModel(
    val path: String,
    val name: String,
    val size: String,
    val filesCount: Int
)

data class BackupHistoryModel(
    val id: String,
    val name: String,
    val timestamp: String,
    val size: String,
    val filesCount: Int,
    val status: BackupStatus
)

enum class BackupStatus {
    COMPLETED,
    FAILED,
    IN_PROGRESS
}

// Enhanced BackupStatus for detailed progress
data class DetailedBackupStatus(
    val isRunning: Boolean,
    val progress: Float,
    val currentFile: String? = null,
    val estimatedTimeRemaining: String? = null
)

// Additional repository interfaces
interface SettingsRepository {
    val backupSettings: Flow<BackupSettings>
    suspend fun setAutoBackupEnabled(enabled: Boolean)
    suspend fun setIncludeSystemFiles(include: Boolean)
    suspend fun setEncryptBackups(encrypt: Boolean)
}

interface FileRepository {
    suspend fun getSelectedFolders(): List<FolderModel>
    suspend fun selectFolders()
    suspend fun removeFolder(path: String)
}

data class BackupSettings(
    val autoBackupEnabled: Boolean,
    val includeSystemFiles: Boolean,
    val encryptBackups: Boolean
)

// Enhanced BackupRepository interface
interface BackupRepository {
    val backupStatus: Flow<DetailedBackupStatus>
    suspend fun startBackup()
    suspend fun stopBackup()
    suspend fun getBackupHistory(): List<BackupHistoryModel>
    suspend fun restoreBackup(backupId: String)
    suspend fun deleteBackup(backupId: String)
}