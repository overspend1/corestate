package com.corestate.backup.model

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

data class BackupJobExecution(
    val id: String,
    val job: BackupJob,
    val progress: AtomicInteger = AtomicInteger(0),
    val startTime: LocalDateTime = LocalDateTime.now(),
    var endTime: LocalDateTime? = null,
    var status: JobStatus = JobStatus.RUNNING,
    var error: String? = null,
    val processedFiles: MutableList<String> = mutableListOf(),
    val failedFiles: MutableList<String> = mutableListOf()
)

sealed class BackupResult {
    data class Success(
        val jobId: String,
        val snapshotIds: List<String> = emptyList(),
        val processedFiles: Int = 0,
        val totalSize: Long = 0L,
        val duration: Long = 0L
    ) : BackupResult()
    
    data class Failure(
        val jobId: String,
        val error: String,
        val partialResults: Success? = null
    ) : BackupResult()
    
    companion object {
        fun success(jobId: String) = Success(jobId)
        fun failure(jobId: String, error: String) = Failure(jobId, error)
    }
}

data class BackupProgress(
    val jobId: String,
    val progress: Int, // 0-100
    val currentFile: String? = null,
    val processedFiles: Int = 0,
    val totalFiles: Int = 0,
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speed: Long = 0L, // bytes per second
    val eta: Long? = null, // estimated time remaining in seconds
    val stage: BackupStage = BackupStage.SCANNING
)

enum class BackupStage {
    SCANNING,
    CHUNKING,
    COMPRESSING,
    ENCRYPTING,
    DEDUPLICATING,
    STORING,
    FINALIZING,
    COMPLETED
}