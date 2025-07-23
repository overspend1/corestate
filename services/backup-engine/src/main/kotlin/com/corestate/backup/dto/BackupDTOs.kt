package com.corestate.backup.dto

import com.corestate.backup.model.*
import java.time.LocalDateTime
import java.util.*

// Request DTOs
data class BackupRequest(
    val deviceId: String,
    val paths: List<String>,
    val backupType: BackupType = BackupType.INCREMENTAL,
    val priority: Int = 1,
    val options: BackupOptions = BackupOptions()
)

data class BackupOptions(
    val compression: Boolean = true,
    val encryption: Boolean = true,
    val excludePatterns: List<String> = emptyList(),
    val includeHidden: Boolean = false,
    val followSymlinks: Boolean = false,
    val maxFileSize: Long = 100 * 1024 * 1024 // 100MB
)

data class RestoreRequest(
    val deviceId: String,
    val snapshotId: String,
    val files: List<String>,
    val targetPath: String,
    val overwriteExisting: Boolean = false,
    val preservePermissions: Boolean = true
)

// Response DTOs
data class BackupJobResponse(
    val jobId: String,
    val deviceId: String,
    val status: JobStatus,
    val createdAt: LocalDateTime,
    val estimatedDuration: Long? = null
) {
    companion object {
        fun fromJob(job: BackupJob): BackupJobResponse {
            return BackupJobResponse(
                jobId = job.id,
                deviceId = job.deviceId,
                status = job.status,
                createdAt = job.createdAt,
                estimatedDuration = job.estimatedDuration
            )
        }
    }
}

data class BackupJobStatus(
    val jobId: String,
    val deviceId: String,
    val status: JobStatus,
    val progress: BackupProgress,
    val startedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val errorMessage: String? = null,
    val statistics: BackupStatistics
)

data class BackupProgress(
    val totalFiles: Long,
    val processedFiles: Long,
    val totalSize: Long,
    val processedSize: Long,
    val currentFile: String? = null,
    val percentage: Double,
    val estimatedTimeRemaining: Long? = null,
    val transferRate: Double = 0.0 // bytes per second
)

data class BackupStatistics(
    val filesProcessed: Long,
    val filesSkipped: Long,
    val filesErrored: Long,
    val totalSize: Long,
    val compressedSize: Long,
    val compressionRatio: Double,
    val deduplicationSavings: Long,
    val duration: Long // milliseconds
)

data class BackupJobListResponse(
    val jobs: List<BackupJobSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class BackupJobSummary(
    val jobId: String,
    val deviceId: String,
    val status: JobStatus,
    val backupType: BackupType,
    val createdAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val fileCount: Long,
    val totalSize: Long,
    val duration: Long?
)

data class RestoreJobResponse(
    val jobId: String,
    val deviceId: String,
    val status: JobStatus,
    val createdAt: LocalDateTime
) {
    companion object {
        fun fromJob(job: RestoreJob): RestoreJobResponse {
            return RestoreJobResponse(
                jobId = job.id,
                deviceId = job.deviceId,
                status = job.status,
                createdAt = job.createdAt
            )
        }
    }
}

data class RestoreJobStatus(
    val jobId: String,
    val deviceId: String,
    val status: JobStatus,
    val progress: RestoreProgress,
    val startedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val errorMessage: String? = null
)

data class RestoreProgress(
    val totalFiles: Long,
    val restoredFiles: Long,
    val totalSize: Long,
    val restoredSize: Long,
    val currentFile: String? = null,
    val percentage: Double,
    val estimatedTimeRemaining: Long? = null
)

data class SnapshotListResponse(
    val snapshots: List<BackupSnapshot>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class BackupSnapshot(
    val id: String,
    val deviceId: String,
    val backupType: BackupType,
    val createdAt: LocalDateTime,
    val fileCount: Long,
    val totalSize: Long,
    val compressedSize: Long,
    val isComplete: Boolean,
    val parentSnapshotId: String? = null
)

data class FileListResponse(
    val path: String,
    val files: List<BackupFileInfo>
)

data class BackupFileInfo(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: LocalDateTime,
    val isDirectory: Boolean,
    val permissions: String?,
    val checksum: String?
)

data class HealthStatus(
    val status: String,
    val timestamp: LocalDateTime,
    val version: String,
    val uptime: Long,
    val services: Map<String, ServiceHealth>
)

data class ServiceHealth(
    val status: String,
    val lastCheck: LocalDateTime,
    val responseTime: Long,
    val errorMessage: String? = null
)

data class BackupMetrics(
    val totalBackupsCompleted: Long,
    val totalBackupsFailed: Long,
    val totalDataBackedUp: Long,
    val compressionRatio: Double,
    val deduplicationRatio: Double,
    val averageBackupDuration: Long,
    val activeJobs: Int,
    val queuedJobs: Int,
    val connectedDevices: Int,
    val storageUtilization: StorageMetrics
)

data class StorageMetrics(
    val totalCapacity: Long,
    val usedSpace: Long,
    val availableSpace: Long,
    val utilizationPercentage: Double
)

// Enums
enum class BackupType {
    FULL,
    INCREMENTAL,
    DIFFERENTIAL
}

enum class JobStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}