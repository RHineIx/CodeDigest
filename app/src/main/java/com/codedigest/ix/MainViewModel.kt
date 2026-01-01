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
        // استرجاع البيانات المحفوظة عند فتح التطبيق
        viewModelScope.launch {
            prefs.preferencesFlow.collect { settings ->
                _config.value = _config.value.copy(
                    excludePatterns = settings.excludePatterns.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    removeComments = settings.removeComments,
                    showTokenCount = settings.showTokenCount
                )
                if (settings.savedSourceUri.isNotEmpty()) {
                     try { _config.value = _config.value.copy(sourceUri = Uri.parse(settings.savedSourceUri)) } catch(_:Exception){}
                }
                if (settings.savedIgnoreUri.isNotEmpty()) {
                    try { _config.value = _config.value.copy(customGitIgnoreUri = Uri.parse(settings.savedIgnoreUri)) } catch(_:Exception){}
                }
            }
        }
    }

    fun updateUri(uri: Uri) {
        _config.value = _config.value.copy(sourceUri = uri)
        viewModelScope.launch { prefs.saveSourceUri(uri.toString()) }
    }
    
    fun updateCustomIgnoreUri(uri: Uri) {
        _config.value = _config.value.copy(customGitIgnoreUri = uri)
        viewModelScope.launch { prefs.saveIgnoreUri(uri.toString()) }
    }

    fun toggleFastMode(v: Boolean) { _config.value = _config.value.copy(fastMode = v) }
    fun toggleSkipTree(v: Boolean) { _config.value = _config.value.copy(skipTree = v) }
    fun toggleCompactMode(v: Boolean) { _config.value = _config.value.copy(compactMode = v) }
    fun toggleUseGitIgnore(v: Boolean) { _config.value = _config.value.copy(useGitIgnore = v) }
    
    fun toggleRemoveComments(v: Boolean) { 
        _config.value = _config.value.copy(removeComments = v)
        saveCurrentSettings()
    }
    
    fun toggleShowTokens(v: Boolean) {
        _config.value = _config.value.copy(showTokenCount = v)
        saveCurrentSettings()
    }

    fun updateExcludePatterns(text: String) {
        val list = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        _config.value = _config.value.copy(excludePatterns = list)
        saveCurrentSettings()
    }

    private fun saveCurrentSettings() {
        viewModelScope.launch {
            prefs.saveSettings(
                exclude = _config.value.excludePatterns.joinToString(","),
                removeComments = _config.value.removeComments,
                showTokens = _config.value.showTokenCount
            )
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
