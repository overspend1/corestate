package com.corestate.backup.repository

import com.corestate.backup.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface BackupJobRepository : JpaRepository<BackupJob, String> {
    
    fun findByDeviceIdOrderByCreatedAtDesc(deviceId: String): List<BackupJob>
    
    fun findByStatusIn(statuses: List<JobStatus>): List<BackupJob>
    
    @Query("SELECT bj FROM BackupJob bj WHERE bj.status IN :statuses AND bj.deviceId = :deviceId")
    fun findActiveJobsByDevice(
        @Param("deviceId") deviceId: String,
        @Param("statuses") statuses: List<JobStatus>
    ): List<BackupJob>
    
    @Query("SELECT bj FROM BackupJob bj WHERE bj.createdAt BETWEEN :startTime AND :endTime")
    fun findJobsByTimeRange(
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): List<BackupJob>
    
    fun countByStatusAndDeviceId(status: JobStatus, deviceId: String): Long
}

@Repository
interface BackupSnapshotRepository : JpaRepository<BackupSnapshot, String> {
    
    fun findByDeviceIdOrderByCreatedAtDesc(deviceId: String): List<BackupSnapshot>
    
    fun findByBackupJobId(backupJobId: String): List<BackupSnapshot>
    
    @Query("SELECT bs FROM BackupSnapshot bs WHERE bs.deviceId = :deviceId AND bs.path LIKE :pathPattern")
    fun findSnapshotsByPath(
        @Param("deviceId") deviceId: String,
        @Param("pathPattern") pathPattern: String
    ): List<BackupSnapshot>
    
    @Query("SELECT SUM(bs.size) FROM BackupSnapshot bs WHERE bs.deviceId = :deviceId")
    fun getTotalBackupSizeByDevice(@Param("deviceId") deviceId: String): Long?
    
    @Query("SELECT bs FROM BackupSnapshot bs WHERE bs.createdAt >= :sinceDate AND bs.deviceId = :deviceId")
    fun findRecentSnapshots(
        @Param("deviceId") deviceId: String,
        @Param("sinceDate") sinceDate: LocalDateTime
    ): List<BackupSnapshot>
}

@Repository
interface RestoreJobRepository : JpaRepository<RestoreJob, String> {
    
    fun findByDeviceIdOrderByCreatedAtDesc(deviceId: String): List<RestoreJob>
    
    fun findByStatusIn(statuses: List<JobStatus>): List<RestoreJob>
    
    fun findBySnapshotId(snapshotId: String): List<RestoreJob>
    
    @Query("SELECT rj FROM RestoreJob rj WHERE rj.createdAt BETWEEN :startTime AND :endTime")
    fun findRestoreJobsByTimeRange(
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): List<RestoreJob>
}