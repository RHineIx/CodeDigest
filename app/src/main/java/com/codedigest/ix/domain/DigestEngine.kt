package com.codedigest.ix.domain

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.codedigest.ix.data.DigestConfig
import com.codedigest.ix.data.ProcessingState
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class DigestEngine(private val context: Context) {

    private val binaryExtensions = setOf(
        "mp4", "avi", "mov", "mkv", "jpg", "jpeg", "png", "gif", "bmp", "svg", "ico",
        "pdf", "zip", "tar", "gz", "rar", "exe", "dll", "so", "dylib", "bin", "dat",
        "apk", "jar", "class", "dex", "lock", "db", "sqlite", "eot", "ttf", "woff", "woff2"
    )

    private val commentsRegex = Regex(
        "(//.*)|(/\\*[\\s\\S]*?\\*/)",
        RegexOption.MULTILINE
    )

    private val registry: EncodingRegistry by lazy { Encodings.newDefaultEncodingRegistry() }
    private val tokenizer: Encoding by lazy { registry.getEncoding(EncodingType.CL100K_BASE) }

    suspend fun process(config: DigestConfig): Flow<ProcessingState> = flow {
        emit(ProcessingState.Scanning)
        try {
            val sourceUri = config.sourceUri ?: throw Exception("No source URI provided")
            val rootDir = DocumentFile.fromTreeUri(context, sourceUri)
                ?: throw Exception("Cannot access source folder")

            val ignorePatterns = mutableListOf<String>()
            ignorePatterns.addAll(config.excludePatterns)
            
            if (config.useGitIgnore) {
                rootDir.findFile(".gitignore")?.let { file ->
                    ignorePatterns.addAll(readLinesFromUri(file.uri))
                }
            }
            config.customGitIgnoreUri?.let { uri ->
                ignorePatterns.addAll(readLinesFromUri(uri))
            }

            val allFiles = mutableListOf<FileItem>()
            val treeBuilder = StringBuilder()

            if (!config.skipTree) {
                treeBuilder.appendLine("Directory Structure for: ${rootDir.name}")
                treeBuilder.appendLine(rootDir.name ?: "root")
            }

            val localRoot = resolveLocalPath(sourceUri)
            if (localRoot != null && localRoot.exists() && localRoot.isDirectory) {
                traverseDirectoryFast(localRoot, "", "", config, ignorePatterns, allFiles, treeBuilder)
            } else {
                traverseDirectorySaf(rootDir, "", "", config, ignorePatterns, allFiles, treeBuilder)
            }

            val totalFiles = allFiles.size
            if (totalFiles == 0) {
                emit(ProcessingState.Error("No matching files found!"))
                return@flow
            }

            val outDir = File(Environment.getExternalStorageDirectory(), "CodeDigest")
            if (!outDir.exists()) outDir.mkdirs()
            val finalOutFile = File(outDir, "${rootDir.name}_Digest.txt")
            
            val tempBodyFile = File(context.cacheDir, "digest_body_${System.currentTimeMillis()}.bin")

            var processedCount = 0
            var totalTokens = 0L
            val writeMutex = Mutex()
            val chunkSize = 20

            val tempFos = FileOutputStream(tempBodyFile)
            if (!config.skipTree) {
                tempFos.write(treeBuilder.toString().toByteArray(StandardCharsets.UTF_8))
                tempFos.write("\n\n".toByteArray(StandardCharsets.UTF_8))
            }
            val tempWriter = tempFos.bufferedWriter()

            try {
                allFiles.chunked(chunkSize).forEach { batch ->
                    val results = batch.map { fileItem ->
                        processFileContent(fileItem, config)
                    }

                    writeMutex.withLock {
                        results.forEach { (content, tokens) ->
                            if (content.isNotEmpty()) {
                                tempWriter.write(content)
                                totalTokens += tokens
                            }
                        }
                        processedCount += batch.size
                        emit(
                            ProcessingState.Processing(
                                currentFile = "Batch ${processedCount / chunkSize}",
                                progress = processedCount.toFloat() / totalFiles,
                                totalFiles = totalFiles,
                                processed = processedCount
                            )
                        )
                    }
                }
            } finally {
                tempWriter.flush()
                tempWriter.close()
            }

            FileOutputStream(finalOutFile).use { finalFos ->
                val finalChannel = finalFos.channel
                
                val headerContent = StringBuilder().apply {
                    append("Project Digest: ${rootDir.name}\n")
                    append("Total Files: $totalFiles\n")
                    append("Total Files Processed: $processedCount\n")
                    if (config.showTokenCount) {
                        append("Total Tokens (CL100K): $totalTokens\n")
                    }
                    append("Generated by CodeDigest App\n")
                    append("------------------------------------------------\n\n")
                }.toString()
                
                finalFos.write(headerContent.toByteArray(StandardCharsets.UTF_8))

                FileInputStream(tempBodyFile).use { tempIn ->
                    val tempChannel = tempIn.channel
                    tempChannel.transferTo(0, tempChannel.size(), finalChannel)
                }
            }

            if (tempBodyFile.exists()) {
                tempBodyFile.delete()
            }

            emit(
                ProcessingState.Success(
                    Uri.fromFile(finalOutFile),
                    "Saved to: ${finalOutFile.absolutePath}\nTokens: $totalTokens"
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
            emit(ProcessingState.Error(e.message ?: "Unknown Error"))
        }
    }.flowOn(Dispatchers.IO)

    private data class FileItem(
        val name: String,
        val uri: Uri?, 
        val file: File? 
    )

    private fun resolveLocalPath(uri: Uri): File? {
        try {
            val path = uri.path ?: return null
            if (path.contains("primary:")) {
                val relativePath = path.substringAfter("primary:")
                return File(Environment.getExternalStorageDirectory(), relativePath)
            }
        } catch (e: Exception) {
            return null
        }
        return null
    }

    private fun readLinesFromUri(uri: Uri): List<String> {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && !it.startsWith("#") }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun traverseDirectoryFast(
        dir: File,
        prefix: String,
        relativePath: String,
        config: DigestConfig,
        ignorePatterns: List<String>,
        fileCollector: MutableList<FileItem>,
        treeBuilder: StringBuilder
    ) {
        val files = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: return
        
        val validItems = files.filter { file ->
            val path = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"
            !shouldIgnore(path, ignorePatterns) && file.name != ".git"
        }

        val count = validItems.size
        validItems.forEachIndexed { index, file ->
            val isLast = index == count - 1
            val name = file.name
            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"

            if (!config.skipTree) {
                treeBuilder.append(prefix)
                treeBuilder.append(if (isLast) "└── " else "├── ")
                treeBuilder.appendLine(name)
            }

            if (file.isDirectory) {
                val newPrefix = prefix + if (isLast) "    " else "│   "
                traverseDirectoryFast(file, newPrefix, currentPath, config, ignorePatterns, fileCollector, treeBuilder)
            } else {
                // FEATURE: Check File Size Limit
                val sizeInKb = file.length() / 1024
                if (config.maxFileSizeKB == 0 || sizeInKb <= config.maxFileSizeKB) {
                    if (!isBinaryFileFast(file, config)) {
                        fileCollector.add(FileItem(name, null, file))
                    }
                }
            }
        }
    }

    private fun traverseDirectorySaf(
        dir: DocumentFile,
        prefix: String,
        relativePath: String,
        config: DigestConfig,
        ignorePatterns: List<String>,
        fileCollector: MutableList<FileItem>,
        treeBuilder: StringBuilder
    ) {
        val files = dir.listFiles().sortedBy { it.name?.lowercase() }
        val validItems = files.filter { file ->
            val name = file.name ?: ""
            val path = if (relativePath.isEmpty()) name else "$relativePath/$name"
            !shouldIgnore(path, ignorePatterns) && name != ".git"
        }

        val count = validItems.size
        validItems.forEachIndexed { index, file ->
            val isLast = index == count - 1
            val name = file.name ?: "unknown"
            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"

            if (!config.skipTree) {
                treeBuilder.append(prefix)
                treeBuilder.append(if (isLast) "└── " else "├── ")
                treeBuilder.appendLine(name)
            }

            if (file.isDirectory) {
                val newPrefix = prefix + if (isLast) "    " else "│   "
                traverseDirectorySaf(file, newPrefix, currentPath, config, ignorePatterns, fileCollector, treeBuilder)
            } else {
                // FEATURE: Check File Size Limit
                val sizeInKb = file.length() / 1024
                if (config.maxFileSizeKB == 0 || sizeInKb <= config.maxFileSizeKB) {
                    if (!isBinaryFileSaf(file, config)) {
                        fileCollector.add(FileItem(name, file.uri, null))
                    }
                }
            }
        }
    }

    private fun processFileContent(item: FileItem, config: DigestConfig): Pair<String, Int> {
        return try {
            val sb = StringBuilder()
            var text = ""

            if (item.file != null) {
                text = item.file.readText()
            } else if (item.uri != null) {
                context.contentResolver.openInputStream(item.uri)?.use { stream ->
                    text = stream.bufferedReader().readText()
                } ?: return Pair("", 0)
            }

            if (config.removeComments) {
                text = commentsRegex.replace(text, "")
            }

            sb.appendLine("================================================")
            sb.appendLine("FILE: ${item.name}")
            sb.appendLine("================================================")

            if (config.compactMode) {
                text.lineSequence()
                    .filter { it.isNotBlank() }
                    .forEach { sb.appendLine(it) }
            } else {
                sb.appendLine(text)
            }
            sb.appendLine()

            val tokens = if (config.showTokenCount) countTokens(text) else 0
            Pair(sb.toString(), tokens)
        } catch (e: Exception) {
            Pair("", 0)
        }
    }

    private fun countTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return try {
            tokenizer.countTokens(text)
        } catch (e: Exception) {
            0
        }
    }

    private fun isBinaryFileFast(file: File, config: DigestConfig): Boolean {
        val ext = file.extension.lowercase()
        if (binaryExtensions.contains(ext)) return true
        if (config.fastMode) return false
        
        return try {
            file.inputStream().use { stream ->
                val buffer = ByteArray(512)
                val read = stream.read(buffer)
                if (read == -1) return false
                (0 until read).any { buffer[it].toInt() == 0 }
            }
        } catch (e: Exception) { false }
    }

    private fun isBinaryFileSaf(file: DocumentFile, config: DigestConfig): Boolean {
        val name = file.name ?: return false
        val ext = name.substringAfterLast('.', "").lowercase()
        if (binaryExtensions.contains(ext)) return true
        if (config.fastMode) return false
        
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                val buffer = ByteArray(512)
                val read = stream.read(buffer)
                if (read == -1) return false
                (0 until read).any { buffer[it].toInt() == 0 }
            } ?: false
        } catch (e: Exception) { false }
    }

    private fun shouldIgnore(path: String, patterns: List<String>): Boolean {
        return patterns.any { pattern ->
            val p = pattern.replace(".", "\\.").replace("*", ".*")
            Pattern.compile(p).matcher(path).find()
        }
    }
}
