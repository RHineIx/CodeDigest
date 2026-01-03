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
        val COMPACT_MODE = booleanPreferencesKey("compact_mode")
        val SKIP_TREE = booleanPreferencesKey("skip_tree")
        val USE_GIT_IGNORE = booleanPreferencesKey("use_git_ignore")
        val FAST_MODE = booleanPreferencesKey("fast_mode")
        val MAX_FILE_SIZE_KB = intPreferencesKey("max_file_size_kb")
        val OUTPUT_FORMAT = stringPreferencesKey("output_format")
        val TOKEN_LIMIT = intPreferencesKey("token_limit")
    }

    // FIX: Changed return type to Triple to match MainViewModel expectation
    val preferencesFlow = context.dataStore.data.map { preferences ->
        val config = DigestConfig(
            sourceUri = null,
            customGitIgnoreUri = null,
            excludePatterns = (preferences[EXCLUDE_PATTERNS] ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() },
            removeComments = preferences[REMOVE_COMMENTS] ?: false,
            showTokenCount = preferences[SHOW_TOKEN_COUNT] ?: true,
            compactMode = preferences[COMPACT_MODE] ?: false,
            skipTree = preferences[SKIP_TREE] ?: false,
            useGitIgnore = preferences[USE_GIT_IGNORE] ?: true,
            fastMode = preferences[FAST_MODE] ?: false,
            maxFileSizeKB = preferences[MAX_FILE_SIZE_KB] ?: 0,
            outputFormat = try {
                OutputFormat.valueOf(preferences[OUTPUT_FORMAT] ?: OutputFormat.PLAIN_TEXT.name)
            } catch (e: Exception) { OutputFormat.PLAIN_TEXT },
            tokenLimit = preferences[TOKEN_LIMIT] ?: 0
        )
        
        // Return as Triple (Config, SourceString?, IgnoreString?)
        Triple(config, preferences[SOURCE_URI], preferences[CUSTOM_IGNORE_URI])
    }

    suspend fun saveSourceUri(uri: String) {
        context.dataStore.edit { it[SOURCE_URI] = uri }
    }

    suspend fun saveIgnoreUri(uri: String) {
        context.dataStore.edit { it[CUSTOM_IGNORE_URI] = uri }
    }

    suspend fun saveConfig(config: DigestConfig) {
        context.dataStore.edit { prefs ->
            prefs[EXCLUDE_PATTERNS] = config.excludePatterns.joinToString(",")
            prefs[REMOVE_COMMENTS] = config.removeComments
            prefs[SHOW_TOKEN_COUNT] = config.showTokenCount
            prefs[COMPACT_MODE] = config.compactMode
            prefs[SKIP_TREE] = config.skipTree
            prefs[USE_GIT_IGNORE] = config.useGitIgnore
            prefs[FAST_MODE] = config.fastMode
            prefs[MAX_FILE_SIZE_KB] = config.maxFileSizeKB
            prefs[OUTPUT_FORMAT] = config.outputFormat.name
            prefs[TOKEN_LIMIT] = config.tokenLimit
        }
    }
}
