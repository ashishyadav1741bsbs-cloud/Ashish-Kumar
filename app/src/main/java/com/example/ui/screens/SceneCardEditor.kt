package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.local.CharacterEntity
import com.example.data.local.SceneEntity
import com.example.model.AspectRatioOption
import com.example.model.GenerationMode
import com.example.model.SceneJobStatus
import com.example.model.StudioPresets
import com.example.ui.components.ExoVideoPlayerBox
import com.example.ui.components.StatusBadgeChip
import com.example.ui.theme.AnamorphicCyan
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioRoseDark
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SceneCardEditor(
    scene: SceneEntity,
    aspectRatio: AspectRatioOption,
    characters: List<CharacterEntity>,
    isBackendConfigured: Boolean,
    isGeneratingImage: Boolean,
    isGeneratingVoice: Boolean,
    onSaveScene: (SceneEntity) -> Unit,
    onDeleteScene: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleCharacterLink: (Long) -> Unit,
    onPickReferenceImage: (SceneEntity) -> Unit,
    onGenerateSceneImage: (SceneEntity) -> Unit,
    onGenerateVideo: (SceneEntity) -> Unit,
    onAttachLocalClip: (SceneEntity) -> Unit,
    onSpeakDialogue: (String) -> Unit,
    onSaveHindiVoice: (SceneEntity) -> Unit,
    onPlaySavedVoice: (String) -> Unit,
    onDownloadSceneMp4: (SceneEntity) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var videoPrompt by remember(scene.id, scene.videoPrompt) { mutableStateOf(scene.videoPrompt) }
    var characterDesc by remember(scene.id, scene.characterDescription) { mutableStateOf(scene.characterDescription) }
    var hindiDialogue by remember(scene.id, scene.hindiDialogue) { mutableStateOf(scene.hindiDialogue) }
    var cameraAngle by remember(scene.id, scene.cameraAngle) { mutableStateOf(scene.cameraAngle) }
    var background by remember(scene.id, scene.background) { mutableStateOf(scene.background) }
    var durationSeconds by remember(scene.id, scene.durationSeconds) { mutableIntStateOf(scene.durationSeconds) }
    var mode by remember(scene.id, scene.generationMode) {
        mutableStateOf(GenerationMode.fromNameOrDefault(scene.generationMode))
    }

    var angleMenuExpanded by remember { mutableStateOf(false) }
    var durationMenuExpanded by remember { mutableStateOf(false) }

    val hasUnsavedChanges = videoPrompt != scene.videoPrompt ||
        characterDesc != scene.characterDescription ||
        hindiDialogue != scene.hindiDialogue ||
        cameraAngle != scene.cameraAngle ||
        background != scene.background ||
        durationSeconds != scene.durationSeconds ||
        mode.name != scene.generationMode

    val status = SceneJobStatus.fromNameOrDefault(scene.jobStatus)
    val linkedIds = remember(scene.linkedCharacterIdsCsv) {
        scene.linkedCharacterIdsCsv.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
    }

    fun currentEditedScene(): SceneEntity = scene.copy(
        videoPrompt = videoPrompt,
        characterDescription = characterDesc,
        hindiDialogue = hindiDialogue,
        cameraAngle = cameraAngle,
        background = background,
        durationSeconds = durationSeconds,
        generationMode = mode.name
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = if (hasUnsavedChanges) 1.5.dp else 1.dp,
            color = if (hasUnsavedChanges) CinemaAmber.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scene Header Row: Scene Number + Status + Move/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = CinemaAmber.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CinemaAmber.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "सीन #${scene.sceneNumber}",
                            style = MaterialTheme.typography.labelLarge,
                            color = CinemaAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    StatusBadgeChip(status = status)
                    if (hasUnsavedChanges) {
                        Surface(
                            color = CinemaAmber.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "असहेजे",
                                style = MaterialTheme.typography.labelSmall,
                                color = CinemaAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMoveUp,
                        modifier = Modifier.testTag("scene_move_up_${scene.sceneNumber}")
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "सीन ऊपर ले जाएं")
                    }
                    IconButton(
                        onClick = onMoveDown,
                        modifier = Modifier.testTag("scene_move_down_${scene.sceneNumber}")
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "सीन नीचे ले जाएं")
                    }
                    IconButton(
                        onClick = onDeleteScene,
                        modifier = Modifier.testTag("scene_delete_${scene.sceneNumber}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "सीन हटाएं",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (scene.isAiPlaceholder) {
                Surface(
                    color = AnamorphicCyan.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "नमूना AI प्रारूप (Firebase AI Logic अनुपलब्ध) — आप नीचे सभी फ़ील्ड संपादित कर सकते हैं।",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnamorphicCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Text-to-Video vs Image-to-Video selector
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GenerationMode.entries.forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = {
                            mode = option
                            onSaveScene(currentEditedScene().copy(generationMode = option.name))
                        },
                        label = { Text(option.hindiLabel) },
                        modifier = Modifier.testTag("scene_mode_${scene.sceneNumber}_${option.name.lowercase()}")
                    )
                }
            }

            // Per-Scene Image Generation + Reference Photo Controls (available in both modes, required in Image-to-Video)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!scene.referenceImageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = Uri.parse(scene.referenceImageUri),
                        contentDescription = "सीन #${scene.sceneNumber} फ़ोटो",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedButton(
                        onClick = { onPickReferenceImage(currentEditedScene()) },
                        modifier = Modifier.testTag("scene_pick_image_${scene.sceneNumber}")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (scene.referenceImageUri.isNullOrBlank()) "फ़ोटो चुनें"
                            else "फ़ोटो बदलें"
                        )
                    }

                    OutlinedButton(
                        onClick = { onGenerateSceneImage(currentEditedScene()) },
                        enabled = !isGeneratingImage,
                        modifier = Modifier.testTag("scene_generate_image_${scene.sceneNumber}")
                    ) {
                        if (isGeneratingImage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        }
                        Spacer(Modifier.size(6.dp))
                        Text("सीन AI फ़ोटो बनाएँ")
                    }
                }
            }

            // Video Prompt
            OutlinedTextField(
                value = videoPrompt,
                onValueChange = { videoPrompt = it },
                label = { Text("वीडियो प्रॉम्प्ट (Video Prompt)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scene_prompt_${scene.sceneNumber}")
            )

            // Character Consistency Bible Links
            if (characters.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "किरदार निरंतरता लिंक करें (Character Consistency):",
                        style = MaterialTheme.typography.labelLarge,
                        color = AnamorphicCyan
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        characters.forEach { ch ->
                            FilterChip(
                                selected = ch.id in linkedIds,
                                onClick = { onToggleCharacterLink(ch.id) },
                                label = { Text(ch.name) },
                                modifier = Modifier.testTag("scene_${scene.sceneNumber}_char_${ch.id}")
                            )
                        }
                    }
                }
            }

            // Character Description
            OutlinedTextField(
                value = characterDesc,
                onValueChange = { characterDesc = it },
                label = { Text("किरदार विवरण (Character Description)") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scene_char_desc_${scene.sceneNumber}")
            )

            // Hindi Dialogue + TTS Controls
            OutlinedTextField(
                value = hindiDialogue,
                onValueChange = { hindiDialogue = it },
                label = { Text("हिंदी संवाद (Hindi Dialogue)") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scene_dialogue_${scene.sceneNumber}")
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onSpeakDialogue(hindiDialogue) },
                    enabled = hindiDialogue.isNotBlank(),
                    modifier = Modifier.testTag("scene_tts_speak_${scene.sceneNumber}")
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("हिंदी आवाज़ सुनें")
                }

                OutlinedButton(
                    onClick = { onSaveHindiVoice(currentEditedScene()) },
                    enabled = hindiDialogue.isNotBlank() && !isGeneratingVoice,
                    modifier = Modifier.testTag("scene_tts_save_${scene.sceneNumber}")
                ) {
                    if (isGeneratingVoice) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.GraphicEq, contentDescription = null)
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (scene.voiceAudioUri.isNullOrBlank()) "AI आवाज़ सेव करें"
                        else "आवाज़ अपडेट करें"
                    )
                }

                if (!scene.voiceAudioUri.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onPlaySavedVoice(scene.voiceAudioUri) },
                        modifier = Modifier.testTag("scene_tts_play_${scene.sceneNumber}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = EmeraldReady)
                        Spacer(Modifier.size(4.dp))
                        Text("सेव आवाज़ चलाएँ", color = EmeraldReady)
                    }
                }
            }

            // Camera Angle & Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { angleMenuExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scene_camera_btn_${scene.sceneNumber}")
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(cameraAngle, maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = angleMenuExpanded,
                        onDismissRequest = { angleMenuExpanded = false }
                    ) {
                        StudioPresets.cameraAngles.forEach { angle ->
                            DropdownMenuItem(
                                text = { Text(angle) },
                                onClick = {
                                    cameraAngle = angle
                                    angleMenuExpanded = false
                                    onSaveScene(currentEditedScene().copy(cameraAngle = angle))
                                }
                            )
                        }
                    }
                }

                Column {
                    OutlinedButton(
                        onClick = { durationMenuExpanded = true },
                        modifier = Modifier.testTag("scene_duration_btn_${scene.sceneNumber}")
                    ) {
                        Text("${durationSeconds} सेकंड")
                    }
                    DropdownMenu(
                        expanded = durationMenuExpanded,
                        onDismissRequest = { durationMenuExpanded = false }
                    ) {
                        StudioPresets.durationOptionsSeconds.forEach { sec ->
                            DropdownMenuItem(
                                text = { Text("$sec सेकंड") },
                                onClick = {
                                    durationSeconds = sec
                                    durationMenuExpanded = false
                                    onSaveScene(currentEditedScene().copy(durationSeconds = sec))
                                }
                            )
                        }
                    }
                }
            }

            // Background
            OutlinedTextField(
                value = background,
                onValueChange = { background = it },
                label = { Text("पृष्ठभूमि / बैकग्राउंड (Background)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scene_background_${scene.sceneNumber}")
            )

            // Action Buttons: Generate Video, Save Scene, Attach Real MP4
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onGenerateVideo(currentEditedScene()) },
                    enabled = status != SceneJobStatus.QUEUED && status != SceneJobStatus.RUNNING,
                    modifier = Modifier.testTag("scene_generate_video_${scene.sceneNumber}")
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("वीडियो जनरेट करें")
                }

                OutlinedButton(
                    onClick = { onSaveScene(currentEditedScene()) },
                    modifier = Modifier.testTag("scene_save_${scene.sceneNumber}")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("सीन सेव करें")
                }

                TextButton(
                    onClick = { onAttachLocalClip(currentEditedScene()) },
                    modifier = Modifier.testTag("scene_attach_clip_${scene.sceneNumber}")
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("MP4 क्लिप जोड़ें")
                }
            }

            // Actual Status / Provider Job / Error Surface (NO fake previews)
            if (scene.statusMessage.isNotBlank() || !scene.errorMessage.isNullOrBlank()) {
                val isError = !scene.errorMessage.isNullOrBlank()
                Surface(
                    color = if (isError) StudioRoseDark else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isError) StudioRose.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (isError) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "त्रुटि",
                                tint = StudioRose,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!scene.providerJobId.isNullOrBlank()) {
                                Text(
                                    text = "Job ID: ${scene.providerJobId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnamorphicCyan
                                )
                            }
                            Text(
                                text = scene.errorMessage ?: scene.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isError) StudioRose else MaterialTheme.colorScheme.onSurface
                            )
                            if (isError && !isBackendConfigured) {
                                OutlinedButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.testTag("open_settings_from_scene_error_${scene.sceneNumber}")
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("बैकएंड सेटिंग्स खोलें")
                                }
                            }
                        }
                    }
                }
            }

            // Real Video Preview + Direct MP4 Download/Share ONLY when an actual MP4 URI exists
            if (!scene.videoUri.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "वास्तविक वीडियो प्रीव्यू (${aspectRatio.code})",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldReady
                )
                ExoVideoPlayerBox(
                    videoUriString = scene.videoUri,
                    aspectRatio = aspectRatio
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onDownloadSceneMp4(currentEditedScene()) },
                        modifier = Modifier.testTag("scene_download_mp4_${scene.sceneNumber}")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("MP4 डाउनलोड करें")
                    }
                    OutlinedButton(
                        onClick = {
                            val parsed = Uri.parse(scene.videoUri)
                            val shareUri = if (parsed.scheme == "file" && !parsed.path.isNullOrBlank()) {
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    File(parsed.path!!)
                                )
                            } else {
                                parsed
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "सीन MP4 शेयर करें")
                            )
                        },
                        modifier = Modifier.testTag("scene_share_mp4_${scene.sceneNumber}")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("शेयर करें")
                    }
                }
            }
        }
    }
}
