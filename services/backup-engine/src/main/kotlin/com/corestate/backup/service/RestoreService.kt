package com.corestate.backup.service

import com.corestate.backup.client.*
import com.corestate.backup.dto.*
import com.corestate.backup.repository.BackupJobRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

interface RestoreService {
    fun startRestore(request: RestoreRequest): Mono<RestoreJobResponse>
    fun getRestoreStatus(jobId: String): Mono<RestoreJobStatus>
    fun cancelRestore(jobId: String): Mono<Unit>
    fun streamRestoreProgress(jobId: String): Flux<RestoreProgress>
}

/**
 * Restore Service Implementation
 *
 * Handles the complete restoration of files from backup:
 * 1. Retrieves chunks from storage
 * 2. Decrypts data
 * 3. Decompresses data
 * 4. Reconstructs original files
 * 5. Restores to target location
 */
@Service
class RestoreServiceImpl(
    private val backupJobRepository: BackupJobRepository,
    private val storageHalClient: StorageHalClient,
    private val encryptionClient: EncryptionServiceClient,
    private val compressionClient: CompressionEngineClient,
    private val deduplicationClient: DeduplicationServiceClient,
    private val syncCoordinatorClient: SyncCoordinatorClient
) : RestoreService {

    // Track active restore jobs
    private val activeRestoreJobs = ConcurrentHashMap<String, RestoreJobInfo>()

    // Progress sinks for streaming
    private val progressSinks = ConcurrentHashMap<String, Sinks.Many<RestoreProgress>>()

    data class RestoreJobInfo(
        val jobId: String,
        val request: RestoreRequest,
        val status: String,
        val progress: Int,
        val startTime: Long,
        var endTime: Long?,
        var error: String?,
        var filesRestored: Int = 0,
        var totalFiles: Int = 0,
        var bytesRestored: Long = 0,
        var totalBytes: Long = 0,
        var cancelled: Boolean = false
    )

    override fun startRestore(request: RestoreRequest): Mono<RestoreJobResponse> {
        val jobId = "restore-${System.currentTimeMillis()}-${(0..9999).random()}"
        logger.info { "Starting restore job: $jobId for backup: ${request.backupJobId}" }

        // Create job info
        val jobInfo = RestoreJobInfo(
            jobId = jobId,
            request = request,
            status = "STARTED",
            progress = 0,
            startTime = System.currentTimeMillis(),
            endTime = null,
            error = null
        )
        activeRestoreJobs[jobId] = jobInfo

        // Create progress sink
        val progressSink = Sinks.many().multicast().onBackpressureBuffer<RestoreProgress>()
        progressSinks[jobId] = progressSink

        // Execute restore asynchronously
        executeRestore(jobId, request)
            .doOnSuccess {
                logger.info { "Restore job completed successfully: $jobId" }
                jobInfo.status = "COMPLETED"
                jobInfo.progress = 100
                jobInfo.endTime = System.currentTimeMillis()

                progressSink.tryEmitNext(RestoreProgress(
                    jobId = jobId,
                    progress = 100,
                    status = "COMPLETED",
                    message = "Restore completed successfully",
                    filesRestored = jobInfo.filesRestored,
                    totalFiles = jobInfo.totalFiles,
                    bytesRestored = jobInfo.bytesRestored
                ))
                progressSink.tryEmitComplete()
            }
            .doOnError { error ->
                logger.error(error) { "Restore job failed: $jobId" }
                jobInfo.status = "FAILED"
                jobInfo.error = error.message
                jobInfo.endTime = System.currentTimeMillis()

                progressSink.tryEmitNext(RestoreProgress(
                    jobId = jobId,
                    progress = jobInfo.progress,
                    status = "FAILED",
                    message = "Restore failed: ${error.message}",
                    filesRestored = jobInfo.filesRestored,
                    totalFiles = jobInfo.totalFiles,
                    bytesRestored = jobInfo.bytesRestored
                ))
                progressSink.tryEmitComplete()
            }
            .subscribe()

        return Mono.just(
            RestoreJobResponse(
                jobId = jobId,
                status = "STARTED",
                message = "Restore job started successfully"
            )
        )
    }

    override fun getRestoreStatus(jobId: String): Mono<RestoreJobStatus> {
        logger.debug { "Getting restore status for job: $jobId" }

        val jobInfo = activeRestoreJobs[jobId]
            ?: return Mono.just(RestoreJobStatus(
                jobId = jobId,
                status = "NOT_FOUND",
                progress = 0,
                startTime = 0,
                endTime = null,
                error = "Restore job not found"
            ))

        return Mono.just(RestoreJobStatus(
            jobId = jobInfo.jobId,
            status = jobInfo.status,
            progress = jobInfo.progress,
            startTime = jobInfo.startTime,
            endTime = jobInfo.endTime,
            error = jobInfo.error,
            filesRestored = jobInfo.filesRestored,
            totalFiles = jobInfo.totalFiles,
            bytesRestored = jobInfo.bytesRestored,
            totalBytes = jobInfo.totalBytes
        ))
    }

    override fun cancelRestore(jobId: String): Mono<Unit> {
        logger.info { "Cancelling restore job: $jobId" }

        val jobInfo = activeRestoreJobs[jobId]
            ?: return Mono.error(RuntimeException("Restore job not found: $jobId"))

        if (jobInfo.status == "COMPLETED" || jobInfo.status == "FAILED") {
            return Mono.error(RuntimeException("Cannot cancel restore job in status: ${jobInfo.status}"))
        }

        jobInfo.cancelled = true
        jobInfo.status = "CANCELLED"
        jobInfo.endTime = System.currentTimeMillis()

        progressSinks[jobId]?.tryEmitNext(RestoreProgress(
            jobId = jobId,
            progress = jobInfo.progress,
            status = "CANCELLED",
            message = "Restore cancelled by user",
            filesRestored = jobInfo.filesRestored,
            totalFiles = jobInfo.totalFiles,
            bytesRestored = jobInfo.bytesRestored
        ))
        progressSinks[jobId]?.tryEmitComplete()

        return Mono.just(Unit)
    }

    override fun streamRestoreProgress(jobId: String): Flux<RestoreProgress> {
        logger.debug { "Streaming restore progress for job: $jobId" }

        val progressSink = progressSinks[jobId]
            ?: return Flux.error(RuntimeException("Restore job not found: $jobId"))

        return progressSink.asFlux()
    }

    /**
     * Execute the actual restore operation
     */
    private fun executeRestore(jobId: String, request: RestoreRequest): Mono<Unit> {
        logger.info { "Executing restore for job: $jobId" }

        val jobInfo = activeRestoreJobs[jobId]!!
        val progressSink = progressSinks[jobId]!!

        return backupJobRepository.findById(request.backupJobId)
            .flatMap { backupJob ->
                // Get backup metadata
                val backupMetadata = backupJob.metadata ?: emptyMap()
                val fileManifest = backupMetadata["fileManifest"] as? List<Map<String, Any>>
                    ?: return@flatMap Mono.error<Unit>(RuntimeException("File manifest not found in backup"))

                jobInfo.totalFiles = fileManifest.size
                jobInfo.totalBytes = fileManifest.sumOf { (it["size"] as? Number)?.toLong() ?: 0L }

                logger.info { "Restoring ${jobInfo.totalFiles} files, total size: ${jobInfo.totalBytes} bytes" }

                // Restore each file
                Flux.fromIterable(fileManifest)
                    .flatMap { fileEntry ->
                        if (jobInfo.cancelled) {
                            return@flatMap Mono.empty<Unit>()
                        }

                        restoreFile(jobId, fileEntry, request.targetPath)
                            .doOnSuccess {
                                jobInfo.filesRestored++
                                jobInfo.bytesRestored += (fileEntry["size"] as? Number)?.toLong() ?: 0L
                                jobInfo.progress = ((jobInfo.filesRestored.toDouble() / jobInfo.totalFiles) * 100).toInt()

                                // Emit progress
                                progressSink.tryEmitNext(RestoreProgress(
                                    jobId = jobId,
                                    progress = jobInfo.progress,
                                    status = "IN_PROGRESS",
                                    message = "Restored file: ${fileEntry["fileName"]}",
                                    filesRestored = jobInfo.filesRestored,
                                    totalFiles = jobInfo.totalFiles,
                                    bytesRestored = jobInfo.bytesRestored,
                                    currentFile = fileEntry["fileName"] as? String
                                ))

                                logger.debug { "Restored file ${jobInfo.filesRestored}/${jobInfo.totalFiles}: ${fileEntry["fileName"]}" }
                            }
                    }
                    .then(Mono.fromCallable { Unit })
            }
            .doOnSuccess {
                // Notify sync coordinator
                syncCoordinatorClient.notifyBackupCompleted(
                    jobId,
                    mapOf(
                        "restoreJobId" to jobId,
                        "backupJobId" to request.backupJobId,
                        "filesRestored" to jobInfo.filesRestored,
                        "bytesRestored" to jobInfo.bytesRestored
                    )
                ).subscribe()
            }
    }

    /**
     * Restore a single file
     */
    private fun restoreFile(jobId: String, fileEntry: Map<String, Any>, targetBasePath: String): Mono<Unit> {
        val fileName = fileEntry["fileName"] as? String
            ?: return Mono.error(RuntimeException("File name not found in manifest"))

        val filePath = fileEntry["filePath"] as? String ?: fileName
        val chunks = fileEntry["chunks"] as? List<String>
            ?: return Mono.error(RuntimeException("Chunks not found for file: $fileName"))

        val encryptionKey = fileEntry["encryptionKey"] as? String ?: "default-key"
        val compressionAlgo = fileEntry["compressionAlgorithm"] as? String ?: "zstd"

        logger.debug { "Restoring file: $fileName (${chunks.size} chunks)" }

        // Restore each chunk and concatenate
        return Flux.fromIterable(chunks)
            .flatMapSequential { chunkId ->
                restoreChunk(chunkId, encryptionKey, compressionAlgo)
            }
            .collectList()
            .flatMap { chunkDataList ->
                // Concatenate all chunks
                val totalSize = chunkDataList.sumOf { it.size }
                val fullData = ByteArray(totalSize)
                var offset = 0
                chunkDataList.forEach { chunkData ->
                    System.arraycopy(chunkData, 0, fullData, offset, chunkData.size)
                    offset += chunkData.size
                }

                // Write to target file
                val targetFile = Paths.get(targetBasePath, filePath)
                Files.createDirectories(targetFile.parent)
                Files.write(targetFile, fullData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

                // Restore file attributes if available
                val permissions = fileEntry["permissions"] as? String
                val modifiedTime = fileEntry["modifiedTime"] as? Long

                logger.info { "Successfully restored file: $targetFile (${fullData.size} bytes)" }

                Mono.fromCallable { Unit }
            }
    }

    /**
     * Restore a single chunk (retrieve, decrypt, decompress)
     */
    private fun restoreChunk(chunkId: String, encryptionKey: String, compressionAlgo: String): Mono<ByteArray> {
        logger.debug { "Restoring chunk: $chunkId" }

        return storageHalClient.retrieveChunk(chunkId)
            .flatMap { encryptedCompressed ->
                // Decrypt
                encryptionClient.decryptData(encryptedCompressed, encryptionKey)
            }
            .flatMap { compressed ->
                // Decompress
                compressionClient.decompressData(compressed, compressionAlgo)
            }
            .doOnSuccess { data ->
                logger.debug { "Successfully restored chunk: $chunkId (${data.size} bytes)" }
            }
            .onErrorResume { error ->
                logger.error(error) { "Failed to restore chunk: $chunkId" }
                Mono.error(error)
            }
    }
}

// Extended DTO for restore progress with more details
data class RestoreProgress(
    val jobId: String,
    val progress: Int,
    val status: String,
    val message: String,
    val filesRestored: Int,
    val totalFiles: Int,
    val bytesRestored: Long,
    val currentFile: String? = null
)
