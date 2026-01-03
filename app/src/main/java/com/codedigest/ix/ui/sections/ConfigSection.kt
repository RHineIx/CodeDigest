package com.codedigest.ix.ui.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codedigest.ix.R
import com.codedigest.ix.MainViewModel
import com.codedigest.ix.data.DigestConfig
import com.codedigest.ix.data.OutputFormat
import com.codedigest.ix.ui.components.SectionHeader
import com.codedigest.ix.ui.components.SwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigSection(
    config: DigestConfig,
    viewModel: MainViewModel,
    enabled: Boolean
) {
    var showStrategyInfoDialog by remember { mutableStateOf(false) }

    // Define limits list HERE, inside the Composable scope
    val limits = listOf(
        0 to stringResource(R.string.limit_none), 
        32000 to stringResource(R.string.limit_32k), 
        128000 to stringResource(R.string.limit_128k), 
        200000 to stringResource(R.string.limit_200k)
    )

    if (showStrategyInfoDialog) {
        AlertDialog(
            onDismissRequest = { showStrategyInfoDialog = false },
            icon = { 
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary
                ) 
            },
            title = { 
                Text(stringResource(R.string.output_strategy), fontWeight = FontWeight.Bold) 
            },
            text = {
                Text(
                    text = stringResource(R.string.output_strategy_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showStrategyInfoDialog = false }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }

    SectionHeader(stringResource(R.string.section_configuration))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            
            // --- Output Strategy ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.output_strategy), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { showStrategyInfoDialog = true }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                }
            }

            // Format Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(OutputFormat.values()) { format ->
                    FilterChip(
                        selected = config.outputFormat == format,
                        onClick = { viewModel.updateOutputFormat(format) },
                        label = { Text(when(format) {
                            OutputFormat.PLAIN_TEXT -> stringResource(R.string.format_plain)
                            OutputFormat.MARKDOWN -> stringResource(R.string.format_markdown)
                            OutputFormat.XML -> stringResource(R.string.format_xml)
                        }) },
                        leadingIcon = if (config.outputFormat == format) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                        enabled = enabled
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))

            // --- Context Splitter UI ---
            Text(stringResource(R.string.split_by_tokens), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(limits) { (valLimit, label) ->
                    FilterChip(
                        selected = config.tokenLimit == valLimit,
                        onClick = { viewModel.updateTokenLimit(valLimit) },
                        label = { Text(label) },
                        leadingIcon = if (config.tokenLimit == valLimit) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                        enabled = enabled
                    )
                }
            }
            
            // Custom Limit Field
            var customLimitText by remember(config.tokenLimit) { 
                mutableStateOf(if (config.tokenLimit == 0 || config.tokenLimit in listOf(32000, 128000, 200000)) "" else config.tokenLimit.toString()) 
            }

            OutlinedTextField(
                value = customLimitText,
                onValueChange = { 
                    if (it.all { char -> char.isDigit() }) {
                        customLimitText = it
                        val newLimit = it.toIntOrNull() ?: 0
                        viewModel.updateTokenLimit(newLimit)
                    }
                },
                label = { Text(stringResource(R.string.custom_limit_label)) },
                placeholder = { Text(stringResource(R.string.custom_limit_placeholder)) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // --- Max File Size ---
            var sizeText by remember(config.maxFileSizeKB) { 
                mutableStateOf(if (config.maxFileSizeKB == 0) "" else config.maxFileSizeKB.toString()) 
            }
            OutlinedTextField(
                value = sizeText,
                onValueChange = { 
                    if (it.all { char -> char.isDigit() }) {
                        sizeText = it
                        viewModel.updateMaxFileSize(it.toIntOrNull() ?: 0)
                    }
                },
                label = { Text(stringResource(R.string.max_file_size_label)) },
                placeholder = { Text(stringResource(R.string.max_file_size_placeholder)) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(12.dp)
            )

            Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
            
            // --- Toggles ---
            SwitchRow(stringResource(R.string.toggle_exclude_gitignore), !config.useGitIgnore, enabled) { viewModel.toggleUseGitIgnore(!it) }
            SwitchRow(stringResource(R.string.toggle_remove_comments), config.removeComments, enabled) { viewModel.toggleRemoveComments(it) }
            SwitchRow(stringResource(R.string.toggle_compact_mode), config.compactMode, enabled) { viewModel.toggleCompactMode(it) }
            SwitchRow(stringResource(R.string.toggle_calculate_tokens), config.showTokenCount, enabled) { viewModel.toggleShowTokens(it) }
            SwitchRow(stringResource(R.string.toggle_skip_tree), config.skipTree, enabled) { viewModel.toggleSkipTree(it) }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            
            // --- Ignore Patterns ---
            Text(stringResource(R.string.exclude_patterns_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Android", "Web", "Python/AI", "Media")) { preset ->
                    SuggestionChip(
                        onClick = { viewModel.applyPreset(preset) },
                        label = { Text(preset) },
                        icon = { Icon(Icons.Default.Add, null, Modifier.size(14.dp)) },
                        enabled = enabled
                    )
                }
            }
            
            var tempExclude by remember(config.excludePatterns) { mutableStateOf(config.excludePatterns.joinToString(", ")) }
            OutlinedTextField(
                value = tempExclude,
                onValueChange = { tempExclude = it; viewModel.updateExcludePatterns(it) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (tempExclude.isNotEmpty() && enabled) {
                        IconButton(onClick = { tempExclude = ""; viewModel.updateExcludePatterns("") }) {
                            Icon(Icons.Default.Close, stringResource(R.string.clear_desc), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }
    }
}
