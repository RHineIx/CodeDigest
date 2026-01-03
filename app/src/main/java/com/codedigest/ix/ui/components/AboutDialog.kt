package com.codedigest.ix.ui.components

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codedigest.ix.R
import compose.icons.SimpleIcons
import compose.icons.simpleicons.Github
import compose.icons.simpleicons.Telegram

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    
    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            context.getString(R.string.about_dialog_version, packageInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            context.getString(R.string.about_dialog_version, "1.0")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(84.dp), 
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.app_icon_desc),
                        modifier = Modifier.size(192.dp),
                        tint = Color.Unspecified
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = appVersion,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.about_app_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.designed_by),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                )
                DeveloperCard(
                    title = "RHineIx",
                    subtitle = "@GitHub",
                    icon = SimpleIcons.Github,
                    onClick = { uriHandler.openUri("https://github.com/RHineIx") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DeveloperCard(
                    title = "RHineIx",
                    subtitle = "@Telegram",
                    icon = SimpleIcons.Telegram,
                    onClick = { uriHandler.openUri("https://t.me/RHineIx") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.close_button), fontSize = 16.sp)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
fun DeveloperCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
            )
        }
    }
}
