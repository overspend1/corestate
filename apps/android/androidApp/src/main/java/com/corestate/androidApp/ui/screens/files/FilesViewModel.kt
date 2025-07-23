package com.corestate.androidApp.ui.screens.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val backupRepository: BackupRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()
    
    init {
        loadFiles()
    }
    
    private fun loadFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val files = fileRepository.getFiles(uiState.value.currentPath)
                _uiState.update { currentState ->
                    currentState.copy(
                        files = files,
                        isLoading = false
                    )
                }
                applyFilters()
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = e.message
                    ) 
                }
            }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }
    
    fun toggleFileTypeFilter(type: FileType) {
        _uiState.update { currentState ->
            val updatedTypes = if (currentState.selectedFileTypes.contains(type)) {
                currentState.selectedFileTypes - type
            } else {
                currentState.selectedFileTypes + type
            }
            currentState.copy(selectedFileTypes = updatedTypes)
        }
        applyFilters()
    }
    
    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }
    
    fun refreshFiles() {
        loadFiles()
    }
    
    fun navigateToFile(file: FileModel) {
        if (file.type == FileType.FOLDER) {
            val newPath = if (uiState.value.currentPath.isEmpty()) {
                file.name
            } else {
                "${uiState.value.currentPath}/${file.name}"
            }
            
            _uiState.update { currentState ->
                currentState.copy(
                    currentPath = newPath,
                    pathHistory = currentState.pathHistory + currentState.currentPath
                )
            }
            loadFiles()
        }
    }
    
    fun navigateUp() {
        val currentState = uiState.value
        if (currentState.canNavigateUp) {
            val parentPath = if (currentState.pathHistory.isNotEmpty()) {
                currentState.pathHistory.last()
            } else {
                ""
            }
            
            _uiState.update { 
                it.copy(
                    currentPath = parentPath,
                    pathHistory = if (it.pathHistory.isNotEmpty()) {
                        it.pathHistory.dropLast(1)
                    } else {
                        emptyList()
                    }
                )
            }
            loadFiles()
        }
    }
    
    fun restoreFile(file: FileModel) {
        viewModelScope.launch {
            try {
                backupRepository.restoreFile(file.path)
                // Optionally show success message
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun downloadFile(file: FileModel) {
        viewModelScope.launch {
            try {
                fileRepository.downloadFile(file.path)
                // Optionally show success message
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun deleteFile(file: FileModel) {
        viewModelScope.launch {
            try {
                fileRepository.deleteFile(file.path)
                loadFiles() // Refresh the list
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    private fun applyFilters() {
        val currentState = uiState.value
        var filteredFiles = currentState.files
        
        // Apply search filter
        if (currentState.searchQuery.isNotEmpty()) {
            filteredFiles = filteredFiles.filter { file ->
                file.name.contains(currentState.searchQuery, ignoreCase = true)
            }
        }
        
        // Apply file type filter
        if (currentState.selectedFileTypes.isNotEmpty()) {
            filteredFiles = filteredFiles.filter { file ->
                currentState.selectedFileTypes.contains(file.type)
            }
        }
        
        _uiState.update { it.copy(filteredFiles = filteredFiles) }
    }
    
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class FilesUiState(
    val isLoading: Boolean = true,
    val files: List<FileModel> = emptyList(),
    val filteredFiles: List<FileModel> = emptyList(),
    val currentPath: String = "",
    val pathHistory: List<String> = emptyList(),
    val searchQuery: String = "",
    val selectedFileTypes: Set<FileType> = emptySet(),
    val isGridView: Boolean = false,
    val error: String? = null
) {
    val canNavigateUp: Boolean
        get() = currentPath.isNotEmpty()
}

data class FileModel(
    val path: String,
    val name: String,
    val size: String,
    val lastModified: String,
    val type: FileType,
    val isBackedUp: Boolean = false
)

enum class FileType(val displayName: String) {
    FOLDER("Folders"),
    IMAGE("Images"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    DOCUMENT("Documents"),
    OTHER("Other")
}

// Enhanced repository interfaces
interface FileRepository {
    suspend fun getFiles(path: String): List<FileModel>
    suspend fun downloadFile(path: String)
    suspend fun deleteFile(path: String)
}

interface BackupRepository {
    suspend fun restoreFile(path: String)
}