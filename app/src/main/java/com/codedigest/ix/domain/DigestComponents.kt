package com.codedigest.ix.domain

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern

// --- Data Structures ---

data class FileItem(
    val path: String,
    val name: String,
    val uri: Uri?,
    val file: File?,
    val sizeInKb: Long
)

// --- Components ---

class TokenProcessor {
    private val registry: EncodingRegistry by lazy { Encodings.newDefaultEncodingRegistry() }
    private val tokenizer: Encoding by lazy { registry.getEncoding(EncodingType.CL100K_BASE) }

    fun countTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return try {
            tokenizer.countTokens(text)
        } catch (e: Exception) {
            0
        }
    }
}

class ContentFilter(private val context: Context) {
    
    // 1. Extensions that are ALWAYS binary or useless text (Keep in tree, skip content)
    private val skippableExtensions = setOf(
        // Images & Media
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "ico", "tiff",
        "mp4", "mkv", "avi", "mov", "mp3", "wav", "flac", "ogg",
        // Archives & Binaries
        "zip", "tar", "gz", "rar", "7z", "jar", "apk", "aab", "dex", "class", "so", "o", "a",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "exe", "dll", "bin", "dat", "db", "sqlite", "pdb",
        // Keystores & Certs
        "jks", "keystore", "pem", "crt", "der", "p12",
        // Fonts
        "ttf", "otf", "woff", "woff2", "eot"
    )

    // 2. Exact filenames to skip (Config & Metadata)
    private val skippableFilenames = setOf(
        "gradle-wrapper.jar",
        "gradlew",
        "gradlew.bat",
        "local.properties",
        ".DS_Store",
        "Thumbs.db"
    )

    // 3. XML Tags that indicate this is a GRAPHIC asset, not code
    // If an XML starts with these, we skip its content.
    private val assetXmlTags = setOf(
        "<vector", 
        "<animated-vector", 
        "<bitmap", 
        "<aapt:attr", 
        "<shape",       // debatable, but often purely visual
        "<selector",    // debatable, but often purely visual
        "<ripple",
        "<layer-list",
        "<nine-patch"
    )

    // Regex for removing comments
    private val commentsRegex = Regex(
        "(//.*)|(/\\*[\\s\\S]*?\\*/)|(#.*)|(--.*)|(\"\"\".*?\"\"\")",
        RegexOption.MULTILINE
    )

    // --- SMART CHECK FUNCTION ---
    fun isSmartSkippable(file: File): Boolean {
        // A. Check Name & Extension
        if (checkNameAndExtension(file.name)) return true
        
        // B. Check Path Heuristics (Mipmap is always icons)
        val path = file.absolutePath.lowercase()
        if (path.contains("/res/mipmap")) return true
        if (path.contains("/res/font")) return true
        if (path.contains("/res/raw")) return true // Raw assets usually binary

        // C. Content Peek (The "Gitingest" Logic) for XML in Drawable
        // Only peek if it's an XML in a drawable folder to save performance
        if (path.contains("/res/drawable") && file.extension.equals("xml", ignoreCase = true)) {
            return isXmlAssetByContent { file.inputStream() }
        }

        return false
    }

    // Overload for DocumentFile (SAF)
    fun isSmartSkippable(docFile: DocumentFile, uri: Uri): Boolean {
        val name = docFile.name ?: return false
        
        // A. Check Name
        if (checkNameAndExtension(name)) return true
        
        // B. Check Path via URI segment detection (heuristic)
        val uriString = uri.toString().lowercase()
        if (uriString.contains("mipmap")) return true
        if (uriString.contains("font")) return true
        
        // C. Content Peek
        if (uriString.contains("drawable") && name.endsWith(".xml", true)) {
            return isXmlAssetByContent { context.contentResolver.openInputStream(uri) }
        }

        return false
    }

    private fun checkNameAndExtension(name: String): Boolean {
        val lowerName = name.lowercase()
        if (skippableFilenames.contains(lowerName)) return true
        val ext = lowerName.substringAfterLast('.', "")
        return skippableExtensions.contains(ext)
    }

    private inline fun isXmlAssetByContent(streamProvider: () -> InputStream?): Boolean {
        return try {
            streamProvider()?.use { stream ->
                // Read just the first 512 bytes. That's enough to find the root tag.
                val buffer = ByteArray(512)
                val read = stream.read(buffer)
                if (read <= 0) return false
                
                val header = String(buffer, 0, read, Charsets.UTF_8).trim()
                
                // Check if any "Asset Tag" is present at the start
                assetXmlTags.any { tag -> header.contains(tag, ignoreCase = true) }
            } ?: false
        } catch (e: Exception) {
            false // If we can't read it, assume it's code to be safe, or skip? Better safe.
        }
    }

    fun shouldIgnore(path: String, patterns: List<String>): Boolean {
        if (patterns.isEmpty()) return false
        return patterns.any { pattern ->
            val p = pattern.replace(".", "\\.").replace("*", ".*")
            Pattern.compile(p).matcher(path).find()
        }
    }

    fun removeComments(text: String): String {
        return commentsRegex.replace(text, "")
    }

    fun readIgnoreRules(uri: Uri): List<String> {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && !it.startsWith("#") }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
