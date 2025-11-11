package com.corestate.index.model

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.*
import java.time.Instant

/**
 * Elasticsearch document for file indexing
 */
@Document(indexName = "file-index")
@Setting(settingPath = "elasticsearch-settings.json")
data class FileIndex(
    @Id
    val id: String,

    @Field(type = FieldType.Keyword)
    val fileId: String,

    @Field(type = FieldType.Text, analyzer = "standard")
    val fileName: String,

    @Field(type = FieldType.Text, analyzer = "path_analyzer")
    val filePath: String,

    @Field(type = FieldType.Keyword)
    val fileExtension: String,

    @Field(type = FieldType.Long)
    val fileSize: Long,

    @Field(type = FieldType.Text, analyzer = "standard")
    val content: String? = null,

    @Field(type = FieldType.Keyword)
    val mimeType: String,

    @Field(type = FieldType.Keyword)
    val tenantId: String,

    @Field(type = FieldType.Keyword)
    val backupJobId: String,

    @Field(type = FieldType.Date, format = [DateFormat.date_time])
    val createdAt: Instant,

    @Field(type = FieldType.Date, format = [DateFormat.date_time])
    val modifiedAt: Instant,

    @Field(type = FieldType.Date, format = [DateFormat.date_time])
    val indexedAt: Instant = Instant.now(),

    @Field(type = FieldType.Keyword)
    val checksum: String,

    @Field(type = FieldType.Keyword)
    val tags: List<String> = emptyList(),

    @Field(type = FieldType.Object)
    val metadata: Map<String, Any> = emptyMap(),

    @Field(type = FieldType.Boolean)
    val isDeleted: Boolean = false,

    @Field(type = FieldType.Integer)
    val version: Int = 1
)

/**
 * Search result with score and highlights
 */
data class FileSearchResult(
    val file: FileIndex,
    val score: Float,
    val highlights: Map<String, List<String>> = emptyMap()
)

/**
 * Search query parameters
 */
data class SearchQuery(
    val query: String,
    val tenantId: String? = null,
    val fileExtensions: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val createdAfter: Instant? = null,
    val createdBefore: Instant? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: String = "score",
    val sortOrder: String = "desc"
)

/**
 * Aggregation result for faceted search
 */
data class SearchAggregation(
    val field: String,
    val buckets: List<AggregationBucket>
)

data class AggregationBucket(
    val key: String,
    val docCount: Long
)

/**
 * Search response with results and aggregations
 */
data class SearchResponse(
    val results: List<FileSearchResult>,
    val totalHits: Long,
    val page: Int,
    val size: Int,
    val aggregations: List<SearchAggregation> = emptyList()
)
