package com.corestate.backup.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

enum class BackupType {
    FULL, INCREMENTAL, DIFFERENTIAL
}

enum class JobStatus {
    QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
}

@Entity
@Table(name = "backup_jobs")
data class BackupJob(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String = UUID.randomUUID().toString(),
    
    @Column(nullable = false)
    val deviceId: String,
    
    @Column(nullable = false)
    val paths: String, // JSON serialized list
    
    @Enumerated(EnumType.STRING)
    val backupType: BackupType,
    
    @Enumerated(EnumType.STRING)
    var status: JobStatus = JobStatus.QUEUED,
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    var startedAt: LocalDateTime? = null,
    
    var completedAt: LocalDateTime? = null,
    
    val estimatedDuration: Long? = null,
    
    var progress: Int = 0,
    
    var error: String? = null,
    
    val priority: Int = 1,
    
    val options: String? = null // JSON serialized BackupOptions
)

@Entity  
@Table(name = "restore_jobs")
data class RestoreJob(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String = UUID.randomUUID().toString(),
    
    @Column(nullable = false)
    val deviceId: String,
    
    @Column(nullable = false)
    val snapshotId: String,
    
    @Column(nullable = false)
    val files: String, // JSON serialized list
    
    @Column(nullable = false)
    val targetPath: String,
    
    @Enumerated(EnumType.STRING)
    var status: JobStatus = JobStatus.QUEUED,
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    var startedAt: LocalDateTime? = null,
    
    var completedAt: LocalDateTime? = null,
    
    var progress: Int = 0,
    
    var error: String? = null,
    
    val overwriteExisting: Boolean = false,
    
    val preservePermissions: Boolean = true
)

@Entity
@Table(name = "backup_snapshots")
data class BackupSnapshot(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String = UUID.randomUUID().toString(),
    
    @Column(nullable = false)
    val deviceId: String,
    
    @Column(nullable = false)
    val backupJobId: String,
    
    @Column(nullable = false)
    val path: String,
    
    @Column(nullable = false)
    val size: Long,
    
    @Column(nullable = false)
    val checksum: String,
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Enumerated(EnumType.STRING)
    val backupType: BackupType,
    
    val metadata: String? = null // JSON serialized metadata
)