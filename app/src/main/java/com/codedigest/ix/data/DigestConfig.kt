package com.codedigest.ix.data

import android.net.Uri

data class DigestConfig(
    val sourceUri: Uri? = null,
    val customGitIgnoreUri: Uri? = null,
    val includePatterns: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList(),
    val useGitIgnore: Boolean = true,
    val fastMode: Boolean = false,
    val skipTree: Boolean = false,
    val compactMode: Boolean = false,
    val removeComments: Boolean = false,
    val showTokenCount: Boolean = true,
    val maxSizeMb: Int = 0 
)
