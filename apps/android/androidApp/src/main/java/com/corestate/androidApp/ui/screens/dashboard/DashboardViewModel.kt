package com.corestate.androidApp.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    init {
        loadDashboardData()
        observeBackupStatus()
    }
    
    private fun loadDashboardData() {
        viewModelScope.launch {
            try {
                val stats = statisticsRepository.getBackupStatistics()
                val activities = statisticsRepository.getRecentActivities()
                
                _uiState.update { currentState ->
                    currentState.copy(
                        totalBackups = stats.totalBackups,
                        storageUsed = formatStorageSize(stats.storageUsedBytes),
                        filesProtected = stats.filesProtected,
                        lastBackupTime = formatLastBackupTime(stats.lastBackupTimestamp),
                        recentActivities = activities,
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
                        backupProgress = status.progress
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
    
    private fun formatLastBackupTime(timestamp: Long): String {
        if (timestamp == 0L) return "Never"
        
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> "${days / 7}w ago"
        }
    }
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isBackupRunning: Boolean = false,
    val backupProgress: Float = 0f,
    val totalBackups: Int = 0,
    val storageUsed: String = "0 B",
    val filesProtected: Int = 0,
    val lastBackupTime: String = "Never",
    val recentActivities: List<ActivityModel> = emptyList(),
    val error: String? = null
)

data class ActivityModel(
    val id: String,
    val type: ActivityType,
    val title: String,
    val description: String,
    val timestamp: String
)

enum class ActivityType {
    BACKUP_COMPLETED,
    BACKUP_FAILED,
    FILE_RESTORED,
    SYNC_COMPLETED
}

// Mock repository interfaces - these would be implemented with real data sources
interface BackupRepository {
    val backupStatus: Flow<BackupStatus>
    suspend fun startBackup()
    suspend fun stopBackup()
}

interface StatisticsRepository {
    suspend fun getBackupStatistics(): BackupStatistics
    suspend fun getRecentActivities(): List<ActivityModel>
}

data class BackupStatus(
    val isRunning: Boolean,
    val progress: Float
)

data class BackupStatistics(
    val totalBackups: Int,
    val storageUsedBytes: Long,
    val filesProtected: Int,
    val lastBackupTimestamp: Long
)