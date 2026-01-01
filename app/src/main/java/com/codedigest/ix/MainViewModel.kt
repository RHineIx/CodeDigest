package com.codedigest.ix

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codedigest.ix.data.DigestConfig
import com.codedigest.ix.data.PreferencesManager
import com.codedigest.ix.data.ProcessingState
import com.codedigest.ix.domain.DigestEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = DigestEngine(application)
    private val prefs = PreferencesManager(application)

    private val _config = MutableStateFlow(DigestConfig())
    val config = _config.asStateFlow()

    private val _state = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.preferencesFlow.collect { (savedConfig, uris) ->
                val (savedSource, savedIgnore) = uris
                
                _config.value = savedConfig.copy(
                    sourceUri = if (savedSource?.isNotEmpty() == true) try { Uri.parse(savedSource) } catch(e: Exception) { null } else _config.value.sourceUri,
                    customGitIgnoreUri = if (savedIgnore?.isNotEmpty() == true) try { Uri.parse(savedIgnore) } catch(e: Exception) { null } else _config.value.customGitIgnoreUri
                )
            }
        }
    }

    fun updateUri(uri: Uri) {
        _config.value = _config.value.copy(sourceUri = uri)
        viewModelScope.launch { prefs.saveSourceUri(uri.toString()) }
    }

    // NEW: Clear Source URI
    fun clearSourceUri() {
        _config.value = _config.value.copy(sourceUri = null)
        viewModelScope.launch { prefs.saveSourceUri("") }
    }

    fun updateCustomIgnoreUri(uri: Uri) {
        _config.value = _config.value.copy(customGitIgnoreUri = uri)
        viewModelScope.launch { prefs.saveIgnoreUri(uri.toString()) }
    }

    // NEW: Clear Ignore URI
    fun clearCustomIgnoreUri() {
        _config.value = _config.value.copy(customGitIgnoreUri = null)
        viewModelScope.launch { prefs.saveIgnoreUri("") }
    }

    fun toggleFastMode(v: Boolean) { 
        _config.value = _config.value.copy(fastMode = v)
        saveCurrentConfig()
    }
    
    fun toggleSkipTree(v: Boolean) { 
        _config.value = _config.value.copy(skipTree = v)
        saveCurrentConfig()
    }
    
    fun toggleCompactMode(v: Boolean) { 
        _config.value = _config.value.copy(compactMode = v)
        saveCurrentConfig()
    }
    
    fun toggleUseGitIgnore(v: Boolean) { 
        _config.value = _config.value.copy(useGitIgnore = v)
        saveCurrentConfig()
    }
    
    fun toggleRemoveComments(v: Boolean) { 
        _config.value = _config.value.copy(removeComments = v)
        saveCurrentConfig()
    }

    fun toggleShowTokens(v: Boolean) {
        _config.value = _config.value.copy(showTokenCount = v)
        saveCurrentConfig()
    }

    fun updateMaxFileSize(kb: Int) {
        _config.value = _config.value.copy(maxFileSizeKB = kb)
        saveCurrentConfig()
    }

    fun updateExcludePatterns(text: String) {
        val list = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        _config.value = _config.value.copy(excludePatterns = list)
        saveCurrentConfig()
    }

    fun applyPreset(presetName: String) {
        val currentPatterns = _config.value.excludePatterns.toMutableSet()
        
        val newPatterns = when(presetName) {
            "Android" -> listOf(
                "build", ".gradle", "local.properties", ".idea", 
                "*.png", "*.jpg", "*.jpeg", "*.webp", "*.svg", "*.ico",
                "*.so", "*.aar", "*.jar", "*.apk", "captures"
            )
            "Web" -> listOf(
                "node_modules", ".next", ".nuxt", "dist", "build", "coverage",
                "package-lock.json", "yarn.lock", ".cache", "*.log"
            )
            "Python/AI" -> listOf(
                "__pycache__", "*.pyc", "venv", ".venv", ".env", 
                ".git", ".ipynb_checkpoints", "poetry.lock"
            )
            "Media" -> listOf(
                "*.mp4", "*.mp3", "*.wav", "*.mov", "*.avi", "*.mkv",
                "*.jpg", "*.png", "*.gif", "*.pdf", "*.zip", "*.gz"
            )
            else -> emptyList()
        }

        currentPatterns.addAll(newPatterns)
        
        _config.value = _config.value.copy(excludePatterns = currentPatterns.toList())
        saveCurrentConfig()
    }

    private fun saveCurrentConfig() {
        viewModelScope.launch {
            prefs.saveConfig(_config.value)
        }
    }

    fun startProcessing() {
        if (_config.value.sourceUri == null) return

        viewModelScope.launch {
            engine.process(_config.value).collect { _state.value = it }
        }
    }

    fun resetState() { _state.value = ProcessingState.Idle }
}
