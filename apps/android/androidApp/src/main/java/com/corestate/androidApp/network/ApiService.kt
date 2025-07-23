package com.corestate.androidApp.network

import com.corestate.androidApp.data.model.*
import retrofit2.Response
import retrofit2.http.*
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody
import okhttp3.RequestBody

interface BackupEngineApi {
    @POST("api/v1/backup/start")
    suspend fun startBackup(@Body request: BackupRequest): Response<BackupJobResponse>
    
    @GET("api/v1/backup/job/{jobId}")
    suspend fun getJobStatus(@Path("jobId") jobId: String): Response<BackupJobStatus>
    
    @GET("api/v1/backup/job/{jobId}/progress")
    suspend fun getProgress(@Path("jobId") jobId: String): Flow<BackupProgress>
    
    @POST("api/v1/backup/job/{jobId}/pause")
    suspend fun pauseJob(@Path("jobId") jobId: String): Response<Unit>
    
    @POST("api/v1/backup/job/{jobId}/resume")
    suspend fun resumeJob(@Path("jobId") jobId: String): Response<Unit>
    
    @DELETE("api/v1/backup/job/{jobId}")
    suspend fun cancelJob(@Path("jobId") jobId: String): Response<Unit>
    
    @GET("api/v1/backup/jobs")
    suspend fun listJobs(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("deviceId") deviceId: String? = null,
        @Query("status") status: String? = null
    ): Response<BackupJobListResponse>
    
    @POST("api/v1/backup/restore")
    suspend fun startRestore(@Body request: RestoreRequest): Response<RestoreJobResponse>
    
    @GET("api/v1/backup/restore/{jobId}")
    suspend fun getRestoreStatus(@Path("jobId") jobId: String): Response<RestoreJobStatus>
    
    @GET("api/v1/backup/snapshots")
    suspend fun listSnapshots(
        @Query("deviceId") deviceId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<SnapshotListResponse>
    
    @GET("api/v1/backup/snapshot/{snapshotId}/files")
    suspend fun browseSnapshotFiles(
        @Path("snapshotId") snapshotId: String,
        @Query("path") path: String = "/"
    ): Response<FileListResponse>
    
    @GET("api/v1/backup/health")
    suspend fun getHealthStatus(): Response<HealthStatus>
    
    @GET("api/v1/backup/metrics")
    suspend fun getMetrics(): Response<BackupMetrics>
}

interface EncryptionApi {
    @POST("api/v1/encrypt")
    suspend fun encryptData(@Body request: EncryptionRequest): Response<EncryptionResult>
    
    @POST("api/v1/decrypt")
    suspend fun decryptData(@Body request: DecryptionRequest): Response<DecryptionResult>
    
    @POST("api/v1/keys/generate")
    suspend fun generateKey(@Body request: KeyGenerationRequest): Response<DeviceKeyInfo>
    
    @POST("api/v1/keys/rotate")
    suspend fun rotateKey(@Body request: KeyRotationRequest): Response<DeviceKeyInfo>
    
    @GET("api/v1/keys/{deviceId}")
    suspend fun getKeyInfo(@Path("deviceId") deviceId: String): Response<DeviceKeysResponse>
    
    @GET("api/v1/health")
    suspend fun getHealthStatus(): Response<ServiceHealthStatus>
    
    @GET("api/v1/metrics")
    suspend fun getMetrics(): Response<EncryptionMetrics>
}

interface MLOptimizerApi {
    @POST("predict/backup")
    suspend fun predictBackup(@Body request: BackupPredictionRequest): Response<BackupPrediction>
    
    @POST("detect/anomaly")
    suspend fun detectAnomaly(@Body request: AnomalyDetectionRequest): Response<AnomalyResult>
    
    @POST("optimize/schedule")
    suspend fun optimizeSchedule(@Body request: ScheduleOptimizationRequest): Response<OptimizationResult>
    
    @GET("models/status")
    suspend fun getModelStatus(): Response<ModelStatusResponse>
    
    @GET("health")
    suspend fun getHealthStatus(): Response<ServiceHealthStatus>
    
    @GET("metrics")
    suspend fun getMetrics(): Response<MLMetrics>
}

interface SyncCoordinatorApi {
    @GET("health")
    suspend fun getHealthStatus(): Response<ServiceHealthStatus>
    
    @GET("metrics")
    suspend fun getMetrics(): Response<SyncMetrics>
    
    @GET("devices")
    suspend fun getConnectedDevices(): Response<ConnectedDevicesResponse>
    
    @POST("sync/manual")
    suspend fun triggerManualSync(@Body request: ManualSyncRequest): Response<SyncResult>
    
    @GET("sync/status")
    suspend fun getSyncStatus(): Response<SyncStatusResponse>
}

interface StorageHalApi {
    @POST("storage/store")
    suspend fun storeChunk(@Body request: StoreChunkRequest): Response<StorageResult>
    
    @GET("storage/retrieve/{chunkId}")
    suspend fun retrieveChunk(@Path("chunkId") chunkId: String): Response<ChunkData>
    
    @DELETE("storage/delete/{chunkId}")
    suspend fun deleteChunk(@Path("chunkId") chunkId: String): Response<Unit>
    
    @GET("storage/health")
    suspend fun getHealthStatus(): Response<StorageHealthStatus>
    
    @GET("storage/metrics")
    suspend fun getMetrics(): Response<StorageMetrics>
    
    @GET("storage/usage")
    suspend fun getStorageUsage(@Query("deviceId") deviceId: String): Response<StorageUsageResponse>
}

interface CompressionEngineApi {
    @POST("compress")
    suspend fun compressData(@Body request: CompressionRequest): Response<CompressionResult>
    
    @POST("decompress")
    suspend fun decompressData(@Body request: DecompressionRequest): Response<DecompressionResult>
    
    @GET("algorithms")
    suspend fun getSupportedAlgorithms(): Response<CompressionAlgorithmsResponse>
    
    @GET("health")
    suspend fun getHealthStatus(): Response<ServiceHealthStatus>
    
    @GET("metrics")
    suspend fun getMetrics(): Response<CompressionMetrics>
}

interface DeduplicationApi {
    @POST("deduplicate")
    suspend fun deduplicateChunks(@Body request: DeduplicationRequest): Response<DeduplicationResult>
    
    @GET("stats")
    suspend fun getDeduplicationStats(@Query("deviceId") deviceId: String): Response<DeduplicationStats>
    
    @GET("health")
    suspend fun getHealthStatus(): Response<ServiceHealthStatus>
    
    @GET("metrics")
    suspend fun getMetrics(): Response<DeduplicationMetrics>
}

interface DaemonApi {
    @GET("status")
    suspend fun getSystemStatus(): Response<SystemStatusInfo>
    
    @GET("logs")
    suspend fun getLogs(
        @Query("level") level: String = "info",
        @Query("lines") lines: Int = 100
    ): Response<LogDataResponse>
    
    @GET("config")
    suspend fun getConfiguration(): Response<DaemonConfigResponse>
    
    @PUT("config")
    suspend fun updateConfiguration(@Body config: DaemonConfigRequest): Response<ConfigUpdateResponse>
    
    @GET("kernel/status")
    suspend fun getKernelStatus(): Response<KernelStatusResponse>
    
    @POST("kernel/load")
    suspend fun loadKernelModule(): Response<KernelOperationResponse>
    
    @POST("kernel/unload")
    suspend fun unloadKernelModule(): Response<KernelOperationResponse>
    
    @GET("files")
    suspend fun listFiles(@Query("path") path: String): Response<FileListResponse>
    
    @POST("backup/start")
    suspend fun startBackupViaDaemon(@Body request: DaemonBackupRequest): Response<DaemonBackupResponse>
    
    @GET("devices")
    suspend fun getRegisteredDevices(): Response<RegisteredDevicesResponse>
    
    @POST("devices/register")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequest): Response<DeviceRegistrationResponse>
}

// Aggregated API service that combines all microservices
interface CoreStateApiService {
    // Backup operations
    suspend fun startBackup(request: BackupRequest): ApiResult<BackupJobResponse>
    suspend fun getBackupStatus(jobId: String): ApiResult<BackupJobStatus>
    suspend fun getBackupProgress(jobId: String): Flow<BackupProgress>
    suspend fun pauseBackup(jobId: String): ApiResult<Unit>
    suspend fun resumeBackup(jobId: String): ApiResult<Unit>
    suspend fun cancelBackup(jobId: String): ApiResult<Unit>
    suspend fun listBackups(page: Int = 0, size: Int = 20): ApiResult<BackupJobListResponse>
    
    // Restore operations
    suspend fun startRestore(request: RestoreRequest): ApiResult<RestoreJobResponse>
    suspend fun getRestoreStatus(jobId: String): ApiResult<RestoreJobStatus>
    
    // File management
    suspend fun listFiles(path: String): ApiResult<FileListResponse>
    suspend fun browseSnapshot(snapshotId: String, path: String = "/"): ApiResult<FileListResponse>
    suspend fun listSnapshots(deviceId: String, page: Int = 0, size: Int = 20): ApiResult<SnapshotListResponse>
    
    // System management
    suspend fun getSystemStatus(): ApiResult<SystemStatusInfo>
    suspend fun getSystemLogs(level: String = "info", lines: Int = 100): ApiResult<LogDataResponse>
    suspend fun getConfiguration(): ApiResult<DaemonConfigResponse>
    suspend fun updateConfiguration(config: DaemonConfigRequest): ApiResult<ConfigUpdateResponse>
    
    // Kernel module management
    suspend fun getKernelStatus(): ApiResult<KernelStatusResponse>
    suspend fun loadKernelModule(): ApiResult<KernelOperationResponse>
    suspend fun unloadKernelModule(): ApiResult<KernelOperationResponse>
    
    // Device management
    suspend fun getRegisteredDevices(): ApiResult<RegisteredDevicesResponse>
    suspend fun registerDevice(request: DeviceRegistrationRequest): ApiResult<DeviceRegistrationResponse>
    suspend fun getConnectedDevices(): ApiResult<ConnectedDevicesResponse>
    
    // Security operations
    suspend fun encryptData(request: EncryptionRequest): ApiResult<EncryptionResult>
    suspend fun decryptData(request: DecryptionRequest): ApiResult<DecryptionResult>
    suspend fun generateDeviceKey(deviceId: String): ApiResult<DeviceKeyInfo>
    suspend fun rotateDeviceKey(deviceId: String): ApiResult<DeviceKeyInfo>
    suspend fun getDeviceKeys(deviceId: String): ApiResult<DeviceKeysResponse>
    
    // ML and Analytics
    suspend fun predictBackupPerformance(request: BackupPredictionRequest): ApiResult<BackupPrediction>
    suspend fun detectAnomalies(request: AnomalyDetectionRequest): ApiResult<AnomalyResult>
    suspend fun optimizeBackupSchedule(request: ScheduleOptimizationRequest): ApiResult<OptimizationResult>
    suspend fun getMLModelStatus(): ApiResult<ModelStatusResponse>
    
    // Storage operations
    suspend fun getStorageUsage(deviceId: String): ApiResult<StorageUsageResponse>
    suspend fun getStorageMetrics(): ApiResult<StorageMetrics>
    
    // Sync operations
    suspend fun getSyncStatus(): ApiResult<SyncStatusResponse>
    suspend fun triggerManualSync(deviceId: String): ApiResult<SyncResult>
    
    // Health and metrics
    suspend fun getAllServicesHealth(): ApiResult<ServicesHealthResponse>
    suspend fun getSystemMetrics(): ApiResult<SystemMetricsResponse>
    
    // Real-time updates
    fun subscribeToBackupProgress(jobId: String): Flow<BackupProgress>
    fun subscribeToSystemEvents(): Flow<SystemEvent>
    fun subscribeToSyncEvents(): Flow<SyncEvent>
}

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val exception: Throwable, val message: String? = null) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}

// Extension functions for easier result handling
inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(data)
    return this
}

inline fun <T> ApiResult<T>.onError(action: (Throwable, String?) -> Unit): ApiResult<T> {
    if (this is ApiResult.Error) action(exception, message)
    return this
}

inline fun <T> ApiResult<T>.onLoading(action: () -> Unit): ApiResult<T> {
    if (this is ApiResult.Loading) action()
    return this
}