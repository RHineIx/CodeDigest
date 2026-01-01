package com.codedigest.ix.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.codedigest.ix.MainViewModel
import com.codedigest.ix.data.ProcessingState
import com.codedigest.ix.ui.components.*
import kotlinx.coroutines.delay

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
        uri?.let { viewModel.updateUri(it) } 
    }
    
    val ignorePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> 
        uri?.let { viewModel.updateCustomIgnoreUri(it) } 
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

        // --- Header ---
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

        // --- Inputs ---
        Column(modifier = Modifier.alpha(if (isProcessing) 0.6f else 1f)) {
            SectionHeader("SOURCE")
            SelectionCard(
                icon = Icons.Default.FolderOpen,
                title = "Project Folder",
                subtitle = if (config.sourceUri != null) DocumentFile.fromTreeUri(context, config.sourceUri!!)?.name ?: "Selected" else "Tap to select folder",
                isSet = config.sourceUri != null,
                onClick = { if(!isProcessing) { focusManager.clearFocus(); sourcePicker.launch(null) } }
            )
            Spacer(modifier = Modifier.height(12.dp))
            SelectionCard(
                icon = Icons.Default.Code,
                title = "Custom .gitignore",
                subtitle = if (config.customGitIgnoreUri != null) "Custom rules loaded" else "Optional",
                isSet = config.customGitIgnoreUri != null,
                onClick = { if(!isProcessing) { focusManager.clearFocus(); ignorePicker.launch("*/*") } }
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
                    SwitchRow("Exclude .gitignore rules", !config.useGitIgnore, enabled) { viewModel.toggleUseGitIgnore(!it) }
                    SwitchRow("Remove Comments", config.removeComments, enabled) { viewModel.toggleRemoveComments(it) }
                    SwitchRow("Compact Mode", config.compactMode, enabled) { viewModel.toggleCompactMode(it) }
                    SwitchRow("Calculate Tokens", config.showTokenCount, enabled) { viewModel.toggleShowTokens(it) }
                    SwitchRow("Skip Tree Generation", config.skipTree, enabled) { viewModel.toggleSkipTree(it) }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    var tempExclude by remember { mutableStateOf(config.excludePatterns.joinToString(",")) }
                    OutlinedTextField(
                        value = tempExclude,
                        onValueChange = { tempExclude = it; viewModel.updateExcludePatterns(it) },
                        label = { Text("Exclude Patterns") },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Action / Status ---
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

        AnimatedVisibility(visible = state is ProcessingState.Success) {
            val successState = state as? ProcessingState.Success
            if (successState != null) ResultCard(successState) { viewModel.resetState() }
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
