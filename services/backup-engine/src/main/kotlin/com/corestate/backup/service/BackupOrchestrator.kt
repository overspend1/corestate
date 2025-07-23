package com.corestate.backup.service

import com.corestate.backup.dto.*
import com.corestate.backup.model.*
import com.corestate.backup.repository.BackupJobRepository
import com.corestate.backup.repository.BackupSnapshotRepository
import com.corestate.backup.client.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
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
                paths = request.paths.toString(), // Convert list to JSON string
                status = JobStatus.QUEUED,
                createdAt = LocalDateTime.now(),
                priority = request.priority
            )
            
            backupJobRepository.save(job)
            
            // Start async processing
            scheduleBackupExecution(job)
            
            job
        }
    }

    fun getJob(jobId: String): Mono<BackupJob?> {
        return Mono.fromCallable {
            backupJobRepository.findById(jobId).orElse(null)
        }
    }

    fun cancelJob(jobId: String): Mono<Unit> {
        return Mono.fromCallable {
            val job = backupJobRepository.findById(jobId).orElse(null)
            if (job != null) {
                job.status = JobStatus.CANCELLED
                backupJobRepository.save(job)
                
                // Cancel active execution if exists
                activeJobs[jobId]?.let { execution ->
                    execution.status = JobStatus.CANCELLED
                    activeJobs.remove(jobId)
                }
            }
            Unit
        }
    }

    fun pauseJob(jobId: String): Mono<Unit> {
        return Mono.fromCallable {
            val job = backupJobRepository.findById(jobId).orElse(null)
            if (job != null && job.status == JobStatus.RUNNING) {
                job.status = JobStatus.PAUSED
                backupJobRepository.save(job)
                
                activeJobs[jobId]?.status = JobStatus.PAUSED
            }
            Unit
        }
    }

    fun resumeJob(jobId: String): Mono<Unit> {
        return Mono.fromCallable {
            val job = backupJobRepository.findById(jobId).orElse(null)
            if (job != null && job.status == JobStatus.PAUSED) {
                job.status = JobStatus.RUNNING
                backupJobRepository.save(job)
                
                activeJobs[jobId]?.status = JobStatus.RUNNING
            }
            Unit
        }
    }

    fun getJobProgress(jobId: String): Flux<BackupProgress> {
        return progressStreams[jobId] ?: Flux.empty()
    }

    fun listJobs(page: Int, size: Int): Mono<List<BackupJob>> {
        return Mono.fromCallable {
            val pageable = PageRequest.of(page, size)
            backupJobRepository.findAll(pageable).content
        }
    }

    fun listJobsByDevice(deviceId: String): Mono<List<BackupJob>> {
        return Mono.fromCallable {
            backupJobRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId)
        }
    }

    private fun scheduleBackupExecution(job: BackupJob) {
        val execution = BackupJobExecution(
            id = job.id,
            job = job
        )
        
        activeJobs[job.id] = execution
        
        // Create progress stream
        val progressStream = createProgressStream(execution)
        progressStreams[job.id] = progressStream
        
        // TODO: Schedule actual backup execution in background
        logger.info("Backup execution scheduled for job: ${job.id}")
    }

    private fun createProgressStream(execution: BackupJobExecution): Flux<BackupProgress> {
        return Flux.interval(java.time.Duration.ofSeconds(1))
            .map { tick ->
                BackupProgress(
                    jobId = execution.id,
                    progress = execution.progress.get(),
                    currentFile = "Processing...",
                    processedFiles = execution.processedFiles.size,
                    totalFiles = 100, // Placeholder
                    processedBytes = 0L,
                    totalBytes = 1000000L, // Placeholder
                    speed = 1000L,
                    eta = null,
                    stage = BackupStage.SCANNING
                )
            }
            .takeUntil { progress -> progress.progress >= 100 }
    }

    suspend fun executeBackupJob(job: BackupJob): BackupResult {
        // Implementation placeholder - this would contain the actual backup logic
        logger.info("Executing backup job: ${job.id}")
        
        return try {
            // Simulate backup process
            val execution = activeJobs[job.id]
            if (execution != null) {
                execution.status = JobStatus.RUNNING
                
                // Update progress incrementally
                for (i in 0..100 step 10) {
                    if (execution.status == JobStatus.CANCELLED) {
                        break
                    }
                    execution.progress.set(i)
                    kotlinx.coroutines.delay(100) // Simulate work
                }
                
                if (execution.status != JobStatus.CANCELLED) {
                    execution.status = JobStatus.COMPLETED
                    execution.endTime = LocalDateTime.now()
                    
                    // Update job in database
                    job.status = JobStatus.COMPLETED
                    job.completedAt = LocalDateTime.now()
                    job.progress = 100
                    backupJobRepository.save(job)
                    
                    BackupResult.success(job.id)
                } else {
                    BackupResult.failure(job.id, "Job was cancelled")
                }
            } else {
                BackupResult.failure(job.id, "Job execution not found")
            }
        } catch (e: Exception) {
            logger.error("Backup job execution failed: ${job.id}", e)
            job.status = JobStatus.FAILED
            job.error = e.message
            backupJobRepository.save(job)
            
            BackupResult.failure(job.id, e.message ?: "Unknown error")
        } finally {
            activeJobs.remove(job.id)
            progressStreams.remove(job.id)
        }
    }
}