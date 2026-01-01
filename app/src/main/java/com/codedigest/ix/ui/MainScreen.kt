package com.codedigest.ix.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.codedigest.ix.MainViewModel
import com.codedigest.ix.data.ProcessingState
import com.codedigest.ix.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val config by viewModel.config.collectAsState()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    var showInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is ProcessingState.Success) {
            delay(300)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            viewModel.updateUri(it)
        }
    }

    val ignorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            viewModel.updateCustomIgnoreUri(it)
        }
    }

    val isProcessing = state is ProcessingState.Scanning || state is ProcessingState.Processing

    if (showInfoDialog) {
        AboutDialog(onDismiss = { showInfoDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "CodeDigest",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Project Tree & Source Generator",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = { showInfoDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "About",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.alpha(if (isProcessing) 0.6f else 1f)) {
            SectionHeader("SOURCE")

            val folderName by produceState(initialValue = "Tap to select folder", key1 = config.sourceUri) {
                config.sourceUri?.let { uri ->
                    value = "Loading..."
                    value = resolveFileName(context, uri, isDirectory = true)
                } ?: run { value = "Tap to select folder" }
            }

            SelectionCard(
                icon = Icons.Default.FolderOpen,
                title = "Project Folder",
                subtitle = if (config.sourceUri != null) "Selected: $folderName" else folderName,
                isSet = config.sourceUri != null,
                onClick = { if (!isProcessing) { focusManager.clearFocus(); sourcePicker.launch(null) } },
                onClear = if (config.sourceUri != null && !isProcessing) { { viewModel.clearSourceUri() } } else null
            )

            Spacer(modifier = Modifier.height(12.dp))

            val ignoreName by produceState(initialValue = "Optional", key1 = config.customGitIgnoreUri) {
                config.customGitIgnoreUri?.let { uri ->
                    value = "Loading..."
                    value = resolveFileName(context, uri, isDirectory = false)
                } ?: run { value = "Optional" }
            }

            SelectionCard(
                icon = Icons.Default.Code,
                title = "Custom .gitignore",
                subtitle = if (config.customGitIgnoreUri != null) "Selected: $ignoreName" else ignoreName,
                isSet = config.customGitIgnoreUri != null,
                onClick = {
                    if (!isProcessing) {
                        focusManager.clearFocus()
                        ignorePicker.launch(arrayOf("*/*"))
                    }
                },
                onClear = if (config.customGitIgnoreUri != null && !isProcessing) { { viewModel.clearCustomIgnoreUri() } } else null
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("CONFIGURATION")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    val enabled = !isProcessing
                    
                    // FILE SIZE LIMIT CONTROL
                    var sizeText by remember(config.maxFileSizeKB) { 
                        mutableStateOf(if (config.maxFileSizeKB == 0) "" else config.maxFileSizeKB.toString()) 
                    }
                    
                    OutlinedTextField(
                        value = sizeText,
                        onValueChange = { 
                            if (it.all { char -> char.isDigit() }) {
                                sizeText = it
                                val newSize = it.toIntOrNull() ?: 0
                                viewModel.updateMaxFileSize(newSize)
                            }
                        },
                        label = { Text("Max File Size (KB)") },
                        placeholder = { Text("0 = Unlimited") },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )

                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))

                    SwitchRow("Exclude .gitignore rules", !config.useGitIgnore, enabled) { viewModel.toggleUseGitIgnore(!it) }
                    SwitchRow("Remove Comments", config.removeComments, enabled) { viewModel.toggleRemoveComments(it) }
                    SwitchRow("Compact Mode", config.compactMode, enabled) { viewModel.toggleCompactMode(it) }
                    SwitchRow("Calculate Tokens", config.showTokenCount, enabled) { viewModel.toggleShowTokens(it) }
                    SwitchRow("Skip Tree Generation", config.skipTree, enabled) { viewModel.toggleSkipTree(it) }

                    Divider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Text(
                        text = "Exclude Patterns",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // PRESETS ROW
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf("Android", "Web", "Python/AI", "Media")
                        items(presets) { preset ->
                            SuggestionChip(
                                onClick = { viewModel.applyPreset(preset) },
                                label = { Text(preset) },
                                icon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp)) },
                                enabled = enabled
                            )
                        }
                    }

                    var tempExclude by remember(config.excludePatterns) { mutableStateOf(config.excludePatterns.joinToString(", ")) }
                    
                    OutlinedTextField(
                        value = tempExclude,
                        onValueChange = { 
                            tempExclude = it
                            viewModel.updateExcludePatterns(it) 
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        trailingIcon = {
                            if (tempExclude.isNotEmpty() && enabled) {
                                IconButton(onClick = { 
                                    tempExclude = ""
                                    viewModel.updateExcludePatterns("") 
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear patterns", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(modifier = Modifier.height(72.dp).fillMaxWidth()) {
            Crossfade(
                targetState = isProcessing,
                label = "StateTransition",
                animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing)
            ) { processing ->
                if (processing) {
                    ProcessingStatusPanel(state)
                } else {
                    Button(
                        onClick = { focusManager.clearFocus(); viewModel.startProcessing() },
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(20.dp),
                        enabled = config.sourceUri != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Bolt, null)
                        Spacer(Modifier.width(12.dp))
                        Text("START DIGESTION", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        var lastSuccessState by remember { mutableStateOf<ProcessingState.Success?>(null) }
        if (state is ProcessingState.Success) {
            lastSuccessState = state as ProcessingState.Success
        }

        AnimatedVisibility(
            visible = state is ProcessingState.Success,
            enter = fadeIn(animationSpec = tween(600)) +
                    expandVertically(animationSpec = tween(600, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.8f, animationSpec = tween(600, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(500)) +
                   shrinkVertically(animationSpec = tween(500, easing = FastOutSlowInEasing)) +
                   scaleOut(targetScale = 0.9f, animationSpec = tween(500))
        ) {
            lastSuccessState?.let { successData ->
                ResultCard(successData) { viewModel.resetState() }
            }
        }

        AnimatedVisibility(visible = state is ProcessingState.Error) {
            val errorState = state as? ProcessingState.Error
            if (errorState != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = errorState.message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        val bottomSpacerHeight by animateDpAsState(targetValue = if (state is ProcessingState.Success || state is ProcessingState.Error) 80.dp else 16.dp, label = "spacer")
        Spacer(modifier = Modifier.height(bottomSpacerHeight))
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

suspend fun resolveFileName(context: Context, uri: Uri, isDirectory: Boolean): String = withContext(Dispatchers.IO) {
    try {
        val docFile = if (isDirectory) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }

        var name = docFile?.name ?: uri.lastPathSegment ?: "Selected"

        if (name.contains(":")) {
            name = name.substringAfterLast(":")
        }

        if (name.contains("/")) {
            name = name.substringAfterLast("/")
        }

        name
    } catch (e: Exception) {
        uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
    }
}
