package com.codedigest.ix.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.codedigest.ix.R
import com.codedigest.ix.data.ProcessingState
import java.io.File
import java.util.ArrayList

@Composable
fun ProcessingStatusPanel(state: ProcessingState) {
    Card(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state is ProcessingState.Scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.status_scanning), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    } else if (state is ProcessingState.Processing) {
                        Text(stringResource(R.string.status_processing), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (state is ProcessingState.Processing) {
                    Text(
                        "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            val progress = if (state is ProcessingState.Processing) state.progress else 0f
            val isScanning = state is ProcessingState.Scanning
            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                 LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
fun ResultCard(state: ProcessingState.Success, onDone: () -> Unit) {
    val context = LocalContext.current
    val displayLimit = 50
    val filesToShow = state.files.take(displayLimit)
    val remainingCount = state.files.size - displayLimit

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TaskAlt, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.digest_complete), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            
            Spacer(Modifier.height(16.dp))

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatItem(
                    icon = Icons.Default.Code,
                    label = stringResource(R.string.stat_source_files),
                    value = state.totalSourceFiles.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    icon = Icons.Default.Description,
                    label = stringResource(R.string.stat_total_tokens),
                    value = formatTokens(state.totalTokens),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))
            
            Text(
                state.message, 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // List of generated files (Limited)
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filesToShow) { file ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(file.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (remainingCount > 0) {
                        item {
                            Text(
                                text = stringResource(R.string.more_files_count, remainingCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // OPEN: Opens only the first file (Part 1)
                FilledTonalButton(
                    onClick = { 
                        if (state.files.isNotEmpty()) {
                            openFile(context, state.files.first()) 
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.open_part_1))
                }
                
                // SHARE: Shares ALL files together
                FilledTonalButton(
                    onClick = { shareMultipleFiles(context, state.files) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_all))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.done))
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

private fun formatTokens(count: Long): String {
    return when {
        count < 1000 -> count.toString()
        count < 1000000 -> String.format("%.1fk", count / 1000.0)
        else -> String.format("%.1fM", count / 1000000.0)
    }
}

private fun openFile(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/plain")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.intent_open_with)))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun shareMultipleFiles(context: android.content.Context, files: List<File>) {
    try {
        val uris = ArrayList<Uri>()
        files.forEach { file ->
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            uris.add(uri)
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.intent_share_with)))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
