package com.corestate.backup.service

import com.corestate.backup.dto.*
import com.corestate.backup.model.*
import com.corestate.backup.repository.BackupJobRepository
import com.corestate.backup.repository.BackupSnapshotRepository
import com.corestate.backup.client.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

@Service
@Transactional
class BackupOrchestrator(
    private val backupJobRepository: BackupJobRepository,
    private val snapshotRepository: BackupSnapshotRepository,
    private val fileSystemService: FileSystemService,
    private val chunkingService: ChunkingService,
    private val compressionClient: CompressionEngineClient,
    private val encryptionClient: EncryptionServiceClient,
    private val deduplicationClient: DeduplicationServiceClient,
    private val storageClient: StorageHalClient,
    private val mlOptimizerClient: MLOptimizerClient,
    private val syncCoordinatorClient: SyncCoordinatorClient
) {
    private val logger = LoggerFactory.getLogger(BackupOrchestrator::class.java)
    private val activeJobs = ConcurrentHashMap<String, BackupJobExecution>()
    private val progressStreams = ConcurrentHashMap<String, Flux<BackupProgress>>()

    fun startBackup(request: BackupRequest): Mono<BackupJob> {
        return Mono.fromCallable {
            logger.info("Starting backup for device: ${request.deviceId}")
            
            val job = BackupJob(
                id = UUID.randomUUID().toString(),
                deviceId = request.deviceId,
                backupType = request.backupType,
                paths = request.paths,
                options = request.options,
                status = JobStatus.QUEUED,
                createdAt = LocalDateTime.now(),
                priority = request.priority
            )
            
            backupJobRepository.save(job)
        }
        .subscribeOn(Schedulers.boundedElastic())
        .doOnSuccess { job ->
            // Start backup execution asynchronously
            executeBackup(job).subscribe(
                { result -> logger.info("Backup completed: ${job.id}") },
                { error -> logger.error("Backup failed: ${job.id}", error) }
            )
        }
    }

    private fun executeBackup(job: BackupJob): Mono<BackupResult> {
        return Mono.fromCallable {
            logger.info("Executing backup job: ${job.id}")
            
            val execution = BackupJobExecution(job)
            activeJobs[job.id] = execution
            
            // Update job status to running
            job.status = JobStatus.RUNNING
            job.startedAt = LocalDateTime.now()
            backupJobRepository.save(job)
            
            execution
        }
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap { execution ->
            performBackupSteps(execution)
        }
        .doOnSuccess { result ->
            val job = result.job
            job.status = if (result.success) JobStatus.COMPLETED else JobStatus.FAILED
            job.completedAt = LocalDateTime.now()
            job.statistics = result.statistics
            backupJobRepository.save(job)
            activeJobs.remove(job.id)
        }
        .doOnError { error ->
            val job = activeJobs[job.id]?.job
            if (job != null) {
                job.status = JobStatus.FAILED
                job.completedAt = LocalDateTime.now()
                job.errorMessage = error.message
                backupJobRepository.save(job)
                activeJobs.remove(job.id)
            }
        }
    }

    private fun performBackupSteps(execution: BackupJobExecution): Mono<BackupResult> {
        return scanFiles(execution)
            .flatMap { chunkFiles(execution) }
            .flatMap { compressChunks(execution) }
            .flatMap { encryptChunks(execution) }
            .flatMap { deduplicateChunks(execution) }
            .flatMap { storeChunks(execution) }
            .flatMap { createSnapshot(execution) }
            .flatMap { updateSyncState(execution) }
            .map { BackupResult(execution.job, true, execution.statistics) }
    }

    private fun scanFiles(execution: BackupJobExecution): Mono<BackupJobExecution> {
        return fileSystemService.scanPaths(execution.job.paths, execution.job.options)
            .doOnNext { fileInfo ->
                execution.addFile(fileInfo)
                updateProgress(execution)
            }
            .then(Mono.just(execution))
    }

    private fun chunkFiles(execution: BackupJobExecution): Mono<BackupJobExecution> {
        return Flux.fromIterable(execution.files)
            .flatMap { fileInfo ->
                chunkingService.chunkFile(fileInfo, execution.job.options)
                    .doOnNext { chunk ->
                        execution.addChunk(chunk)
                        updateProgress(execution)
                    }
            }
            .then(Mono.just(execution))
    }

    private fun compressChunks(execution: BackupJobExecution): Mono<BackupJobExecution> {
        if (!execution.job.options.compression) {
            return Mono.just(execution)
        }

        return Flux.fromIterable(execution.chunks)
            .flatMap { chunk ->
                compressionClient.compressChunk(chunk)
                    .doOnNext { compressedChunk ->
                        execution.updateChunk(compressedChunk)
                        updateProgress(execution)
                    }
            }
            .then(Mono.just(execution))
    }

    private fun encryptChunks(execution: BackupJobExecution): Mono<BackupJobExecution> {
        if (!execution.job.options.encryption) {
            return Mono.just(execution)
        }

        return Flux.fromIterable(execution.chunks)
            .flatMap { chunk ->
                encryptionClient.encryptChunk(chunk, execution.job.deviceId)
                    .doOnNext { encryptedChunk ->
                        execution.updateChunk(encryptedChunk)
                        updateProgress(execution)
                    }
            }
            .then(Mono.just(execution))
    }

    private fun deduplicateChunks(execution: BackupJobExecution): Mono<BackupJobExecution> {
        return deduplicationClient.deduplicateChunks(execution.chunks)
            .doOnNext { deduplicationResult ->
                execution.applyDeduplication(deduplicationResult)
                updateProgress(execution)
            }
            .then(Mono.just(execution))
    }

    private fun storeChunks(execution: BackupJobExecution): Mono<BackupJobExecution> {
        return Flux.fromIterable(execution.uniqueChunks)
            .flatMap { chunk ->
                storageClient.storeChunk(chunk)
                    .doOnNext { storageResult ->
                        execution.addStorageResult(storageResult)
                        updateProgress(execution)
                    }
            }
            .then(Mono.just(execution))
    }

    private fun createSnapshot(execution: BackupJobExecution): Mono<BackupJobExecution> {
        return Mono.fromCallable {
            val snapshot = BackupSnapshot(
                id = UUID.randomUUID().toString(),
                deviceId = execution.job.deviceId,
                jobId = execution.job.id,
                backupType = execution.job.backupType,
                createdAt = LocalDateTime.now(),
                fileCount = execution.files.size.toLong(),
                totalSize = execution.files.sumOf { it.size },
                compressedSize = execution.chunks.sumOf { it.compressedSize ?: it.size },
                isComplete = true,
                parentSnapshotId = findParentSnapshot(execution.job.deviceId, execution.job.backupType)
            )
            
            snapshotRepository.save(snapshot)
            execution.snapshot = snapshot
            execution
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun updateSyncState(execution: BackupJobExecution): Mono<BackupJobExecution> {
        return syncCoordinatorClient.updateBackupState(
            execution.job.deviceId,
            execution.snapshot!!,
            execution.files
        ).then(Mono.just(execution))
    }

    private fun findParentSnapshot(deviceId: String, backupType: BackupType): String? {
        if (backupType == BackupType.FULL) return null
        
        return snapshotRepository.findLatestByDeviceId(deviceId)?.id
    }

    private fun updateProgress(execution: BackupJobExecution) {
        val progress = execution.calculateProgress()
        
        // Update statistics
        execution.statistics.apply {
            filesProcessed = execution.processedFiles.toLong()
            totalSize = execution.files.sumOf { it.size }
            compressedSize = execution.chunks.sumOf { it.compressedSize ?: it.size }
            compressionRatio = if (totalSize > 0) compressedSize.toDouble() / totalSize else 1.0
        }
        
        // Emit progress to subscribers
        progressStreams[execution.job.id]?.let { stream ->
            // This would normally use a hot publisher like a Subject
            // For now, we'll update the job record
            execution.job.progress = progress
            backupJobRepository.save(execution.job)
        }
    }

    fun getJobStatus(jobId: String): Mono<BackupJobStatus> {
        return Mono.fromCallable {
            val job = backupJobRepository.findById(jobId).orElse(null)
                ?: return@fromCallable null
            
            val execution = activeJobs[jobId]
            val progress = execution?.calculateProgress() ?: BackupProgress(
                totalFiles = 0,
                processedFiles = 0,
                totalSize = 0,
                processedSize = 0,
                percentage = if (job.status == JobStatus.COMPLETED) 100.0 else 0.0
            )
            
            BackupJobStatus(
                jobId = job.id,
                deviceId = job.deviceId,
                status = job.status,
                progress = progress,
                startedAt = job.startedAt,
                completedAt = job.completedAt,
                errorMessage = job.errorMessage,
                statistics = job.statistics ?: BackupStatistics(0, 0, 0, 0, 0, 1.0, 0, 0)
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    fun streamProgress(jobId: String): Flux<BackupProgress> {
        return progressStreams.computeIfAbsent(jobId) {
            Flux.interval(java.time.Duration.ofSeconds(1))
                .map {
                    activeJobs[jobId]?.calculateProgress() ?: BackupProgress(
                        totalFiles = 0,
                        processedFiles = 0,
                        totalSize = 0,
                        processedSize = 0,
                        percentage = 0.0
                    )
                }
                .takeUntil { progress ->
                    val job = activeJobs[jobId]?.job
                    job?.status == JobStatus.COMPLETED || job?.status == JobStatus.FAILED
                }
                .doFinally { progressStreams.remove(jobId) }
        }
    }

    fun pauseJob(jobId: String): Mono<Void> {
        return Mono.fromRunnable {
            activeJobs[jobId]?.let { execution ->
                execution.job.status = JobStatus.PAUSED
                backupJobRepository.save(execution.job)
                execution.pause()
            }
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    fun resumeJob(jobId: String): Mono<Void> {
        return Mono.fromRunnable {
            activeJobs[jobId]?.let { execution ->
                execution.job.status = JobStatus.RUNNING
                backupJobRepository.save(execution.job)
                execution.resume()
            }
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    fun cancelJob(jobId: String): Mono<Void> {
        return Mono.fromRunnable {
            activeJobs[jobId]?.let { execution ->
                execution.job.status = JobStatus.CANCELLED
                execution.job.completedAt = LocalDateTime.now()
                backupJobRepository.save(execution.job)
                execution.cancel()
                activeJobs.remove(jobId)
            }
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    fun listJobs(page: Int, size: Int, deviceId: String?, status: String?): Mono<BackupJobListResponse> {
        return Mono.fromCallable {
            val pageRequest = PageRequest.of(page, size)
            val jobsPage = when {
                deviceId != null && status != null -> 
                    backupJobRepository.findByDeviceIdAndStatus(deviceId, JobStatus.valueOf(status), pageRequest)
                deviceId != null -> 
                    backupJobRepository.findByDeviceId(deviceId, pageRequest)
                status != null -> 
                    backupJobRepository.findByStatus(JobStatus.valueOf(status), pageRequest)
                else -> 
                    backupJobRepository.findAll(pageRequest)
            }
            
            val jobs = jobsPage.content.map { job ->
                BackupJobSummary(
                    jobId = job.id,
                    deviceId = job.deviceId,
                    status = job.status,
                    backupType = job.backupType,
                    createdAt = job.createdAt,
                    completedAt = job.completedAt,
                    fileCount = job.statistics?.filesProcessed ?: 0,
                    totalSize = job.statistics?.totalSize ?: 0,
                    duration = job.completedAt?.let { 
                        java.time.Duration.between(job.startedAt ?: job.createdAt, it).toMillis() 
                    }
                )
            }
            
            BackupJobListResponse(
                jobs = jobs,
                page = page,
                size = size,
                totalElements = jobsPage.totalElements,
                totalPages = jobsPage.totalPages
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    fun listSnapshots(deviceId: String, page: Int, size: Int): Mono<SnapshotListResponse> {
        return Mono.fromCallable {
            val pageRequest = PageRequest.of(page, size)
            val snapshotsPage = snapshotRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId, pageRequest)
            
            val snapshots = snapshotsPage.content.map { snapshot ->
                BackupSnapshot(
                    id = snapshot.id,
                    deviceId = snapshot.deviceId,
                    backupType = snapshot.backupType,
                    createdAt = snapshot.createdAt,
                    fileCount = snapshot.fileCount,
                    totalSize = snapshot.totalSize,
                    compressedSize = snapshot.compressedSize,
                    isComplete = snapshot.isComplete,
                    parentSnapshotId = snapshot.parentSnapshotId
                )
            }
            
            SnapshotListResponse(
                snapshots = snapshots,
                page = page,
                size = size,
                totalElements = snapshotsPage.totalElements,
                totalPages = snapshotsPage.totalPages
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    fun browseSnapshotFiles(snapshotId: String, path: String): Mono<FileListResponse> {
        return Mono.fromCallable {
            val snapshot = snapshotRepository.findById(snapshotId).orElse(null)
                ?: throw IllegalArgumentException("Snapshot not found: $snapshotId")
            
            // This would typically query a file index or metadata store
            val files = listOf<BackupFileInfo>() // Placeholder
            
            FileListResponse(path = path, files = files)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    fun getHealthStatus(): Mono<HealthStatus> {
        return Mono.fromCallable {
            HealthStatus(
                status = "UP",
                timestamp = LocalDateTime.now(),
                version = "2.0.0",
                uptime = System.currentTimeMillis(), // Simplified
                services = mapOf(
                    "compression" to ServiceHealth("UP", LocalDateTime.now(), 50),
                    "encryption" to ServiceHealth("UP", LocalDateTime.now(), 30),
                    "storage" to ServiceHealth("UP", LocalDateTime.now(), 100),
                    "deduplication" to ServiceHealth("UP", LocalDateTime.now(), 75)
                )
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    fun getMetrics(): Mono<BackupMetrics> {
        return Mono.fromCallable {
            val totalCompleted = backupJobRepository.countByStatus(JobStatus.COMPLETED)
            val totalFailed = backupJobRepository.countByStatus(JobStatus.FAILED)
            
            BackupMetrics(
                totalBackupsCompleted = totalCompleted,
                totalBackupsFailed = totalFailed,
                totalDataBackedUp = 0, // Would calculate from snapshots
                compressionRatio = 0.7,
                deduplicationRatio = 0.3,
                averageBackupDuration = 0, // Would calculate from job history
                activeJobs = activeJobs.size,
                queuedJobs = backupJobRepository.countByStatus(JobStatus.QUEUED).toInt(),
                connectedDevices = 0, // Would get from device registry
                storageUtilization = StorageMetrics(0, 0, 0, 0.0)
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }
}