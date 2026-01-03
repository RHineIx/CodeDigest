package com.codedigest.ix.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error 
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codedigest.ix.R
import com.codedigest.ix.MainViewModel
import com.codedigest.ix.data.ProcessingState
import com.codedigest.ix.ui.components.*
import com.codedigest.ix.ui.sections.ConfigSection
import com.codedigest.ix.ui.sections.SourceSection
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val config by viewModel.config.collectAsState()
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    
    // State for the dialog
    var showAppInfoDialog by remember { mutableStateOf(false) }

    // Scroll to bottom on success
    LaunchedEffect(state) {
        if (state is ProcessingState.Success) {
            delay(300)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Show Dialog if state is true
    if (showAppInfoDialog) { 
        AboutDialog(onDismiss = { showAppInfoDialog = false }) 
    }

    val isProcessing = state is ProcessingState.Scanning || state is ProcessingState.Processing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name), 
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold, 
                        letterSpacing = (-1).sp
                    ), 
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.app_description), 
                    style = MaterialTheme.typography.bodyLarge, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            // About Button
            IconButton(onClick = { showAppInfoDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info, 
                    contentDescription = stringResource(R.string.about_button_desc), 
                    tint = MaterialTheme.colorScheme.primary, 
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Main Content
        Column(modifier = Modifier.alpha(if (isProcessing) 0.6f else 1f)) {
            SourceSection(config, viewModel, isProcessing)
            Spacer(modifier = Modifier.height(24.dp))
            ConfigSection(config, viewModel, !isProcessing)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action Button or Status Panel
        Box(modifier = Modifier.height(72.dp).fillMaxWidth()) {
            Crossfade(targetState = isProcessing, label = "State", animationSpec = tween(400)) { processing ->
                if (processing) {
                    ProcessingStatusPanel(state)
                } else {
                    Button(
                        onClick = { 
                            focusManager.clearFocus()
                            viewModel.startProcessing() 
                        },
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(20.dp),
                        enabled = config.sourceUri != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Bolt, null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.start_digestion), 
                            fontSize = 18.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Result Card Animation
        var lastSuccessState by remember { mutableStateOf<ProcessingState.Success?>(null) }
        if (state is ProcessingState.Success) lastSuccessState = state as ProcessingState.Success

        AnimatedVisibility(
            visible = state is ProcessingState.Success,
            enter = fadeIn() + expandVertically() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + shrinkVertically() + scaleOut(targetScale = 0.9f)
        ) {
            lastSuccessState?.let { ResultCard(it) { viewModel.resetState() } }
        }

        // Error Card Animation
        AnimatedVisibility(visible = state is ProcessingState.Error) {
            (state as? ProcessingState.Error)?.let { 
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp), 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(it.message, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        val bottomSpacer by animateDpAsState(if (state is ProcessingState.Success) 80.dp else 16.dp, label = "pad")
        Spacer(modifier = Modifier.height(bottomSpacer))
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}
