package com.corestate.androidApp.data.repository

import com.corestate.androidApp.data.model.*
import com.corestate.androidApp.network.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val apiService: CoreStateApiService,
    private val deviceManager: DeviceManager
) {
    
    suspend fun startBackup(
        paths: List<String>,
        backupType: BackupType = BackupType.INCREMENTAL,
        options: BackupOptions = BackupOptions()
    ): ApiResult<BackupJobResponse> {
        return try {
            val deviceId = deviceManager.getDeviceId()
            val request = BackupRequest(
                deviceId = deviceId,
                paths = paths,
                backupType = backupType,
                options = options
            )
            apiService.startBackup(request)
        } catch (e: Exception) {
            ApiResult.Error(e, "Failed to start backup")
        }
    }
    
    suspend fun getBackupStatus(jobId: String): ApiResult<BackupJobStatus> {
        return apiService.getBackupStatus(jobId)
    }
    
    fun streamBackupProgress(jobId: String): Flow<BackupProgress> {
        return apiService.getBackupProgress(jobId)
    }
    
    suspend fun pauseBackup(jobId: String): ApiResult<Unit> {
        return apiService.pauseBackup(jobId)
    }
    
    suspend fun resumeBackup(jobId: String): ApiResult<Unit> {
        return apiService.resumeBackup(jobId)
    }
    
    suspend fun cancelBackup(jobId: String): ApiResult<Unit> {
        return apiService.cancelBackup(jobId)
    }
    
    suspend fun getBackupHistory(
        page: Int = 0,
        size: Int = 20
    ): ApiResult<BackupJobListResponse> {
        return apiService.listBackups(page, size)
    }
    
    suspend fun getActiveBackups(): ApiResult<List<BackupJobSummary>> {
        return when (val result = apiService.listBackups(0, 50)) {
            is ApiResult.Success -> {
                val activeJobs = result.data.jobs.filter { 
                    it.status in listOf(JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.PAUSED)
                }
                ApiResult.Success(activeJobs)
            }
            is ApiResult.Error -> result
            ApiResult.Loading -> ApiResult.Loading
        }
    }
    
    suspend fun startRestore(
        snapshotId: String,
        files: List<String>,
        targetPath: String,
        overwriteExisting: Boolean = false
    ): ApiResult<RestoreJobResponse> {
        return try {
            val deviceId = deviceManager.getDeviceId()
            val request = RestoreRequest(
                deviceId = deviceId,
                snapshotId = snapshotId,
                files = files,
                targetPath = targetPath,
                overwriteExisting = overwriteExisting
            )
            apiService.startRestore(request)
        } catch (e: Exception) {
            ApiResult.Error(e, "Failed to start restore")
        }
    }
    
    suspend fun getRestoreStatus(jobId: String): ApiResult<RestoreJobStatus> {
        return apiService.getRestoreStatus(jobId)
    }
    
    suspend fun getSnapshots(
        page: Int = 0,
        size: Int = 20
    ): ApiResult<SnapshotListResponse> {
        val deviceId = deviceManager.getDeviceId()
        return apiService.listSnapshots(deviceId, page, size)
    }
    
    suspend fun browseSnapshotFiles(
        snapshotId: String,
        path: String = "/"
    ): ApiResult<FileListResponse> {
        return apiService.browseSnapshot(snapshotId, path)
    }
    
    suspend fun getBackupPrediction(
        paths: List<String>,
        estimatedSize: Long
    ): ApiResult<BackupPrediction> {
        return try {
            val deviceId = deviceManager.getDeviceId()
            val request = BackupPredictionRequest(
                deviceId = deviceId,
                filePaths = paths,
                estimatedSize = estimatedSize,
                metadata = deviceManager.getDeviceMetadata()
            )
            apiService.predictBackupPerformance(request)
        } catch (e: Exception) {
            ApiResult.Error(e, "Failed to get backup prediction")
        }
    }
    
    suspend fun optimizeBackupSchedule(
        backupJobs: List<BackupJobRequest>
    ): ApiResult<OptimizationResult> {
        return try {
            val request = ScheduleOptimizationRequest(
                backupJobs = backupJobs,
                resourceConstraints = mapOf(
                    "maxConcurrentJobs" to 3,
                    "maxCpuUsage" to 80,
                    "maxMemoryUsage" to 90
                ),
                optimizationGoals = listOf("minimize_time", "maximize_throughput")
            )
            apiService.optimizeBackupSchedule(request)
        } catch (e: Exception) {
            ApiResult.Error(e, "Failed to optimize backup schedule")
        }
    }
}

@Singleton
class FileRepository @Inject constructor(
    private val apiService: CoreStateApiService,
    private val deviceManager: DeviceManager
) {
    
    suspend fun listFiles(path: String): ApiResult<FileListResponse> {
        return apiService.listFiles(path)
    }
    
    suspend fun getFileInfo(path: String): ApiResult<BackupFileInfo> {
        return when (val result = listFiles(path)) {
            is ApiResult.Success -> {
                val file = result.data.files.find { it.path == path }
                if (file != null) {
                    ApiResult.Success(file)
                } else {
                    ApiResult.Error(Exception("File not found"), "File not found: $path")
                }
            }
            is ApiResult.Error -> result
            ApiResult.Loading -> ApiResult.Loading
        }
    }
    
    suspend fun searchFiles(
        query: String,
        path: String = "/",
        fileTypes: List<String> = emptyList()
    ): ApiResult<List<BackupFileInfo>> {
        return when (val result = listFiles(path)) {
            is ApiResult.Success -> {
                val filteredFiles = result.data.files.filter { file ->
                    val matchesQuery = file.name.contains(query, ignoreCase = true) ||
                                     file.path.contains(query, ignoreCase = true)
                    val matchesType = fileTypes.isEmpty() || 
                                    fileTypes.any { type -> file.name.endsWith(".$type", ignoreCase = true) }
                    matchesQuery && matchesType
                }
                ApiResult.Success(filteredFiles)
            }
            is ApiResult.Error -> result
            ApiResult.Loading -> ApiResult.Loading
        }
    }
    
    suspend fun getDirectoryTree(rootPath: String, maxDepth: Int = 3): ApiResult<DirectoryNode> {
        return buildDirectoryTree(rootPath, maxDepth, 0)
    }
    
    private suspend fun buildDirectoryTree(
        path: String,
        maxDepth: Int,
        currentDepth: Int
    ): ApiResult<DirectoryNode> {
        if (currentDepth >= maxDepth) {
            return ApiResult.Success(DirectoryNode(path, emptyList(), emptyList()))
        }
        
        return when (val result = listFiles(path)) {
            is ApiResult.Success -> {
                val files = result.data.files.filter { !it.isDirectory }
                val directories = result.data.files.filter { it.isDirectory }
                
                val childNodes = mutableListOf<DirectoryNode>()
                for (dir in directories) {
                    when (val childResult = buildDirectoryTree(dir.path, maxDepth, currentDepth + 1)) {
                        is ApiResult.Success -> childNodes.add(childResult.data)
                        is ApiResult.Error -> continue // Skip failed directories
                        ApiResult.Loading -> continue
                    }
                }
                
                ApiResult.Success(DirectoryNode(path, files, childNodes))
            }
            is ApiResult.Error -> result
            ApiResult.Loading -> ApiResult.Loading
        }
    }
}

@Singleton
class StatisticsRepository @Inject constructor(
    private val apiService: CoreStateApiService,
    private val deviceManager: DeviceManager
) {
    
    suspend fun getSystemMetrics(): ApiResult<SystemMetricsResponse> {
        return apiService.getSystemMetrics()
    }
    
    suspend fun getBackupMetrics(): ApiResult<BackupMetrics> {
        return apiService.getSystemMetrics().let { result ->
            when (result) {
                is ApiResult.Success -> ApiResult.Success(result.data.backupMetrics)
                is ApiResult.Error -> result
                ApiResult.Loading -> ApiResult.Loading
            }
        }
    }
    
    suspend fun getStorageUsage(): ApiResult<StorageUsageResponse> {
        val deviceId = deviceManager.getDeviceId()
        return apiService.getStorageUsage(deviceId)
    }
    
    suspend fun getSystemHealth(): ApiResult<ServicesHealthResponse> {
        return apiService.getAllServicesHealth()
    }
    
    suspend fun getAnomalyReport(
        timeRange: TimeRange = TimeRange.LAST_24_HOURS
    ): ApiResult<AnomalyReport> {
        return try {
            val deviceId = deviceManager.getDeviceId()
            val metrics = deviceManager.getCurrentMetrics()
            
            val request = AnomalyDetectionRequest(
                deviceId = deviceId,
                metrics = metrics,
                timestamp = System.currentTimeMillis()
            )
            
            when (val result = apiService.detectAnomalies(request)) {
                is ApiResult.Success -> {
                    val report = AnomalyReport(
                        deviceId = deviceId,
                        timeRange = timeRange,
                        anomalies = listOf(result.data),
                        totalAnomalies = if (result.data.isAnomaly) 1 else 0,
                        timestamp = System.currentTimeMillis()
                    )
                    ApiResult.Success(report)
                }
                is ApiResult.Error -> result
                ApiResult.Loading -> ApiResult.Loading
            }
        } catch (e: Exception) {
            ApiResult.Error(e, "Failed to get anomaly report")
        }
    }
    
    suspend fun getPerformanceReport(
        timeRange: TimeRange = TimeRange.LAST_7_DAYS
    ): ApiResult<PerformanceReport> {
        return try {
            val backupMetrics = getBackupMetrics()
            val systemMetrics = getSystemMetrics()
            
            when {
                backupMetrics is ApiResult.Success && systemMetrics is ApiResult.Success -> {
                    val report = PerformanceReport(
                        timeRange = timeRange,
                        averageBackupDuration = backupMetrics.data.averageBackupDuration,
                        compressionRatio = backupMetrics.data.compressionRatio,
                        deduplicationRatio = backupMetrics.data.deduplicationRatio,
                        successRate = calculateSuccessRate(backupMetrics.data),
                        systemUtilization = systemMetrics.data.systemUtilization,
                        timestamp = System.currentTimeMillis()
                    )
                    ApiResult.Success(report)
                }
                backupMetrics is ApiResult.Error -> backupMetrics
                systemMetrics is ApiResult.Error -> systemMetrics
                else -> ApiResult.Loading
            }
        } catch (e: Exception) {
            ApiResult.Error(e, "Failed to get performance report")
        }
    }
    
    private fun calculateSuccessRate(metrics: BackupMetrics): Double {
        val total = metrics.totalBackupsCompleted + metrics.totalBackupsFailed
        return if (total > 0) {
            metrics.totalBackupsCompleted.toDouble() / total * 100
        } else {
            0.0
        }
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    private val apiService: CoreStateApiService,
    private val deviceManager: DeviceManager,
    private val localPreferences: LocalPreferences
) {
    
    suspend fun getConfiguration(): ApiResult<DaemonConfigResponse> {
        return apiService.getConfiguration()
    }
    
    suspend fun updateConfiguration(config: DaemonConfigRequest): ApiResult<ConfigUpdateResponse> {
        return apiService.updateConfiguration(config)
    }
    
    suspend fun getLocalSettings(): LocalSettings {
        return localPreferences.getSettings()
    }
    
    suspend fun updateLocalSettings(settings: LocalSettings) {
        localPreferences.saveSettings(settings)
    }
    
    suspend fun getNotificationSettings(): NotificationSettings {
        return localPreferences.getNotificationSettings()
    }
    
    suspend fun updateNotificationSettings(settings: NotificationSettings) {
        localPreferences.saveNotificationSettings(settings)
    }
    
    suspend fun getSecuritySettings(): SecuritySettings {
        return localPreferences.getSecuritySettings()
    }
    
    suspend fun updateSecuritySettings(settings: SecuritySettings) {
        localPreferences.saveSecuritySettings(settings)
    }
    
    suspend fun exportConfiguration(): ApiResult<String> {
        return when (val result = getConfiguration()) {
            is ApiResult.Success -> {
                try {
                    val json = kotlinx.serialization.json.Json.encodeToString(result.data)
                    ApiResult.Success(json)
                } catch (e: Exception) {
                    ApiResult.Error(e, "Failed to export configuration")
                }
            }
            is ApiResult.Error -> result
            ApiResult.Loading -> ApiResult.Loading
        }
    }
    
    suspend fun importConfiguration(configJson: String): ApiResult<ConfigUpdateResponse> {
        return try {
            val config = kotlinx.serialization.json.Json.decodeFromString<DaemonConfigRequest>(configJson)
            updateConfiguration(config)
        } catch (e: Exception) {
            ApiResult.Error(e, "Failed to import configuration")
        }
    }
}

// Device management repository
@Singleton
class DeviceRepository @Inject constructor(
    private val apiService: CoreStateApiService,
    private val deviceManager: DeviceManager
) {
    
    suspend fun registerDevice(): ApiResult<DeviceRegistrationResponse> {
        return try {
            val request = DeviceRegistrationRequest(
                deviceId = deviceManager.getDeviceId(),
                deviceInfo = deviceManager.getDeviceInfo(),
                capabilities = deviceManager.getDeviceCapabilities()
            )
            apiService.registerDevice(request)
        } catch (e: Exception) {
            ApiResult.Error(e, "Failed to register device")
        }
    }
    
    suspend fun getRegisteredDevices(): ApiResult<RegisteredDevicesResponse> {
        return apiService.getRegisteredDevices()
    }
    
    suspend fun getConnectedDevices(): ApiResult<ConnectedDevicesResponse> {
        return apiService.getConnectedDevices()
    }
    
    suspend fun getCurrentDevice(): ApiResult<DeviceInfo> {
        return try {
            val deviceInfo = deviceManager.getDeviceInfo()
            ApiResult.Success(deviceInfo)
        } catch (e: Exception) {
            ApiResult.Error(e, "Failed to get current device info")
        }
    }
    
    suspend fun updateDeviceStatus(status: DeviceStatus): ApiResult<Unit> {
        return try {
            deviceManager.updateStatus(status)
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Error(e, "Failed to update device status")
        }
    }
    
    suspend fun getKernelModuleStatus(): ApiResult<KernelStatusResponse> {
        return apiService.getKernelStatus()
    }
    
    suspend fun loadKernelModule(): ApiResult<KernelOperationResponse> {
        return apiService.loadKernelModule()
    }
    
    suspend fun unloadKernelModule(): ApiResult<KernelOperationResponse> {
        return apiService.unloadKernelModule()
    }
}

// Security repository for encryption and key management
@Singleton
class SecurityRepository @Inject constructor(
    private val apiService: CoreStateApiService,
    private val deviceManager: DeviceManager
) {
    
    suspend fun generateDeviceKey(): ApiResult<DeviceKeyInfo> {
        val deviceId = deviceManager.getDeviceId()
        return apiService.generateDeviceKey(deviceId)
    }
    
    suspend fun rotateDeviceKey(): ApiResult<DeviceKeyInfo> {
        val deviceId = deviceManager.getDeviceId()
        return apiService.rotateDeviceKey(deviceId)
    }
    
    suspend fun getDeviceKeys(): ApiResult<DeviceKeysResponse> {
        val deviceId = deviceManager.getDeviceId()
        return apiService.getDeviceKeys(deviceId)
    }
    
    suspend fun encryptData(data: String): ApiResult<EncryptionResult> {
        return try {
            val request = EncryptionRequest(
                data = data,
                deviceId = deviceManager.getDeviceId()
            )
            apiService.encryptData(request)
        } catch (e: Exception) {
            ApiResult.Error(e, "Failed to encrypt data")
        }
    }
    
    suspend fun decryptData(encryptedData: String, keyId: String? = null): ApiResult<DecryptionResult> {
        return try {
            val request = DecryptionRequest(
                encryptedData = encryptedData,
                deviceId = deviceManager.getDeviceId(),
                keyId = keyId
            )
            apiService.decryptData(request)
        } catch (e: Exception) {
            ApiResult.Error(e, "Failed to decrypt data")
        }
    }
}