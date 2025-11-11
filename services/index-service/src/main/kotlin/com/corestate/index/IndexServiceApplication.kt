package com.corestate.index

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * CoreState Index Service
 *
 * Enterprise-grade indexing and search service for backup files and metadata.
 * Provides fast full-text search, content-based search, and metadata queries
 * using Elasticsearch and PostgreSQL.
 *
 * Features:
 * - Real-time file indexing
 * - Full-text search across file contents and metadata
 * - Advanced query capabilities (filters, aggregations, facets)
 * - gRPC and REST APIs
 * - Asynchronous batch indexing
 * - Search suggestions and autocomplete
 */
@SpringBootApplication
@EnableElasticsearchRepositories
@EnableJpaRepositories
@EnableAsync
@EnableScheduling
class IndexServiceApplication

fun main(args: Array<String>) {
    runApplication<IndexServiceApplication>(*args)
}
