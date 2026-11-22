package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.local.ProjectEntity
import com.example.media.RealApkInfo
import com.example.media.ZipExportState
import com.example.ui.theme.AnamorphicCyan
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioRoseDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectExportDialog(
    project: ProjectEntity,
    zipState: ZipExportState,
    realApkInfo: RealApkInfo?,
    onPackageZip: () -> Unit,
    onDownloadZipToDownloads: (ZipExportState.Packaged) -> Unit,
    onSaveZipToCustomUri: (ZipExportState.Packaged, Uri) -> Unit,
    onDownloadRealApk: (RealApkInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { targetUri ->
        if (targetUri != null && zipState is ZipExportState.Packaged) {
            onSaveZipToCustomUri(zipState, targetUri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Archive, contentDescription = null, tint = CinemaAmber)
                Text("प्रोजेक्ट एक्सपोर्ट और डाउनलोड")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "प्रोजेक्ट '${project.title}' का पूरा बंडल (`project.json`, `storyboard.md`, `README.md`, HTML/CSS/JS/React वेब प्रोजेक्ट, और सभी वास्तविक MP4/WAV/इमेज एसेट्स) ZIP के रूप में एक्सपोर्ट करें।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Step 1: Export Project (Package ZIP)
                Button(
                    onClick = onPackageZip,
                    enabled = zipState !is ZipExportState.Packaging,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_project_package_zip_button")
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (zipState is ZipExportState.Packaged) "ZIP दोबारा पैकेज करें (Export Project)"
                        else "प्रोजेक्ट एक्सपोर्ट करें (Export Project)"
                    )
                }

                // Progress / Result / Error Status
                when (zipState) {
                    is ZipExportState.Idle -> Unit
                    is ZipExportState.Packaging -> {
                        LinearProgressIndicator(
                            progress = { zipState.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = AnamorphicCyan
                        )
                        Text(
                            text = zipState.statusHindi,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AnamorphicCyan
                        )
                    }
                    is ZipExportState.Packaged -> {
                        Surface(
                            color = EmeraldReady.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, EmeraldReady.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldReady)
                                    Text(
                                        text = "ZIP पैकेज तैयार (${zipState.fileCount} फ़ाइलें • ${formatBytes(zipState.sizeBytes)})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EmeraldReady
                                    )
                                }
                                Text(
                                    text = zipState.fileName,
                                    style = MaterialTheme.typography.labelMedium
                                )

                                // Step 2: Download ZIP buttons (Downloads folder, Save As, Share)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onDownloadZipToDownloads(zipState) },
                                        modifier = Modifier.testTag("download_zip_button")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(Modifier.size(6.dp))
                                        Text("ZIP डाउनलोड करें (Download ZIP)")
                                    }

                                    OutlinedButton(
                                        onClick = { saveAsLauncher.launch(zipState.fileName) },
                                        modifier = Modifier.testTag("save_as_zip_button")
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                                        Spacer(Modifier.size(6.dp))
                                        Text("फ़ोल्डर चुनें")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val shareUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                zipState.zipFile
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/zip"
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(intent, "प्रोजेक्ट ZIP शेयर करें")
                                            )
                                        },
                                        modifier = Modifier.testTag("share_zip_button")
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                        Spacer(Modifier.size(6.dp))
                                        Text("शेयर करें")
                                    }
                                }

                                if (!zipState.savedDisplayPath.isNullOrBlank()) {
                                    Text(
                                        text = "सफलतापूर्वक सहेजा गया: ${zipState.savedDisplayPath}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = EmeraldReady
                                    )
                                }
                            }
                        }
                    }
                    is ZipExportState.Error -> {
                        Surface(
                            color = StudioRoseDark,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StudioRose.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StudioRose)
                                Text(
                                    text = zipState.messageHindi,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StudioRose
                                )
                            }
                        }
                    }
                }

                // Real APK Check (NEVER show Download APK unless a genuine APK binary exists)
                if (realApkInfo != null) {
                    Surface(
                        color = CinemaAmber.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CinemaAmber.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "सत्यापित Android APK उपलब्ध: ${realApkInfo.fileName} (${formatBytes(realApkInfo.sizeBytes)})",
                                style = MaterialTheme.typography.titleSmall,
                                color = CinemaAmber
                            )
                            Button(
                                onClick = { onDownloadRealApk(realApkInfo) },
                                modifier = Modifier.testTag("download_real_apk_button")
                            ) {
                                Icon(Icons.Default.Android, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Download APK")
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AnamorphicCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "APK डाउनलोड स्थिति: कोई वास्तविक बिल्ट APK फ़ाइल उपलब्ध नहीं है (नकली या खाली APK फ़ाइल नहीं बनाई जाती)। असली APK/AAB बनाने के लिए AI Studio मेनू में 'Generate APK' का उपयोग करें।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_export_dialog_button")
            ) {
                Text("बंद करें")
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    }
}
