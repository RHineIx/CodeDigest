package com.codedigest.ix.data

import android.net.Uri

sealed class ProcessingState {
    object Idle : ProcessingState()
    object Scanning : ProcessingState()

    data class Processing(
        val currentFile: String,
        val progress: Float,
        val totalFiles: Int,
        val processed: Int
    ) : ProcessingState()

    data class Success(
        val uri: Uri,
        val message: String
    ) : ProcessingState()

    data class Error(val message: String) : ProcessingState()
}
