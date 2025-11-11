package com.corestate.index.controller

import com.corestate.index.model.FileIndex
import com.corestate.index.service.IndexingService
import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/index")
@Tag(name = "Index Management", description = "File indexing operations")
class IndexController(
    private val indexingService: IndexingService
) {

    @PostMapping("/file")
    @Timed("index.file.api")
    @Operation(summary = "Index a single file")
    fun indexFile(@RequestBody fileIndex: FileIndex): ResponseEntity<FileIndex> {
        logger.info { "API request to index file: ${fileIndex.fileId}" }

        return try {
            val indexed = indexingService.indexFile(fileIndex)
            ResponseEntity.ok(indexed)
        } catch (e: Exception) {
            logger.error(e) { "Failed to index file: ${fileIndex.fileId}" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/files/batch")
    @Timed("index.batch.api")
    @Operation(summary = "Index multiple files in batch")
    fun indexBatch(@RequestBody files: List<FileIndex>): ResponseEntity<Map<String, Any>> {
        logger.info { "API request to batch index ${files.size} files" }

        return try {
            val indexed = indexingService.batchIndexFiles(files)
            ResponseEntity.ok(mapOf(
                "status" -> "success",
                "indexed_count" -> indexed.size
            ))
        } catch (e: Exception) {
            logger.error(e) { "Failed to batch index files" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PutMapping("/file/{fileId}")
    @Timed("index.update.api")
    @Operation(summary = "Update file index")
    fun updateFile(@PathVariable fileId: String, @RequestBody fileIndex: FileIndex): ResponseEntity<FileIndex> {
        logger.info { "API request to update file: $fileId" }

        return try {
            val updated = indexingService.updateFile(fileIndex.copy(id = fileId, fileId = fileId))
            ResponseEntity.ok(updated)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update file: $fileId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @DeleteMapping("/file/{fileId}")
    @Timed("index.delete.api")
    @Operation(summary = "Delete file from index")
    fun deleteFile(@PathVariable fileId: String, @RequestParam(defaultValue = "false") soft: Boolean): ResponseEntity<Map<String, String>> {
        logger.info { "API request to delete file: $fileId (soft=$soft)" }

        return try {
            if (soft) {
                indexingService.softDeleteFile(fileId)
            } else {
                indexingService.deleteFile(fileId)
            }
            ResponseEntity.ok(mapOf("status" -> "deleted", "fileId" -> fileId))
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete file: $fileId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @GetMapping("/file/{fileId}")
    @Operation(summary = "Get file by ID")
    fun getFile(@PathVariable fileId: String): ResponseEntity<FileIndex> {
        logger.debug { "API request to get file: $fileId" }

        val file = indexingService.getFileById(fileId)
        return if (file != null) {
            ResponseEntity.ok(file)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/duplicates/{checksum}")
    @Operation(summary = "Find duplicate files by checksum")
    fun findDuplicates(@PathVariable checksum: String): ResponseEntity<List<FileIndex>> {
        logger.info { "API request to find duplicates for checksum: $checksum" }

        val duplicates = indexingService.findDuplicates(checksum)
        return ResponseEntity.ok(duplicates)
    }

    @DeleteMapping("/backup/{backupJobId}")
    @Operation(summary = "Delete all files for a backup job")
    fun deleteBackupJob(@PathVariable backupJobId: String): ResponseEntity<Map<String, Any>> {
        logger.info { "API request to delete backup job: $backupJobId" }

        return try {
            val deletedCount = indexingService.deleteBackupJobFiles(backupJobId)
            ResponseEntity.ok(mapOf(
                "status" -> "deleted",
                "backupJobId" -> backupJobId,
                "deletedCount" -> deletedCount
            ))
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete backup job: $backupJobId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/reindex/{fileId}")
    @Operation(summary = "Reindex a specific file")
    fun reindexFile(
        @PathVariable fileId: String,
        @RequestParam(required = false) content: String?,
        @RequestParam(required = false) tags: List<String>?
    ): ResponseEntity<FileIndex> {
        logger.info { "API request to reindex file: $fileId" }

        val reindexed = indexingService.reindexFile(fileId, content, tags)
        return if (reindexed != null) {
            ResponseEntity.ok(reindexed)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/reindex/tenant/{tenantId}")
    @Operation(summary = "Bulk reindex for a tenant")
    fun bulkReindex(@PathVariable tenantId: String): ResponseEntity<Map<String, Any>> {
        logger.info { "API request to bulk reindex for tenant: $tenantId" }

        return try {
            val count = indexingService.bulkReindex(tenantId)
            ResponseEntity.ok(mapOf(
                "status" -> "completed",
                "tenantId" -> tenantId,
                "reindexed_count" -> count
            ))
        } catch (e: Exception) {
            logger.error(e) { "Failed to bulk reindex for tenant: $tenantId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @GetMapping("/stats/tenant/{tenantId}")
    @Operation(summary = "Get indexing statistics for a tenant")
    fun getTenantStats(@PathVariable tenantId: String): ResponseEntity<Map<String, Any>> {
        logger.debug { "API request for tenant stats: $tenantId" }

        val fileCount = indexingService.getTenantFileCount(tenantId)
        return ResponseEntity.ok(mapOf(
            "tenantId" -> tenantId,
            "totalFiles" -> fileCount
        ))
    }
}
