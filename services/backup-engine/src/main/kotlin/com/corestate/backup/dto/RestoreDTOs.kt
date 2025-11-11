package com.corestate.backup.dto

data class RestoreRequest(
    val backupJobId: String,
    val targetPath: String,
    val files: List<String>? = null, // If null, restore all files
    val overwrite: Boolean = true,
    val verifyIntegrity: Boolean = true
)

data class RestoreJobResponse(
    val jobId: String,
    val status: String,
    val message: String
)

data class RestoreJobStatus(
    val jobId: String,
    val status: String,
    val progress: Int,
    val startTime: Long,
    val endTime: Long?,
    val error: String?,
    val filesRestored: Int = 0,
    val totalFiles: Int = 0,
    val bytesRestored: Long = 0,
    val totalBytes: Long = 0
)
