package com.codedigest.ix.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.codedigest.ix.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun resolveFileName(context: Context, uri: Uri, isDirectory: Boolean): String = withContext(Dispatchers.IO) {
    try {
        val docFile = if (isDirectory) DocumentFile.fromTreeUri(context, uri) else DocumentFile.fromSingleUri(context, uri)
        var name = docFile?.name ?: uri.lastPathSegment ?: context.getString(R.string.selected_default)
        if (name.contains(":")) name = name.substringAfterLast(":")
        if (name.contains("/")) name = name.substringAfterLast("/")
        name
    } catch (e: Exception) {
        uri.lastPathSegment?.substringAfterLast("/") ?: context.getString(R.string.unknown_filename)
    }
}
