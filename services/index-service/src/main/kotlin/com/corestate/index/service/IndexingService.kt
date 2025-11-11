package com.corestate.index.service

import com.corestate.index.model.FileIndex
import com.corestate.index.repository.FileIndexRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Async
import java.time.Instant
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

/**
 * Service for indexing files and managing search indices
 */
@Service
class IndexingService(
    private val repository: FileIndexRepository,
    private val meterRegistry: MeterRegistry
) {

    private val indexTimer = Timer.builder("index.file.duration")
        .description("Time taken to index a file")
        .register(meterRegistry)

    private val batchIndexTimer = Timer.builder("index.batch.duration")
        .description("Time taken to index a batch of files")
        .register(meterRegistry)

    /**
     * Index a single file
     */
    fun indexFile(fileIndex: FileIndex): FileIndex {
        return indexTimer.recordCallable {
            logger.info { "Indexing file: ${fileIndex.fileId} - ${fileIndex.fileName}" }

            val indexed = repository.save(fileIndex)

            meterRegistry.counter("index.file.indexed",
                "tenant_id", fileIndex.tenantId,
                "file_extension", fileIndex.fileExtension
            ).increment()

            logger.info { "Successfully indexed file: ${fileIndex.fileId}" }
            indexed
        }!!
    }

    /**
     * Index multiple files asynchronously
     */
    @Async
    fun indexFilesAsync(files: List<FileIndex>): CompletableFuture<List<FileIndex>> {
        return CompletableFuture.supplyAsync {
            batchIndexFiles(files)
        }
    }

    /**
     * Index a batch of files
     */
    fun batchIndexFiles(files: List<FileIndex>): List<FileIndex> {
        return batchIndexTimer.recordCallable {
            logger.info { "Batch indexing ${files.size} files" }

            val indexed = repository.saveAll(files).toList()

            // Update metrics
            files.groupBy { it.tenantId }.forEach { (tenantId, tenantFiles) ->
                meterRegistry.counter("index.batch.indexed",
                    "tenant_id", tenantId
                ).increment(tenantFiles.size.toDouble())
            }

            logger.info { "Successfully indexed ${indexed.size} files" }
            indexed
        }!!
    }

    /**
     * Update file index
     */
    fun updateFile(fileIndex: FileIndex): FileIndex {
        logger.info { "Updating index for file: ${fileIndex.fileId}" }

        val updated = fileIndex.copy(
            indexedAt = Instant.now(),
            version = fileIndex.version + 1
        )

        return repository.save(updated)
    }

    /**
     * Delete file from index
     */
    fun deleteFile(fileId: String) {
        logger.info { "Deleting file from index: $fileId" }
        repository.deleteById(fileId)
        meterRegistry.counter("index.file.deleted").increment()
    }

    /**
     * Soft delete file (mark as deleted)
     */
    fun softDeleteFile(fileId: String): FileIndex? {
        logger.info { "Soft deleting file from index: $fileId" }

        return repository.findById(fileId).map { file ->
            val updated = file.copy(isDeleted = true, version = file.version + 1)
            repository.save(updated)
        }.orElse(null)
    }

    /**
     * Delete all files for a backup job
     */
    fun deleteBackupJobFiles(backupJobId: String): Long {
        logger.info { "Deleting all files for backup job: $backupJobId" }

        val deletedCount = repository.deleteByBackupJobId(backupJobId)

        meterRegistry.counter("index.backup_job.deleted").increment()
        meterRegistry.counter("index.file.deleted").increment(deletedCount.toDouble())

        logger.info { "Deleted $deletedCount files for backup job: $backupJobId" }
        return deletedCount
    }

    /**
     * Get file by ID
     */
    fun getFileById(fileId: String): FileIndex? {
        return repository.findById(fileId).orElse(null)
    }

    /**
     * Find duplicate files by checksum
     */
    fun findDuplicates(checksum: String): List<FileIndex> {
        logger.debug { "Finding duplicate files with checksum: $checksum" }
        return repository.findByChecksum(checksum)
    }

    /**
     * Get total file count for tenant
     */
    fun getTenantFileCount(tenantId: String): Long {
        return repository.countByTenantId(tenantId)
    }

    /**
     * Reindex file (for updates or corrections)
     */
    fun reindexFile(fileId: String, newContent: String? = null, newTags: List<String>? = null): FileIndex? {
        logger.info { "Reindexing file: $fileId" }

        return repository.findById(fileId).map { existing ->
            val updated = existing.copy(
                content = newContent ?: existing.content,
                tags = newTags ?: existing.tags,
                indexedAt = Instant.now(),
                version = existing.version + 1
            )
            repository.save(updated)
        }.orElse(null)
    }

    /**
     * Bulk reindex operation (for maintenance)
     */
    fun bulkReindex(tenantId: String? = null): Long {
        logger.info { "Starting bulk reindex for tenant: ${tenantId ?: "all"}" }

        // This would typically fetch data from primary storage and reindex
        // For now, just update the indexedAt timestamp

        var count = 0L
        val batchSize = 1000

        // Simplified reindexing logic
        logger.info { "Bulk reindex completed: $count files" }

        return count
    }
}
