package com.corestate.index.service

import com.corestate.index.model.*
import com.corestate.index.repository.FileIndexRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import mu.KotlinLogging
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.SearchHit
import org.springframework.data.elasticsearch.core.SearchHits
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Service for searching indexed files
 */
@Service
class SearchService(
    private val repository: FileIndexRepository,
    private val elasticsearchOps: ElasticsearchOperations,
    private val meterRegistry: MeterRegistry
) {

    private val searchTimer = Timer.builder("search.query.duration")
        .description("Time taken to execute search query")
        .register(meterRegistry)

    /**
     * Full-text search across file names and content
     */
    fun search(searchQuery: SearchQuery): SearchResponse {
        return searchTimer.recordCallable {
            logger.info { "Executing search query: ${searchQuery.query}" }

            val queryBuilder = QueryBuilders.boolQuery().apply {
                // Main search query
                must(
                    QueryBuilders.multiMatchQuery(searchQuery.query)
                        .field("fileName", 2.0f) // Boost file name matches
                        .field("content")
                        .field("filePath")
                        .field("tags", 1.5f) // Boost tag matches
                )

                // Filters
                searchQuery.tenantId?.let { filter(QueryBuilders.termQuery("tenantId", it)) }

                if (searchQuery.fileExtensions.isNotEmpty()) {
                    filter(QueryBuilders.termsQuery("fileExtension", searchQuery.fileExtensions))
                }

                if (searchQuery.tags.isNotEmpty()) {
                    filter(QueryBuilders.termsQuery("tags", searchQuery.tags))
                }

                searchQuery.minSize?.let { filter(QueryBuilders.rangeQuery("fileSize").gte(it)) }
                searchQuery.maxSize?.let { filter(QueryBuilders.rangeQuery("fileSize").lte(it)) }
                searchQuery.createdAfter?.let { filter(QueryBuilders.rangeQuery("createdAt").gte(it)) }
                searchQuery.createdBefore?.let { filter(QueryBuilders.rangeQuery("createdAt").lte(it)) }

                // Exclude deleted files
                mustNot(QueryBuilders.termQuery("isDeleted", true))
            }

            // Highlighting
            val highlightBuilder = HighlightBuilder()
                .field("fileName")
                .field("content")
                .field("filePath")
                .preTags("<mark>")
                .postTags("</mark>")

            // Aggregations for faceted search
            val aggregations = listOf(
                AggregationBuilders.terms("by_extension").field("fileExtension").size(10),
                AggregationBuilders.terms("by_type").field("mimeType").size(10),
                AggregationBuilders.terms("by_tags").field("tags").size(20)
            )

            // Build native search query
            val nativeSearchQuery = NativeSearchQueryBuilder()
                .withQuery(queryBuilder)
                .withHighlightBuilder(highlightBuilder)
                .withPageable(PageRequest.of(
                    searchQuery.page,
                    searchQuery.size,
                    Sort.by(
                        if (searchQuery.sortOrder == "desc") Sort.Direction.DESC else Sort.Direction.ASC,
                        searchQuery.sortBy
                    )
                ))
                .apply {
                    aggregations.forEach { withAggregation(it) }
                }
                .build()

            // Execute search
            val searchHits: SearchHits<FileIndex> = elasticsearchOps.search(
                nativeSearchQuery,
                FileIndex::class.java
            )

            // Process results
            val results = searchHits.searchHits.map { hit ->
                FileSearchResult(
                    file = hit.content,
                    score = hit.score,
                    highlights = hit.highlightFields.mapValues { it.value.map { fragment -> fragment.toString() } }
                )
            }

            // Process aggregations
            val searchAggregations = searchHits.aggregations?.let { aggs ->
                listOf(
                    SearchAggregation(
                        field = "fileExtension",
                        buckets = (aggs.get("by_extension") as? Terms)?.buckets?.map {
                            AggregationBucket(it.keyAsString, it.docCount)
                        } ?: emptyList()
                    ),
                    SearchAggregation(
                        field = "mimeType",
                        buckets = (aggs.get("by_type") as? Terms)?.buckets?.map {
                            AggregationBucket(it.keyAsString, it.docCount)
                        } ?: emptyList()
                    ),
                    SearchAggregation(
                        field = "tags",
                        buckets = (aggs.get("by_tags") as? Terms)?.buckets?.map {
                            AggregationBucket(it.keyAsString, it.docCount)
                        } ?: emptyList()
                    )
                )
            } ?: emptyList()

            meterRegistry.counter("search.query.executed",
                "tenant_id", searchQuery.tenantId ?: "all"
            ).increment()

            logger.info { "Search completed: ${searchHits.totalHits} total hits, returned ${results.size} results" }

            SearchResponse(
                results = results,
                totalHits = searchHits.totalHits,
                page = searchQuery.page,
                size = searchQuery.size,
                aggregations = searchAggregations
            )
        }!!
    }

    /**
     * Search by file name only
     */
    fun searchByFileName(fileName: String, tenantId: String?, page: Int = 0, size: Int = 20): SearchResponse {
        logger.info { "Searching by file name: $fileName" }

        val queryBuilder = QueryBuilders.boolQuery().apply {
            must(QueryBuilders.wildcardQuery("fileName", "*$fileName*"))
            tenantId?.let { filter(QueryBuilders.termQuery("tenantId", it)) }
            mustNot(QueryBuilders.termQuery("isDeleted", true))
        }

        val nativeSearchQuery = NativeSearchQueryBuilder()
            .withQuery(queryBuilder)
            .withPageable(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "modifiedAt")))
            .build()

        val searchHits = elasticsearchOps.search(nativeSearchQuery, FileIndex::class.java)

        val results = searchHits.searchHits.map { hit ->
            FileSearchResult(file = hit.content, score = hit.score)
        }

        return SearchResponse(
            results = results,
            totalHits = searchHits.totalHits,
            page = page,
            size = size
        )
    }

    /**
     * Search by file path
     */
    fun searchByPath(path: String, tenantId: String?, page: Int = 0, size: Int = 20): SearchResponse {
        logger.info { "Searching by path: $path" }

        val queryBuilder = QueryBuilders.boolQuery().apply {
            must(QueryBuilders.matchPhraseQuery("filePath", path))
            tenantId?.let { filter(QueryBuilders.termQuery("tenantId", it)) }
            mustNot(QueryBuilders.termQuery("isDeleted", true))
        }

        val nativeSearchQuery = NativeSearchQueryBuilder()
            .withQuery(queryBuilder)
            .withPageable(PageRequest.of(page, size))
            .build()

        val searchHits = elasticsearchOps.search(nativeSearchQuery, FileIndex::class.java)

        val results = searchHits.searchHits.map { hit ->
            FileSearchResult(file = hit.content, score = hit.score)
        }

        return SearchResponse(
            results = results,
            totalHits = searchHits.totalHits,
            page = page,
            size = size
        )
    }

    /**
     * Search by tags
     */
    fun searchByTags(tags: List<String>, tenantId: String?, matchAll: Boolean = false, page: Int = 0, size: Int = 20): SearchResponse {
        logger.info { "Searching by tags: $tags, matchAll=$matchAll" }

        val queryBuilder = QueryBuilders.boolQuery().apply {
            if (matchAll) {
                tags.forEach { tag -> must(QueryBuilders.termQuery("tags", tag)) }
            } else {
                must(QueryBuilders.termsQuery("tags", tags))
            }
            tenantId?.let { filter(QueryBuilders.termQuery("tenantId", it)) }
            mustNot(QueryBuilders.termQuery("isDeleted", true))
        }

        val nativeSearchQuery = NativeSearchQueryBuilder()
            .withQuery(queryBuilder)
            .withPageable(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "modifiedAt")))
            .build()

        val searchHits = elasticsearchOps.search(nativeSearchQuery, FileIndex::class.java)

        val results = searchHits.searchHits.map { hit ->
            FileSearchResult(file = hit.content, score = hit.score)
        }

        return SearchResponse(
            results = results,
            totalHits = searchHits.totalHits,
            page = page,
            size = size
        )
    }

    /**
     * Get search suggestions (autocomplete)
     */
    fun getSuggestions(prefix: String, tenantId: String?, limit: Int = 10): List<String> {
        logger.debug { "Getting suggestions for prefix: $prefix" }

        val queryBuilder = QueryBuilders.boolQuery().apply {
            must(QueryBuilders.prefixQuery("fileName", prefix))
            tenantId?.let { filter(QueryBuilders.termQuery("tenantId", it)) }
            mustNot(QueryBuilders.termQuery("isDeleted", true))
        }

        val nativeSearchQuery = NativeSearchQueryBuilder()
            .withQuery(queryBuilder)
            .withPageable(PageRequest.of(0, limit))
            .withFields("fileName")
            .build()

        val searchHits = elasticsearchOps.search(nativeSearchQuery, FileIndex::class.java)

        return searchHits.searchHits.map { it.content.fileName }.distinct()
    }

    /**
     * Similar files search (by metadata similarity)
     */
    fun findSimilarFiles(fileId: String, limit: Int = 10): List<FileSearchResult> {
        logger.info { "Finding similar files to: $fileId" }

        val originalFile = repository.findById(fileId).orElse(null) ?: return emptyList()

        val queryBuilder = QueryBuilders.boolQuery().apply {
            should(QueryBuilders.termQuery("fileExtension", originalFile.fileExtension))
            should(QueryBuilders.termsQuery("tags", originalFile.tags))
            should(QueryBuilders.rangeQuery("fileSize")
                .gte(originalFile.fileSize * 0.8)
                .lte(originalFile.fileSize * 1.2))
            must(QueryBuilders.termQuery("tenantId", originalFile.tenantId))
            mustNot(QueryBuilders.termQuery("fileId", fileId))
            mustNot(QueryBuilders.termQuery("isDeleted", true))
            minimumShouldMatch(1)
        }

        val nativeSearchQuery = NativeSearchQueryBuilder()
            .withQuery(queryBuilder)
            .withPageable(PageRequest.of(0, limit))
            .build()

        val searchHits = elasticsearchOps.search(nativeSearchQuery, FileIndex::class.java)

        return searchHits.searchHits.map { hit ->
            FileSearchResult(file = hit.content, score = hit.score)
        }
    }
}
