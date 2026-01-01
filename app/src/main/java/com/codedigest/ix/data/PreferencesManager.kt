package com.codedigest.ix.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "digest_settings")

class PreferencesManager(private val context: Context) {
    companion object {
        val SOURCE_URI = stringPreferencesKey("source_uri")
        val CUSTOM_IGNORE_URI = stringPreferencesKey("ignore_uri")
        val EXCLUDE_PATTERNS = stringPreferencesKey("exclude_patterns")
        val REMOVE_COMMENTS = booleanPreferencesKey("remove_comments")
        val SHOW_TOKEN_COUNT = booleanPreferencesKey("show_token_count")
    }

    val preferencesFlow = context.dataStore.data.map { preferences ->
        DigestSettings(
            savedSourceUri = preferences[SOURCE_URI] ?: "",
            savedIgnoreUri = preferences[CUSTOM_IGNORE_URI] ?: "",
            excludePatterns = preferences[EXCLUDE_PATTERNS] ?: "",
            removeComments = preferences[REMOVE_COMMENTS] ?: false,
            showTokenCount = preferences[SHOW_TOKEN_COUNT] ?: true
        )
    }

    suspend fun saveSourceUri(uri: String) {
        context.dataStore.edit { it[SOURCE_URI] = uri }
    }
    
    suspend fun saveIgnoreUri(uri: String) {
        context.dataStore.edit { it[CUSTOM_IGNORE_URI] = uri }
    }

    suspend fun saveSettings(exclude: String, removeComments: Boolean, showTokens: Boolean) {
        context.dataStore.edit { 
            it[EXCLUDE_PATTERNS] = exclude
            it[REMOVE_COMMENTS] = removeComments
            it[SHOW_TOKEN_COUNT] = showTokens
        }
    }
}

data class DigestSettings(
    val savedSourceUri: String,
    val savedIgnoreUri: String,
    val excludePatterns: String,
    val removeComments: Boolean,
    val showTokenCount: Boolean
)
