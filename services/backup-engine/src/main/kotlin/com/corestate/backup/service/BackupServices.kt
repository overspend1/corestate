package com.corestate.backup.service

import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Path

@Service
class FileSystemService {
    
    fun listFiles(path: Path): Mono<List<File>> {
        return try {
            val files = path.toFile().listFiles()?.toList() ?: emptyList()
            Mono.just(files)
        } catch (e: Exception) {
            Mono.error(e)
        }
    }
    
    fun getFileMetadata(path: Path): Mono<FileMetadata> {
        return try {
            val file = path.toFile()
            val metadata = FileMetadata(
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                permissions = getFilePermissions(file)
            )
            Mono.just(metadata)
        } catch (e: Exception) {
            Mono.error(e)
        }
    }
    
    private fun getFilePermissions(file: File): String {
        val permissions = StringBuilder()
        permissions.append(if (file.canRead()) "r" else "-")
        permissions.append(if (file.canWrite()) "w" else "-")
        permissions.append(if (file.canExecute()) "x" else "-")
        return permissions.toString()
    }
}

data class FileMetadata(
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val permissions: String
)

@Service
class ChunkingService {
    
    companion object {
        private const val DEFAULT_CHUNK_SIZE = 1024 * 1024 // 1MB
    }
    
    fun chunkFile(filePath: Path, chunkSize: Int = DEFAULT_CHUNK_SIZE): Mono<List<ByteArray>> {
        return try {
            val file = filePath.toFile()
            if (!file.exists()) {
                return Mono.error(IllegalArgumentException("File does not exist: $filePath"))
            }
            
            val chunks = mutableListOf<ByteArray>()
            file.inputStream().use { input ->
                val buffer = ByteArray(chunkSize)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (bytesRead < chunkSize) {
                        chunks.add(buffer.copyOf(bytesRead))
                    } else {
                        chunks.add(buffer.copyOf())
                    }
                }
            }
            
            Mono.just(chunks)
        } catch (e: Exception) {
            Mono.error(e)
        }
    }
    
    fun reassembleChunks(chunks: List<ByteArray>, outputPath: Path): Mono<Unit> {
        return try {
            outputPath.toFile().outputStream().use { output ->
                chunks.forEach { chunk ->
                    output.write(chunk)
                }
            }
            Mono.just(Unit)
        } catch (e: Exception) {
            Mono.error(e)
        }
    }
}