package com.corestate.index.repository

import com.corestate.index.model.FileIndex
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

@Repository
interface FileIndexRepository : ElasticsearchRepository<FileIndex, String> {

    fun findByTenantId(tenantId: String, pageable: Pageable): Page<FileIndex>

    fun findByTenantIdAndFileNameContaining(tenantId: String, fileName: String, pageable: Pageable): Page<FileIndex>

    fun findByTenantIdAndFileExtension(tenantId: String, extension: String, pageable: Pageable): Page<FileIndex>

    fun findByTenantIdAndTagsContaining(tenantId: String, tag: String, pageable: Pageable): Page<FileIndex>

    fun findByBackupJobId(backupJobId: String, pageable: Pageable): Page<FileIndex>

    fun findByChecksum(checksum: String): List<FileIndex>

    fun deleteByBackupJobId(backupJobId: String): Long

    fun countByTenantId(tenantId: String): Long
}
