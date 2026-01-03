package com.codedigest.ix.ui.sections

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codedigest.ix.R
import com.codedigest.ix.MainViewModel
import com.codedigest.ix.data.DigestConfig
import com.codedigest.ix.ui.components.SectionHeader
import com.codedigest.ix.ui.components.SelectionCard
import com.codedigest.ix.utils.resolveFileName

@Composable
fun SourceSection(
    config: DigestConfig,
    viewModel: MainViewModel,
    isProcessing: Boolean
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            viewModel.updateUri(it)
        }
    }

    val ignorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            viewModel.updateCustomIgnoreUri(it)
        }
    }

    SectionHeader(stringResource(R.string.section_source))

    // Project Folder
    val tapToSelect = stringResource(R.string.tap_to_select)
    val folderName by produceState(tapToSelect, key1 = config.sourceUri) {
        config.sourceUri?.let { value = resolveFileName(context, it, true) } ?: run { value = tapToSelect }
    }
    
    val selectedPrefix = stringResource(R.string.selected_prefix, folderName)
    SelectionCard(
        icon = Icons.Default.FolderOpen,
        title = stringResource(R.string.project_folder),
        subtitle = if (config.sourceUri != null) selectedPrefix else folderName,
        isSet = config.sourceUri != null,
        onClick = { if (!isProcessing) { focusManager.clearFocus(); sourcePicker.launch(null) } },
        onClear = if (config.sourceUri != null && !isProcessing) { { viewModel.clearSourceUri() } } else null
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Custom GitIgnore
    val optionalText = stringResource(R.string.optional)
    val ignoreName by produceState(optionalText, key1 = config.customGitIgnoreUri) {
        config.customGitIgnoreUri?.let { value = resolveFileName(context, it, false) } ?: run { value = optionalText }
    }
    
    val selectedIgnorePrefix = stringResource(R.string.selected_prefix, ignoreName)
    SelectionCard(
        icon = Icons.Default.Code,
        title = stringResource(R.string.custom_gitignore),
        subtitle = if (config.customGitIgnoreUri != null) selectedIgnorePrefix else ignoreName,
        isSet = config.customGitIgnoreUri != null,
        onClick = { if (!isProcessing) { focusManager.clearFocus(); ignorePicker.launch(arrayOf("*/*")) } },
        onClear = if (config.customGitIgnoreUri != null && !isProcessing) { { viewModel.clearCustomIgnoreUri() } } else null
    )
}
