package com.corestate.backup.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import mu.KotlinLogging
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Client for Compression Engine service
 */
@Component
class CompressionEngineClient(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${services.compression-engine.url:http://compression-engine:8080}")
    private val serviceUrl: String
) {
    private val webClient = webClientBuilder.baseUrl(serviceUrl).build()

    data class CompressionRequest(
        val data: ByteArray,
        val algorithm: String
    )

    data class CompressionResponse(
        val compressedData: ByteArray,
        val originalSize: Int,
        val compressedSize: Int,
        val compressionRatio: Double
    )

    fun compressData(data: ByteArray, algorithm: String = "zstd"): Mono<ByteArray> {
        logger.debug { "Compressing data: ${data.size} bytes using $algorithm" }

        return webClient.post()
            .uri("/api/v1/compress")
            .bodyValue(mapOf(
                "data" to data,
                "algorithm" to algorithm
            ))
            .retrieve()
            .bodyToMono<CompressionResponse>()
            .map { response ->
                logger.info { "Compressed ${response.originalSize} bytes to ${response.compressedSize} bytes (ratio: ${response.compressionRatio})" }
                response.compressedData
            }
            .timeout(Duration.ofSeconds(30))
            .onErrorResume { error ->
                logger.error(error) { "Compression failed, returning original data" }
                Mono.just(data) // Fallback to uncompressed
            }
    }

    fun decompressData(data: ByteArray, algorithm: String = "zstd"): Mono<ByteArray> {
        logger.debug { "Decompressing data: ${data.size} bytes using $algorithm" }

        return webClient.post()
            .uri("/api/v1/decompress")
            .bodyValue(mapOf(
                "data" to data,
                "algorithm" to algorithm
            ))
            .retrieve()
            .bodyToMono<ByteArray>()
            .timeout(Duration.ofSeconds(30))
            .onErrorResume { error ->
                logger.error(error) { "Decompression failed" }
                Mono.error(error)
            }
    }
}

/**
 * Client for Encryption Service
 */
@Component
class EncryptionServiceClient(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${services.encryption.url:http://encryption-service:3002}")
    private val serviceUrl: String
) {
    private val webClient = webClientBuilder.baseUrl(serviceUrl).build()

    data class EncryptionRequest(
        val data: ByteArray,
        val keyId: String,
        val algorithm: String = "aes-256-gcm"
    )

    data class EncryptionResponse(
        val encryptedData: ByteArray,
        val iv: ByteArray,
        val authTag: ByteArray
    )

    fun encryptData(data: ByteArray, keyId: String, algorithm: String = "aes-256-gcm"): Mono<ByteArray> {
        logger.debug { "Encrypting data: ${data.size} bytes with key $keyId using $algorithm" }

        return webClient.post()
            .uri("/api/encrypt")
            .bodyValue(mapOf(
                "data" to data,
                "keyId" to keyId,
                "algorithm" to algorithm
            ))
            .retrieve()
            .bodyToMono<EncryptionResponse>()
            .map { response ->
                logger.info { "Successfully encrypted ${data.size} bytes" }
                response.encryptedData
            }
            .timeout(Duration.ofSeconds(30))
            .onErrorResume { error ->
                logger.error(error) { "Encryption failed" }
                Mono.error(error)
            }
    }

    fun decryptData(data: ByteArray, keyId: String, algorithm: String = "aes-256-gcm"): Mono<ByteArray> {
        logger.debug { "Decrypting data: ${data.size} bytes with key $keyId" }

        return webClient.post()
            .uri("/api/decrypt")
            .bodyValue(mapOf(
                "data" to data,
                "keyId" to keyId,
                "algorithm" to algorithm
            ))
            .retrieve()
            .bodyToMono<ByteArray>()
            .timeout(Duration.ofSeconds(30))
            .onErrorResume { error ->
                logger.error(error) { "Decryption failed" }
                Mono.error(error)
            }
    }

    fun generateKey(deviceId: String): Mono<String> {
        logger.info { "Generating encryption key for device: $deviceId" }

        return webClient.post()
            .uri("/api/keys/generate")
            .bodyValue(mapOf("deviceId" to deviceId))
            .retrieve()
            .bodyToMono<Map<String, String>>()
            .map { it["keyId"] ?: throw RuntimeException("Key ID not returned") }
            .timeout(Duration.ofSeconds(10))
    }
}

/**
 * Client for Deduplication Service
 */
@Component
class DeduplicationServiceClient(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${services.deduplication.url:http://deduplication-service:8002}")
    private val serviceUrl: String
) {
    private val webClient = webClientBuilder.baseUrl(serviceUrl).build()

    data class DeduplicationResponse(
        val chunkId: String,
        val isDuplicate: Boolean,
        val existingChunkId: String?
    )

    fun deduplicateChunk(chunk: ByteArray, checksum: String): Mono<String> {
        logger.debug { "Deduplicating chunk: ${chunk.size} bytes, checksum: $checksum" }

        return webClient.post()
            .uri("/api/chunks/deduplicate")
            .bodyValue(mapOf(
                "chunk" to chunk,
                "checksum" to checksum
            ))
            .retrieve()
            .bodyToMono<DeduplicationResponse>()
            .map { response ->
                if (response.isDuplicate) {
                    logger.info { "Chunk is duplicate, using existing: ${response.existingChunkId}" }
                    response.existingChunkId!!
                } else {
                    logger.info { "Chunk is unique, created new: ${response.chunkId}" }
                    response.chunkId
                }
            }
            .timeout(Duration.ofSeconds(30))
            .onErrorResume { error ->
                logger.error(error) { "Deduplication failed, generating fallback chunk ID" }
                Mono.just("chunk-$checksum")
            }
    }

    fun getChunk(chunkId: String): Mono<ByteArray> {
        logger.debug { "Retrieving chunk: $chunkId" }

        return webClient.get()
            .uri("/api/chunks/{chunkId}", chunkId)
            .retrieve()
            .bodyToMono<ByteArray>()
            .timeout(Duration.ofSeconds(30))
            .onErrorResume { error ->
                logger.error(error) { "Chunk retrieval failed: $chunkId" }
                Mono.error(error)
            }
    }

    fun deleteChunk(chunkId: String): Mono<Boolean> {
        logger.debug { "Deleting chunk: $chunkId" }

        return webClient.delete()
            .uri("/api/chunks/{chunkId}", chunkId)
            .retrieve()
            .bodyToMono<Map<String, Boolean>>()
            .map { it["success"] ?: false }
            .timeout(Duration.ofSeconds(30))
    }
}

/**
 * Client for Storage HAL service
 */
@Component
class StorageHalClient(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${services.storage-hal.url:http://storage-hal:50051}")
    private val serviceUrl: String
) {
    private val webClient = webClientBuilder.baseUrl(serviceUrl).build()

    data class StorageRequest(
        val chunkId: String,
        val data: ByteArray,
        val redundancy: Int = 3
    )

    fun storeChunk(chunkId: String, data: ByteArray, redundancy: Int = 3): Mono<Boolean> {
        logger.debug { "Storing chunk: $chunkId, size: ${data.size} bytes with redundancy $redundancy" }

        return webClient.post()
            .uri("/api/v1/store")
            .bodyValue(StorageRequest(chunkId, data, redundancy))
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .map { response ->
                val success = response["success"] as? Boolean ?: false
                if (success) {
                    logger.info { "Successfully stored chunk: $chunkId" }
                } else {
                    logger.warn { "Failed to store chunk: $chunkId" }
                }
                success
            }
            .timeout(Duration.ofMinutes(2))
            .onErrorResume { error ->
                logger.error(error) { "Storage failed for chunk: $chunkId" }
                Mono.just(false)
            }
    }

    fun retrieveChunk(chunkId: String): Mono<ByteArray> {
        logger.debug { "Retrieving chunk from storage: $chunkId" }

        return webClient.get()
            .uri("/api/v1/retrieve/{chunkId}", chunkId)
            .retrieve()
            .bodyToMono<ByteArray>()
            .timeout(Duration.ofMinutes(2))
            .onErrorResume { error ->
                logger.error(error) { "Chunk retrieval from storage failed: $chunkId" }
                Mono.error(error)
            }
    }

    fun deleteChunk(chunkId: String): Mono<Boolean> {
        logger.debug { "Deleting chunk from storage: $chunkId" }

        return webClient.delete()
            .uri("/api/v1/delete/{chunkId}", chunkId)
            .retrieve()
            .bodyToMono<Map<String, Boolean>>()
            .map { it["success"] ?: false }
            .timeout(Duration.ofSeconds(30))
    }

    fun verifyIntegrity(chunkId: String, expectedChecksum: String): Mono<Boolean> {
        logger.debug { "Verifying integrity of chunk: $chunkId" }

        return webClient.post()
            .uri("/api/v1/verify")
            .bodyValue(mapOf(
                "chunkId" to chunkId,
                "expectedChecksum" to expectedChecksum
            ))
            .retrieve()
            .bodyToMono<Map<String, Boolean>>()
            .map { it["valid"] ?: false }
            .timeout(Duration.ofSeconds(30))
    }
}

/**
 * Client for ML Optimizer service
 */
@Component
class MLOptimizerClient(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${services.ml-optimizer.url:http://ml-optimizer:8001}")
    private val serviceUrl: String
) {
    private val webClient = webClientBuilder.baseUrl(serviceUrl).build()

    data class BackupPredictionRequest(
        val path: String,
        val size: Long,
        val fileCount: Int,
        val backupType: String
    )

    data class BackupPredictionResponse(
        val estimatedDuration: Long,
        val estimatedSuccessRate: Double,
        val recommendedSchedule: String?
    )

    fun predictBackupDuration(path: String, size: Long, fileCount: Int, backupType: String): Mono<Long> {
        logger.debug { "Predicting backup duration for: $path, size: $size, files: $fileCount" }

        return webClient.post()
            .uri("/api/predict/backup")
            .bodyValue(BackupPredictionRequest(path, size, fileCount, backupType))
            .retrieve()
            .bodyToMono<BackupPredictionResponse>()
            .map { response ->
                logger.info { "Predicted duration: ${response.estimatedDuration}ms, success rate: ${response.estimatedSuccessRate}" }
                response.estimatedDuration
            }
            .timeout(Duration.ofSeconds(10))
            .onErrorResume { error ->
                logger.error(error) { "Prediction failed, using fallback estimation" }
                // Fallback: rough estimate based on size (10 MB/s)
                Mono.just((size / 10_000_000 * 1000).coerceAtLeast(1000))
            }
    }

    fun optimizeBackupSchedule(jobRequests: List<Map<String, Any>>): Mono<Map<String, Any>> {
        logger.info { "Optimizing schedule for ${jobRequests.size} backup jobs" }

        return webClient.post()
            .uri("/api/optimize/schedule")
            .bodyValue(mapOf("jobs" to jobRequests))
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .timeout(Duration.ofSeconds(30))
            .onErrorResume { error ->
                logger.error(error) { "Schedule optimization failed" }
                Mono.just(mapOf("optimized" to false, "error" to error.message))
            }
    }

    fun detectAnomalies(metrics: Map<String, Any>): Mono<Map<String, Any>> {
        logger.debug { "Detecting anomalies in backup metrics" }

        return webClient.post()
            .uri("/api/anomaly/detect")
            .bodyValue(metrics)
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .timeout(Duration.ofSeconds(10))
    }
}

/**
 * Client for Sync Coordinator service
 */
@Component
class SyncCoordinatorClient(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${services.sync-coordinator.url:http://sync-coordinator:3003}")
    private val serviceUrl: String
) {
    private val webClient = webClientBuilder.baseUrl(serviceUrl).build()

    fun notifyBackupCompleted(jobId: String, metadata: Map<String, Any>): Mono<Unit> {
        logger.info { "Notifying sync coordinator of completed backup: $jobId" }

        return webClient.post()
            .uri("/api/sync/notify")
            .bodyValue(mapOf(
                "jobId" to jobId,
                "status" to "completed",
                "metadata" to metadata
            ))
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .then(Mono.fromCallable { Unit })
            .timeout(Duration.ofSeconds(10))
            .onErrorResume { error ->
                logger.error(error) { "Failed to notify sync coordinator" }
                Mono.just(Unit) // Don't fail backup if sync notification fails
            }
    }

    fun syncBackupMetadata(jobId: String, metadata: Map<String, Any>): Mono<Unit> {
        logger.debug { "Syncing backup metadata for job: $jobId" }

        return webClient.post()
            .uri("/api/sync/metadata")
            .bodyValue(mapOf(
                "jobId" to jobId,
                "metadata" to metadata
            ))
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .then(Mono.fromCallable { Unit })
            .timeout(Duration.ofSeconds(10))
            .onErrorResume { error ->
                logger.error(error) { "Failed to sync metadata" }
                Mono.just(Unit)
            }
    }

    fun registerDevice(deviceId: String, deviceInfo: Map<String, Any>): Mono<String> {
        logger.info { "Registering device: $deviceId" }

        return webClient.post()
            .uri("/api/devices/register")
            .bodyValue(mapOf(
                "deviceId" to deviceId,
                "deviceInfo" to deviceInfo
            ))
            .retrieve()
            .bodyToMono<Map<String, String>>()
            .map { it["token"] ?: throw RuntimeException("Registration token not returned") }
            .timeout(Duration.ofSeconds(10))
    }

    fun syncState(deviceId: String, state: Map<String, Any>): Mono<Map<String, Any>> {
        logger.debug { "Syncing state for device: $deviceId" }

        return webClient.post()
            .uri("/api/state/sync")
            .bodyValue(mapOf(
                "deviceId" to deviceId,
                "state" to state
            ))
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .timeout(Duration.ofSeconds(10))
    }
}

/**
 * Client for Index Service
 */
@Component
class IndexServiceClient(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${services.index.url:http://index-service:8085}")
    private val serviceUrl: String
) {
    private val webClient = webClientBuilder.baseUrl(serviceUrl).build()

    fun indexFile(fileMetadata: Map<String, Any>): Mono<Unit> {
        logger.debug { "Indexing file: ${fileMetadata["fileName"]}" }

        return webClient.post()
            .uri("/api/v1/index/file")
            .bodyValue(fileMetadata)
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .then(Mono.fromCallable { Unit })
            .timeout(Duration.ofSeconds(10))
            .onErrorResume { error ->
                logger.error(error) { "File indexing failed" }
                Mono.just(Unit) // Don't fail backup if indexing fails
            }
    }

    fun batchIndexFiles(filesMetadata: List<Map<String, Any>>): Mono<Int> {
        logger.info { "Batch indexing ${filesMetadata.size} files" }

        return webClient.post()
            .uri("/api/v1/index/files/batch")
            .bodyValue(filesMetadata)
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .map { (it["indexed_count"] as? Number)?.toInt() ?: 0 }
            .timeout(Duration.ofSeconds(60))
    }
}
