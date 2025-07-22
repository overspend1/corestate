package com.corestate.backup.distributed

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.SortedMap
import java.util.TreeMap
import java.security.MessageDigest

// --- Placeholder Classes and Enums ---

data class DataChunk(val data: ByteArray) {
    fun calculateHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).fold("") { str, it -> str + "%02x".format(it) }
    }
}

data class StorageNode(val id: String) {
    suspend fun uploadChunk(chunk: DataChunk): Boolean {
        // Placeholder for actual network upload logic
        println("Uploading chunk ${chunk.calculateHash()} to node $id")
        // Simulate network delay and potential failure
        kotlinx.coroutines.delay(100)
        if (Math.random() < 0.1) throw RuntimeException("Failed to upload to $id")
        return true
    }
}

enum class ChunkStatus { UPLOADED, FAILED }
enum class DistributionStatus { SUCCESS, PARTIAL, FAILED }

data class ChunkLocation(val nodeId: String, val chunkId: String, val status: ChunkStatus)
data class DistributionResult(
    val chunkId: String,
    val locations: List<ChunkLocation>,
    val status: DistributionStatus
)

// --- Consistent Hashing Implementation ---

class ConsistentHashRing<T : StorageNode>(
    private val numberOfReplicas: Int = 3,
    private val hashFunction: (String) -> Int = { it.hashCode() }
) {
    private val circle: SortedMap<Int, T> = TreeMap()

    fun add(node: T) {
        for (i in 0 until numberOfReplicas) {
            val hash = hashFunction("${node.id}:$i")
            circle[hash] = node
        }
    }

    fun getNodes(key: String, count: Int): List<T> {
        if (circle.isEmpty() || count == 0) return emptyList()
        val hash = hashFunction(key)
        val result = mutableSetOf<T>()
        
        val tailMap = circle.tailMap(hash)
        val iterator = (tailMap.values + circle.values).iterator()

        while (result.size < count && result.size < circle.values.distinct().size) {
            if (iterator.hasNext()) {
                result.add(iterator.next())
            } else {
                break // Should not happen with circular logic, but for safety
            }
        }
        return result.toList()
    }
    
    fun getNextNode(failedNode: T): T? {
        // Simple logic: find the next node in the circle
        val hashes = circle.filterValues { it.id == failedNode.id }.keys
        if (hashes.isEmpty()) return null
        val firstHash = hashes.first()
        val tailMap = circle.tailMap(firstHash + 1)
        return (tailMap.values + circle.values).firstOrNull { it.id != failedNode.id }
    }
}

// --- Main ChunkDistributor Class ---

class ChunkDistributor(
    storageNodes: List<StorageNode>,
    private val replicationFactor: Int = 3
) {
    private val consistentHash = ConsistentHashRing<StorageNode>()
    // private val logger = LoggerFactory.getLogger(ChunkDistributor::class.java) // Placeholder

    init {
        storageNodes.forEach { consistentHash.add(it) }
    }

    suspend fun distributeChunk(chunk: DataChunk): DistributionResult {
        val chunkId = chunk.calculateHash()
        val primaryNodes = consistentHash.getNodes(chunkId, replicationFactor)

        if (primaryNodes.isEmpty()) {
            // logger.error("No storage nodes available to distribute chunk $chunkId")
            return DistributionResult(chunkId, emptyList(), DistributionStatus.FAILED)
        }

        return coroutineScope {
            val uploadJobs = primaryNodes.map { node ->
                async {
                    try {
                        node.uploadChunk(chunk)
                        ChunkLocation(node.id, chunkId, ChunkStatus.UPLOADED)
                    } catch (e: Exception) {
                        handleFailedUpload(node, chunk, e)
                    }
                }
            }

            val locations = uploadJobs.awaitAll().filterNotNull()
            // updateChunkIndex(chunkId, locations) // Placeholder for index update

            val successCount = locations.count { it.status == ChunkStatus.UPLOADED }
            val finalStatus = when {
                successCount >= replicationFactor -> DistributionStatus.SUCCESS
                successCount > 0 -> DistributionStatus.PARTIAL
                else -> DistributionStatus.FAILED
            }
            
            DistributionResult(
                chunkId = chunkId,
                locations = locations,
                status = finalStatus
            )
        }
    }

    private suspend fun handleFailedUpload(
        failedNode: StorageNode,
        chunk: DataChunk,
        error: Exception
    ): ChunkLocation? {
        // logger.error("Failed to upload chunk to node ${failedNode.id}", error)
        println("Failed to upload chunk to node ${failedNode.id}: ${error.message}")

        val alternativeNode = consistentHash.getNextNode(failedNode)
        return if (alternativeNode != null) {
            try {
                alternativeNode.uploadChunk(chunk)
                ChunkLocation(alternativeNode.id, chunk.calculateHash(), ChunkStatus.UPLOADED)
            } catch (e: Exception) {
                // logger.error("Failed to upload chunk to alternative node ${alternativeNode.id}", e)
                println("Failed to upload chunk to alternative node ${alternativeNode.id}: ${e.message}")
                ChunkLocation(failedNode.id, chunk.calculateHash(), ChunkStatus.FAILED)
            }
        } else {
            // logger.warn("No alternative node found for failed node ${failedNode.id}")
            println("No alternative node found for failed node ${failedNode.id}")
            null
        }
    }
}