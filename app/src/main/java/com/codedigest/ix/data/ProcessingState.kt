package com.codedigest.ix.data

import android.net.Uri

sealed class ProcessingState {
    data object Idle : ProcessingState()
    data object Scanning : ProcessingState()
    
    data class Processing(
        val currentFile: String, 
        val progress: Float, 
        val totalFiles: Int, 
        val processed: Int
    ) : ProcessingState()
    
    data class Success(val outputFileUri: Uri, val summary: String) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}
