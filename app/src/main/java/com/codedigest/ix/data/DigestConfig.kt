package com.codedigest.ix.data

import android.net.Uri

enum class OutputFormat {
    PLAIN_TEXT,
    MARKDOWN,
    XML
}

data class DigestConfig(
    val sourceUri: Uri? = null,
    val customGitIgnoreUri: Uri? = null,
    val excludePatterns: List<String> = emptyList(),
    val removeComments: Boolean = false,
    val showTokenCount: Boolean = true,
    val compactMode: Boolean = false,
    val skipTree: Boolean = false,
    val useGitIgnore: Boolean = true,
    val fastMode: Boolean = false,
    val maxFileSizeKB: Int = 0,
    val outputFormat: OutputFormat = OutputFormat.PLAIN_TEXT,
    val tokenLimit: Int = 0
)
