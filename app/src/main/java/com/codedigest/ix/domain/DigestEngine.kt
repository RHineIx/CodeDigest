package com.codedigest.ix.domain

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.codedigest.ix.R
import com.codedigest.ix.data.DigestConfig
import com.codedigest.ix.data.ProcessingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class DigestEngine(private val context: Context) {

    private val filter = ContentFilter(context)
    private val tokenizer = TokenProcessor()

    suspend fun process(config: DigestConfig): Flow<ProcessingState> = flow {
        emit(ProcessingState.Scanning)

        try {
            val sourceUri = config.sourceUri ?: throw Exception(context.getString(R.string.err_no_source_uri))
            val rootDir = DocumentFile.fromTreeUri(context, sourceUri)
                ?: throw Exception(context.getString(R.string.err_cannot_access_folder))

            val ignorePatterns = prepareIgnorePatterns(config, rootDir)

            val treeBuilder = StringBuilder()
            if (!config.skipTree) {
                treeBuilder.appendLine("Directory Structure for: ${rootDir.name}")
                treeBuilder.appendLine(rootDir.name ?: "root")
            }

            val fileItems = mutableListOf<FileItem>()
            val localRoot = resolveLocalPath(sourceUri)

            if (localRoot != null && localRoot.exists() && localRoot.isDirectory) {
                scanDirectoryFast(localRoot, "", "", config, ignorePatterns, fileItems, treeBuilder)
            } else {
                scanDirectorySaf(rootDir, "", "", config, ignorePatterns, fileItems, treeBuilder)
            }

            val totalFiles = fileItems.size
            if (totalFiles == 0) {
                emit(ProcessingState.Error(context.getString(R.string.err_no_matching_files)))
                return@flow
            }

            val outDir = File(Environment.getExternalStorageDirectory(), "CodeDigest")
            if (!outDir.exists()) outDir.mkdirs()
            val baseFileName = "${rootDir.name ?: "Project"}_Digest"

            var processedCount = 0
            val treeContent = if (!config.skipTree) OutputFormatter.formatTree(treeBuilder.toString(), config.outputFormat) else ""
            val treeTokens = if (config.showTokenCount) tokenizer.countTokens(treeContent) else 0

            val partManager = PartManager(
                outDir = outDir,
                baseFileName = baseFileName,
                tokenLimit = config.tokenLimit,
                treeContent = treeContent,
                treeTokens = treeTokens,
                tokenizer = tokenizer,
                config = config,
                totalFiles = totalFiles
            )

            try {
                partManager.startNewPart()

                fileItems.forEach { item ->
                    val result = processSingleFile(item, config)
                    if (result != null) {
                        val (content, tokens) = result
                        partManager.writeContent(item.path, content, tokens)
                    }

                    processedCount++
                    if (processedCount % 10 == 0) {
                        emit(
                            ProcessingState.Processing(
                                currentFile = context.getString(R.string.processing_file_progress, item.name),
                                progress = processedCount.toFloat() / totalFiles,
                                totalFiles = totalFiles,
                                processed = processedCount
                            )
                        )
                    }
                }
            } finally {
                partManager.closeCurrentPart()
            }

            val generatedFiles = partManager.getGeneratedFiles()
            val totalTokens = partManager.getTotalProjectTokens()

            emit(
                ProcessingState.Success(
                    files = generatedFiles,
                    totalSourceFiles = totalFiles,
                    totalTokens = totalTokens,
                    message = context.getString(
                        R.string.success_saved_msg,
                        generatedFiles.size,
                        rootDir.name,
                        totalTokens
                    )
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
            emit(ProcessingState.Error(e.message ?: context.getString(R.string.err_unknown)))
        }
    }.flowOn(Dispatchers.IO)

    // --- Inner Class for managing splits ---
    private inner class PartManager(
        private val outDir: File,
        private val baseFileName: String,
        private val tokenLimit: Int,
        private val treeContent: String,
        private val treeTokens: Int,
        private val tokenizer: TokenProcessor,
        private val config: DigestConfig,
        private val totalFiles: Int
    ) {
        private var partIndex = 1
        private var currentWriter: BufferedWriter? = null
        private var currentPartTokens = 0
        private var totalProjectTokens = 0L
        private val generatedFiles = mutableListOf<File>()

        fun startNewPart() {
            closeCurrentPart()
            val fileName = if (tokenLimit > 0) "${baseFileName}_Part$partIndex.txt" else "${baseFileName}.txt"
            val file = File(outDir, fileName)
            generatedFiles.add(file)

            val fos = FileOutputStream(file)
            currentWriter = BufferedWriter(OutputStreamWriter(fos, StandardCharsets.UTF_8))
            
            val header = OutputFormatter.formatHeader(
                projectName = baseFileName,
                fileCount = totalFiles, 
                tokenCount = 0, 
                format = config.outputFormat
            ) + "\n" + treeContent

            currentWriter?.write(header)
            currentPartTokens = tokenizer.countTokens(header)
            partIndex++
        }

        fun writeContent(filePath: String, content: String, tokens: Int) {
            if (tokenLimit > 0 && (currentPartTokens + tokens) > tokenLimit) {
                if (tokens > (tokenLimit - treeTokens)) {
                    writeLargeFileSplit(filePath, content)
                } else {
                    startNewPart()
                    currentWriter?.write(content)
                    currentPartTokens += tokens
                }
            } else {
                currentWriter?.write(content)
                currentPartTokens += tokens
            }
            totalProjectTokens += tokens
        }

        private fun writeLargeFileSplit(filePath: String, fullContent: String) {
            val safetyMargin = 500
            val effectiveChunkSize = tokenLimit - currentPartTokens - safetyMargin
            
            if (effectiveChunkSize < 1000) {
                startNewPart()
            }

            val charLimit = (tokenLimit - currentPartTokens) * 3 
            
            if (fullContent.length <= charLimit) {
                 currentWriter?.write(fullContent)
                 currentPartTokens += tokenizer.countTokens(fullContent)
                 return
            }

            val splitPoint = charLimit.coerceAtMost(fullContent.length)
            
            val truncatedMsg = context.getString(R.string.file_truncated_msg)
            val part1 = fullContent.substring(0, splitPoint) + truncatedMsg
            currentWriter?.write(part1)
            
            startNewPart()
            
            val continuationMsg = context.getString(R.string.file_continuation_msg, filePath)
            val part2 = continuationMsg + fullContent.substring(splitPoint)
            
            currentWriter?.write(part2)
            currentPartTokens += tokenizer.countTokens(part2)
        }

        fun closeCurrentPart() {
            try {
                currentWriter?.flush()
                currentWriter?.close()
                currentWriter = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun getGeneratedFiles() = generatedFiles
        fun getTotalProjectTokens() = totalProjectTokens
    }

    private fun prepareIgnorePatterns(config: DigestConfig, rootDir: DocumentFile): List<String> {
        val patterns = mutableListOf<String>()
        patterns.addAll(config.excludePatterns)

        if (config.useGitIgnore) {
            rootDir.findFile(".gitignore")?.let { file ->
                patterns.addAll(filter.readIgnoreRules(file.uri))
            }
        }
        config.customGitIgnoreUri?.let { uri ->
            patterns.addAll(filter.readIgnoreRules(uri))
        }
        return patterns
    }

    private fun processSingleFile(item: FileItem, config: DigestConfig): Pair<String, Int>? {
        return try {
            var text = ""
            if (item.file != null) {
                text = item.file.readText()
            } else if (item.uri != null) {
                context.contentResolver.openInputStream(item.uri)?.use {
                    text = it.bufferedReader().readText()
                } ?: return null
            }

            if (config.removeComments) {
                text = filter.removeComments(text)
            }
            if (config.compactMode) {
                text = text.lineSequence().filter { it.isNotBlank() }.joinToString("\n")
            }

            val formatted = OutputFormatter.formatFile(item.path, text, config.outputFormat)
            val tokens = if (config.showTokenCount) tokenizer.countTokens(text) else 0
            Pair(formatted, tokens)
        } catch (e: Exception) {
            null
        }
    }

    private fun scanDirectoryFast(
        dir: File,
        prefix: String,
        relativePath: String,
        config: DigestConfig,
        ignorePatterns: List<String>,
        collector: MutableList<FileItem>,
        treeBuilder: StringBuilder
    ) {
        val files = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: return
        val validItems = files.filter { file ->
            val path = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"
            !filter.shouldIgnore(path, ignorePatterns) && file.name != ".git"
        }

        val count = validItems.size
        validItems.forEachIndexed { index, file ->
            val isLast = index == count - 1
            val currentPath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"

            if (!config.skipTree) {
                treeBuilder.append(prefix)
                treeBuilder.append(if (isLast) "└── " else "├── ")
                treeBuilder.appendLine(file.name)
            }

            if (file.isDirectory) {
                val newPrefix = prefix + if (isLast) "    " else "│   "
                scanDirectoryFast(file, newPrefix, currentPath, config, ignorePatterns, collector, treeBuilder)
            } else {
                val sizeInKb = file.length() / 1024
                if ((config.maxFileSizeKB == 0 || sizeInKb <= config.maxFileSizeKB)) {
                    // --- NEW: Use isSmartSkippable ---
                    if (!filter.isSmartSkippable(file)) {
                        collector.add(FileItem(currentPath, file.name, null, file, sizeInKb))
                    }
                }
            }
        }
    }

    private fun scanDirectorySaf(
        dir: DocumentFile,
        prefix: String,
        relativePath: String,
        config: DigestConfig,
        ignorePatterns: List<String>,
        collector: MutableList<FileItem>,
        treeBuilder: StringBuilder
    ) {
        val files = dir.listFiles().sortedBy { it.name?.lowercase() }
        val validItems = files.filter { file ->
            val name = file.name ?: ""
            val path = if (relativePath.isEmpty()) name else "$relativePath/$name"
            !filter.shouldIgnore(path, ignorePatterns) && name != ".git"
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
                scanDirectorySaf(file, newPrefix, currentPath, config, ignorePatterns, collector, treeBuilder)
            } else {
                val sizeInKb = file.length() / 1024
                if ((config.maxFileSizeKB == 0 || sizeInKb <= config.maxFileSizeKB)) {
                    // --- NEW: Use isSmartSkippable ---
                    if (!filter.isSmartSkippable(file, file.uri)) {
                        collector.add(FileItem(currentPath, name, file.uri, null, sizeInKb))
                    }
                }
            }
        }
    }

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
}
