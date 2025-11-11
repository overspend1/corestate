package com.corestate.index.controller

import com.corestate.index.model.SearchQuery
import com.corestate.index.model.SearchResponse
import com.corestate.index.service.SearchService
import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "File search operations")
class SearchController(
    private val searchService: SearchService
) {

    @PostMapping
    @Timed("search.fulltext.api")
    @Operation(summary = "Full-text search across files")
    fun search(@RequestBody searchQuery: SearchQuery): ResponseEntity<SearchResponse> {
        logger.info { "API search request: ${searchQuery.query}" }

        return try {
            val response = searchService.search(searchQuery)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error(e) { "Search failed for query: ${searchQuery.query}" }
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping
    @Timed("search.simple.api")
    @Operation(summary = "Simple search with query string")
    fun simpleSearch(
        @RequestParam q: String,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<SearchResponse> {
        logger.info { "API simple search: $q" }

        val searchQuery = SearchQuery(
            query = q,
            tenantId = tenantId,
            page = page,
            size = size
        )

        return try {
            val response = searchService.search(searchQuery)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error(e) { "Simple search failed for query: $q" }
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/filename")
    @Timed("search.filename.api")
    @Operation(summary = "Search by file name")
    fun searchByFileName(
        @RequestParam fileName: String,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<SearchResponse> {
        logger.info { "API search by filename: $fileName" }

        return try {
            val response = searchService.searchByFileName(fileName, tenantId, page, size)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error(e) { "Filename search failed: $fileName" }
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/path")
    @Timed("search.path.api")
    @Operation(summary = "Search by file path")
    fun searchByPath(
        @RequestParam path: String,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<SearchResponse> {
        logger.info { "API search by path: $path" }

        return try {
            val response = searchService.searchByPath(path, tenantId, page, size)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error(e) { "Path search failed: $path" }
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/tags")
    @Timed("search.tags.api")
    @Operation(summary = "Search by tags")
    fun searchByTags(
        @RequestParam tags: List<String>,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam(defaultValue = "false") matchAll: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<SearchResponse> {
        logger.info { "API search by tags: $tags" }

        return try {
            val response = searchService.searchByTags(tags, tenantId, matchAll, page, size)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error(e) { "Tag search failed: $tags" }
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/suggestions")
    @Timed("search.suggestions.api")
    @Operation(summary = "Get search suggestions (autocomplete)")
    fun getSuggestions(
        @RequestParam prefix: String,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<List<String>> {
        logger.debug { "API search suggestions for: $prefix" }

        return try {
            val suggestions = searchService.getSuggestions(prefix, tenantId, limit)
            ResponseEntity.ok(suggestions)
        } catch (e: Exception) {
            logger.error(e) { "Suggestions failed for: $prefix" }
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/similar/{fileId}")
    @Timed("search.similar.api")
    @Operation(summary = "Find similar files")
    fun findSimilar(
        @PathVariable fileId: String,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<Map<String, Any>> {
        logger.info { "API find similar files to: $fileId" }

        return try {
            val similar = searchService.findSimilarFiles(fileId, limit)
            ResponseEntity.ok(mapOf(
                "fileId" -> fileId,
                "similar" -> similar
            ))
        } catch (e: Exception) {
            logger.error(e) { "Similar search failed for: $fileId" }
            ResponseEntity.internalServerError().build()
        }
    }
}
