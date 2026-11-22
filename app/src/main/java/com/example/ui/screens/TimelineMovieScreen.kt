package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ProjectEntity
import com.example.data.local.SceneEntity
import com.example.media.MovieExportState
import com.example.model.AspectRatioOption
import com.example.model.SceneJobStatus
import com.example.model.StudioPresets
import com.example.ui.components.ExoVideoPlayerBox
import com.example.ui.components.StatusBadgeChip
import com.example.ui.components.formatDurationMmSs
import com.example.ui.theme.AnamorphicCyan
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioRoseDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineMovieScreen(
    project: ProjectEntity?,
    scenes: List<SceneEntity>,
    exportState: MovieExportState,
    onExportMovie: () -> Unit,
    onCancelExport: () -> Unit,
    onExportProjectZip: (ProjectEntity) -> Unit,
    onSpeakDialogue: (String) -> Unit,
    onNavigateToStoryboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (project == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("कृपया पहले कोई प्रोजेक्ट चुनें।", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val context = LocalContext.current
    val aspectRatio = AspectRatioOption.fromNameOrDefault(project.aspectRatioName)
    val orderedScenes = remember(scenes) { scenes.sortedBy { it.sceneNumber } }
    val readyClips = remember(orderedScenes) { orderedScenes.filter { !it.videoUri.isNullOrBlank() } }

    var selectedSceneId by remember(project.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(orderedScenes) {
        if (selectedSceneId == null || orderedScenes.none { it.id == selectedSceneId }) {
            selectedSceneId = readyClips.firstOrNull()?.id ?: orderedScenes.firstOrNull()?.id
        }
    }

    val activeScene = orderedScenes.firstOrNull { it.id == selectedSceneId }
    val totalPlannedSec = orderedScenes.sumOf { it.durationSeconds }
    val totalReadySec = readyClips.sumOf { it.durationSeconds }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 30-Minute Capacity Header
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${project.title} — मूवी टाइमलाइन",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = CinemaAmber
                                )
                                Text(
                                    text = "तैयार क्लिप्स: ${readyClips.size}/${orderedScenes.size} • तैयार अवधि: ${formatDurationMmSs(totalReadySec)} / 30:00",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AnamorphicCyan
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = aspectRatio.code,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CinemaAmber,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = {
                                (totalReadySec.toFloat() / StudioPresets.MAX_MOVIE_DURATION_SECONDS.toFloat())
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = EmeraldReady
                        )
                        Text(
                            text = "नियोजित कुल लंबाई: ${formatDurationMmSs(totalPlannedSec)} (30 मिनट तक के मल्टी-क्लिप जोड़ने हेतु)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Video Preview Monitor (Strict Real Video Only — Never Fake)
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = activeScene?.let { "सीन #${it.sceneNumber} मॉनिटर (${aspectRatio.hindiLabel})" }
                                ?: "मूवी मॉनिटर",
                            style = MaterialTheme.typography.titleMedium
                        )

                        val activeVideoUri = activeScene?.videoUri
                        if (!activeVideoUri.isNullOrBlank()) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                ExoVideoPlayerBox(
                                    videoUriString = activeVideoUri,
                                    aspectRatio = aspectRatio,
                                    modifier = Modifier.widthIn(
                                        max = if (aspectRatio == AspectRatioOption.RATIO_9_16) 320.dp else 760.dp
                                    )
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 600.dp)
                                    .aspectRatio(aspectRatio.ratioFloat.coerceAtLeast(0.75f))
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF06080D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VideocamOff,
                                        contentDescription = "कोई वीडियो क्लिप नहीं",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = "इस सीन के लिए अभी कोई वास्तविक वीडियो क्लिप तैयार नहीं है",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "नकली वीडियो प्रीव्यू नहीं दिखाया जाता। स्टोरीबोर्ड में जाकर सुरक्षित बैकएंड से वीडियो जनरेट करें या अपनी MP4 क्लिप संलग्न करें।",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = onNavigateToStoryboard,
                                        modifier = Modifier.testTag("timeline_back_to_storyboard")
                                    ) {
                                        Text("स्टोरीबोर्ड पर जाएं")
                                    }
                                }
                            }
                        }

                        if (activeScene != null && activeScene.hindiDialogue.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "हिंदी संवाद:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AnamorphicCyan
                                        )
                                        Text(
                                            text = "“${activeScene.hindiDialogue}”",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onSpeakDialogue(activeScene.hindiDialogue) },
                                        modifier = Modifier.testTag("timeline_speak_dialogue")
                                    ) {
                                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                                        Spacer(Modifier.size(6.dp))
                                        Text("सुनें")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Filmstrip Timeline Sequence
            item {
                Text(
                    text = "सीन फिल्मस्ट्रिप (चुनकर प्रीव्यू देखें)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(orderedScenes, key = { it.id }) { sc ->
                        val isSelected = sc.id == selectedSceneId
                        val st = SceneJobStatus.fromNameOrDefault(sc.jobStatus)
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) CinemaAmber else MaterialTheme.colorScheme.outline
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .width(200.dp)
                                .clickable { selectedSceneId = sc.id }
                                .testTag("timeline_clip_card_${sc.sceneNumber}")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "सीन #${sc.sceneNumber}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CinemaAmber
                                    )
                                    Text(
                                        text = "${sc.durationSeconds}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AnamorphicCyan
                                    )
                                }
                                StatusBadgeChip(status = st)
                                Text(
                                    text = sc.videoPrompt.ifBlank { "प्रॉम्प्ट खाली है" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Full Movie Export & Download (Media3 Transformer)
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.MovieFilter, contentDescription = null, tint = CinemaAmber)
                            Text(
                                text = "मूवी जोड़ें और डाउनलोड करें (Media3 Export)",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Text(
                            text = "सभी तैयार MP4 क्लिप्स (${readyClips.size} क्लिप्स, ${formatDurationMmSs(totalReadySec)}) को एक साथ जोड़कर डिवाइस की Movies/AshishAIVideoStudio गैलरी में सेव करें।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        when (exportState) {
                            is MovieExportState.Idle -> {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = onExportMovie,
                                        enabled = readyClips.isNotEmpty(),
                                        modifier = Modifier.testTag("export_movie_button")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(Modifier.size(8.dp))
                                        Text("पूरी मूवी जोड़ें और डाउनलोड करें")
                                    }
                                    OutlinedButton(
                                        onClick = { onExportProjectZip(project) },
                                        modifier = Modifier.testTag("timeline_export_project_zip_button")
                                    ) {
                                        Icon(Icons.Default.Archive, contentDescription = null)
                                        Spacer(Modifier.size(8.dp))
                                        Text("प्रोजेक्ट एक्सपोर्ट / ZIP डाउनलोड")
                                    }
                                }
                            }
                            is MovieExportState.Exporting -> {
                                LinearProgressIndicator(
                                    progress = { exportState.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = exportState.statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AnamorphicCyan
                                )
                                OutlinedButton(
                                    onClick = onCancelExport,
                                    modifier = Modifier.testTag("cancel_export_button")
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null)
                                    Spacer(Modifier.size(6.dp))
                                    Text("एक्सपोर्ट रद्द करें")
                                }
                            }
                            is MovieExportState.Completed -> {
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
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldReady)
                                            Text(
                                                text = "मूवी सफलतापूर्वक एक्सपोर्ट हुई!",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = EmeraldReady
                                            )
                                        }
                                        Text(
                                            text = "Movies स्थान: ${exportState.displayPath}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (!exportState.downloadsDisplayPath.isNullOrBlank()) {
                                            Text(
                                                text = "Downloads स्थान: ${exportState.downloadsDisplayPath}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = AnamorphicCyan
                                            )
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(exportState.exportedUri, "video/mp4")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    runCatching {
                                                        context.startActivity(viewIntent)
                                                    }.onFailure {
                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "video/mp4"
                                                            putExtra(Intent.EXTRA_STREAM, exportState.exportedUri)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(
                                                            Intent.createChooser(shareIntent, "MP4 वीडियो खोलें")
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.testTag("open_exported_movie_button")
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null)
                                                Spacer(Modifier.size(6.dp))
                                                Text("MP4 खोलें / चलाएँ")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "video/mp4"
                                                        putExtra(Intent.EXTRA_STREAM, exportState.exportedUri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(
                                                        Intent.createChooser(shareIntent, "मूवी शेयर करें")
                                                    )
                                                },
                                                modifier = Modifier.testTag("share_exported_movie_button")
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null)
                                                Spacer(Modifier.size(6.dp))
                                                Text("मूवी शेयर करें")
                                            }
                                            OutlinedButton(onClick = onExportMovie) {
                                                Text("दोबारा एक्सपोर्ट करें")
                                            }
                                        }
                                    }
                                }
                            }
                            is MovieExportState.Error -> {
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
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = exportState.message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = StudioRose
                                            )
                                            OutlinedButton(onClick = onExportMovie) {
                                                Text("पुनः प्रयास करें")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
