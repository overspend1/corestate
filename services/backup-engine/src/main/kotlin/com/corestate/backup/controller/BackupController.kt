package com.corestate.backup.controller

import com.corestate.backup.dto.*
import com.corestate.backup.service.BackupOrchestrator
import com.corestate.backup.service.RestoreService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

@RestController
@RequestMapping("/api/v1/backup")
@Tag(name = "Backup Operations", description = "Core backup and restore operations")
class BackupController(
    private val backupOrchestrator: BackupOrchestrator,
    private val restoreService: RestoreService
) {

    @PostMapping("/start")
    @Operation(summary = "Start a new backup job", description = "Initiates a backup job for specified files/directories")
    fun startBackup(@RequestBody request: BackupRequest): Mono<ResponseEntity<BackupJobResponse>> {
        return backupOrchestrator.startBackup(request)
            .map { job -> ResponseEntity.ok(BackupJobResponse.fromJob(job)) }
    }

    @GetMapping("/job/{jobId}")
    @Operation(summary = "Get backup job status", description = "Retrieves current status and progress of a backup job")
    fun getJobStatus(@PathVariable jobId: String): Mono<ResponseEntity<BackupJobStatus>> {
        return backupOrchestrator.getJobStatus(jobId)
            .map { status -> ResponseEntity.ok(status) }
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
    }

    @GetMapping("/job/{jobId}/progress", produces = ["text/event-stream"])
    @Operation(summary = "Stream backup progress", description = "Server-sent events for real-time backup progress")
    fun streamProgress(@PathVariable jobId: String): Flux<BackupProgress> {
        return backupOrchestrator.streamProgress(jobId)
    }

    @PostMapping("/job/{jobId}/pause")
    @Operation(summary = "Pause backup job", description = "Pauses a running backup job")
    fun pauseJob(@PathVariable jobId: String): Mono<ResponseEntity<Void>> {
        return backupOrchestrator.pauseJob(jobId)
            .map { ResponseEntity.ok().build<Void>() }
    }

    @PostMapping("/job/{jobId}/resume")
    @Operation(summary = "Resume backup job", description = "Resumes a paused backup job")
    fun resumeJob(@PathVariable jobId: String): Mono<ResponseEntity<Void>> {
        return backupOrchestrator.resumeJob(jobId)
            .map { ResponseEntity.ok().build<Void>() }
    }

    @DeleteMapping("/job/{jobId}")
    @Operation(summary = "Cancel backup job", description = "Cancels a running or paused backup job")
    fun cancelJob(@PathVariable jobId: String): Mono<ResponseEntity<Void>> {
        return backupOrchestrator.cancelJob(jobId)
            .map { ResponseEntity.ok().build<Void>() }
    }

    @GetMapping("/jobs")
    @Operation(summary = "List backup jobs", description = "Retrieves list of backup jobs with optional filtering")
    fun listJobs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) deviceId: String?,
        @RequestParam(required = false) status: String?
    ): Mono<ResponseEntity<BackupJobListResponse>> {
        return backupOrchestrator.listJobs(page, size, deviceId, status)
            .map { response -> ResponseEntity.ok(response) }
    }

    @PostMapping("/restore")
    @Operation(summary = "Start file restore", description = "Initiates restoration of files from backup")
    fun startRestore(@RequestBody request: RestoreRequest): Mono<ResponseEntity<RestoreJobResponse>> {
        return restoreService.startRestore(request)
            .map { job -> ResponseEntity.ok(RestoreJobResponse.fromJob(job)) }
    }

    @GetMapping("/restore/{jobId}")
    @Operation(summary = "Get restore job status", description = "Retrieves current status of a restore job")
    fun getRestoreStatus(@PathVariable jobId: String): Mono<ResponseEntity<RestoreJobStatus>> {
        return restoreService.getRestoreStatus(jobId)
            .map { status -> ResponseEntity.ok(status) }
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
    }

    @GetMapping("/snapshots")
    @Operation(summary = "List backup snapshots", description = "Retrieves available backup snapshots for a device")
    fun listSnapshots(
        @RequestParam deviceId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): Mono<ResponseEntity<SnapshotListResponse>> {
        return backupOrchestrator.listSnapshots(deviceId, page, size)
            .map { response -> ResponseEntity.ok(response) }
    }

    @GetMapping("/snapshot/{snapshotId}/files")
    @Operation(summary = "Browse snapshot files", description = "Browse files within a specific backup snapshot")
    fun browseSnapshotFiles(
        @PathVariable snapshotId: String,
        @RequestParam(defaultValue = "/") path: String
    ): Mono<ResponseEntity<FileListResponse>> {
        return backupOrchestrator.browseSnapshotFiles(snapshotId, path)
            .map { response -> ResponseEntity.ok(response) }
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Service health status")
    fun healthCheck(): Mono<ResponseEntity<HealthStatus>> {
        return backupOrchestrator.getHealthStatus()
            .map { status -> ResponseEntity.ok(status) }
    }

    @GetMapping("/metrics")
    @Operation(summary = "Backup metrics", description = "Retrieve backup system metrics")
    fun getMetrics(): Mono<ResponseEntity<BackupMetrics>> {
        return backupOrchestrator.getMetrics()
            .map { metrics -> ResponseEntity.ok(metrics) }
    }
}