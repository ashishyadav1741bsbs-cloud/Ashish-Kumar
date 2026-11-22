package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.CharacterEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.SceneEntity
import com.example.model.AspectRatioOption
import com.example.model.StudioPresets
import com.example.model.VideoStyle
import com.example.ui.components.FirebaseAiNoticeBanner
import com.example.ui.components.formatDurationMmSs
import com.example.ui.theme.AnamorphicCyan
import com.example.ui.theme.CinemaAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryboardScreen(
    project: ProjectEntity?,
    characters: List<CharacterEntity>,
    scenes: List<SceneEntity>,
    aiNotice: String?,
    isSplittingStory: Boolean,
    isGeneratingBatch: Boolean,
    isBackendConfigured: Boolean,
    generatingImageSceneIds: Set<Long>,
    generatingVoiceSceneIds: Set<Long>,
    onSplitStory: (String) -> Unit,
    onGenerateAllScenes: () -> Unit,
    onUpdateProjectStyleAndAspect: (VideoStyle, AspectRatioOption) -> Unit,
    onAddBlankScene: () -> Unit,
    onSaveScene: (SceneEntity) -> Unit,
    onSaveAllScenes: (List<SceneEntity>) -> Unit,
    onDeleteScene: (Long) -> Unit,
    onMoveScene: (Long, Int) -> Unit,
    onSaveCharacter: (Long, String, String, String, String, String?) -> Unit,
    onDeleteCharacter: (Long) -> Unit,
    onToggleCharacterLink: (SceneEntity, Long) -> Unit,
    onPersistPickedImage: (SceneEntity, Uri) -> Unit,
    onGenerateSceneImage: (SceneEntity) -> Unit,
    onGenerateVideo: (SceneEntity) -> Unit,
    onAttachLocalClip: (SceneEntity, Uri) -> Unit,
    onSpeakDialogue: (String) -> Unit,
    onSaveHindiVoice: (SceneEntity) -> Unit,
    onPlaySavedVoice: (String) -> Unit,
    onDownloadSceneMp4: (SceneEntity) -> Unit,
    onExportProject: (ProjectEntity) -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (project == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text("कोई सक्रिय प्रोजेक्ट चयनित नहीं है", style = MaterialTheme.typography.headlineSmall)
                Button(
                    onClick = onNavigateToProjects,
                    modifier = Modifier.testTag("go_to_projects_button")
                ) {
                    Text("प्रोजेक्ट चुनें या नया बनाएँ")
                }
            }
        }
        return
    }

    val currentStyle = VideoStyle.fromNameOrDefault(project.styleName)
    val currentAspect = AspectRatioOption.fromNameOrDefault(project.aspectRatioName)
    val totalDurationSec = scenes.sumOf { it.durationSeconds }

    var storyDraft by remember(project.id, project.storyPrompt) { mutableStateOf(project.storyPrompt) }
    var showCharacterDialog by remember { mutableStateOf(false) }
    var editingCharacter by remember { mutableStateOf<CharacterEntity?>(null) }

    var pendingImageScene by remember { mutableStateOf<SceneEntity?>(null) }
    var pendingClipScene by remember { mutableStateOf<SceneEntity?>(null) }

    val scenePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = pendingImageScene
        if (uri != null && target != null) {
            onPersistPickedImage(target, uri)
        }
        pendingImageScene = null
    }

    val localClipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = pendingClipScene
        if (uri != null && target != null) {
            onAttachLocalClip(target, uri)
        }
        pendingClipScene = null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideLayout = maxWidth >= 840.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Backend Configuration Banner when not yet configured
            if (!isBackendConfigured) {
                item {
                    Surface(
                        color = CinemaAmber.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CinemaAmber.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = CinemaAmber
                                )
                                Text(
                                    text = "सुरक्षित वीडियो बैकएंड अभी सेट नहीं है। वीडियो/इमेज जनरेशन के लिए अपना HTTPS बैकएंड URL और API कुंजी जोड़ें।",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CinemaAmber
                                )
                            }
                            Spacer(Modifier.size(8.dp))
                            OutlinedButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier.testTag("storyboard_open_settings_button")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("सेटिंग्स")
                            }
                        }
                    }
                }
            }

            // Project Header + Style & Aspect Ratio Controls + 30-Min Budget Bar
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = CinemaAmber
                                )
                                Text(
                                    text = "${scenes.size} सीन • कुल अवधि ${formatDurationMmSs(totalDurationSec)} / 30:00",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AnamorphicCyan
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onExportProject(project) },
                                    modifier = Modifier.testTag("storyboard_export_project_button")
                                ) {
                                    Icon(Icons.Default.Archive, contentDescription = null)
                                    Spacer(Modifier.size(4.dp))
                                    Text("एक्सपोर्ट")
                                }
                                Button(
                                    onClick = onAddBlankScene,
                                    modifier = Modifier.testTag("add_blank_scene_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.size(4.dp))
                                    Text("नया सीन")
                                }
                            }
                        }

                        LinearProgressIndicator(
                            progress = {
                                (totalDurationSec.toFloat() / StudioPresets.MAX_MOVIE_DURATION_SECONDS.toFloat())
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = AnamorphicCyan
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VideoStyle.entries.forEach { st ->
                                FilterChip(
                                    selected = currentStyle == st,
                                    onClick = { onUpdateProjectStyleAndAspect(st, currentAspect) },
                                    label = { Text(st.hindiLabel) },
                                    modifier = Modifier.testTag("storyboard_style_${st.name.lowercase()}")
                                )
                            }
                            AspectRatioOption.entries.forEach { ar ->
                                FilterChip(
                                    selected = currentAspect == ar,
                                    onClick = { onUpdateProjectStyleAndAspect(currentStyle, ar) },
                                    label = { Text(ar.code) },
                                    modifier = Modifier.testTag("storyboard_aspect_${ar.code.replace(':', '_')}")
                                )
                            }
                        }
                    }
                }
            }

            // Story-to-Scene Auto Splitter + Auto-Generate All + AI Notice
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
                            text = "कहानी से स्वचालित सीन विभाजन और वीडियो निर्माण",
                            style = MaterialTheme.typography.titleMedium
                        )
                        FirebaseAiNoticeBanner(customText = aiNotice)

                        OutlinedTextField(
                            value = storyDraft,
                            onValueChange = { storyDraft = it },
                            label = { Text("पूरी कहानी लिखें या संपादित करें") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("storyboard_story_input")
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onSplitStory(storyDraft) },
                                enabled = storyDraft.isNotBlank() && !isSplittingStory,
                                modifier = Modifier.testTag("split_story_button")
                            ) {
                                if (isSplittingStory) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.size(8.dp))
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                }
                                Text("कहानी को सीन में बाँटें")
                            }

                            OutlinedButton(
                                onClick = onGenerateAllScenes,
                                enabled = scenes.isNotEmpty() && !isGeneratingBatch,
                                modifier = Modifier.testTag("generate_all_scenes_button")
                            ) {
                                if (isGeneratingBatch) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.size(8.dp))
                                } else {
                                    Icon(Icons.Default.Movie, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                }
                                Text("सभी सीन का वीडियो बनाएं")
                            }

                            if (scenes.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { onSaveAllScenes(scenes) },
                                    modifier = Modifier.testTag("save_all_scenes_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(Modifier.size(6.dp))
                                    Text("सभी सीन सेव")
                                }
                            }
                        }
                    }
                }
            }

            // Character Consistency Bible
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Face, contentDescription = null, tint = AnamorphicCyan)
                                Text(
                                    text = "किरदार निरंतरता बाइबल (Characters)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    editingCharacter = null
                                    showCharacterDialog = true
                                },
                                modifier = Modifier.testTag("add_character_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("किरदार जोड़ें")
                            }
                        }

                        if (characters.isEmpty()) {
                            Text(
                                text = "सभी सीन में एक जैसा चेहरा, कपड़े और लुक रखने के लिए मुख्य किरदार जोड़ें।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(characters, key = { it.id }) { ch ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.width(240.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (!ch.referenceImageUri.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = Uri.parse(ch.referenceImageUri),
                                                    contentDescription = ch.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(ch.name, style = MaterialTheme.typography.titleSmall, color = CinemaAmber)
                                                Text(
                                                    ch.visualDescription,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 2
                                                )
                                            }
                                            IconButton(onClick = { onDeleteCharacter(ch.id) }) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "किरदार हटाएं",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Scene Cards (Responsive 2-column grid on wide tablet/desktop or 1-column on mobile)
            if (isWideLayout) {
                val pairs = scenes.chunked(2)
                items(pairs) { rowScenes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        rowScenes.forEach { scene ->
                            SceneCardEditor(
                                scene = scene,
                                aspectRatio = currentAspect,
                                characters = characters,
                                isBackendConfigured = isBackendConfigured,
                                isGeneratingImage = scene.id in generatingImageSceneIds,
                                isGeneratingVoice = scene.id in generatingVoiceSceneIds,
                                onSaveScene = onSaveScene,
                                onDeleteScene = { onDeleteScene(scene.id) },
                                onMoveUp = { onMoveScene(scene.id, -1) },
                                onMoveDown = { onMoveScene(scene.id, 1) },
                                onToggleCharacterLink = { charId -> onToggleCharacterLink(scene, charId) },
                                onPickReferenceImage = { edited ->
                                    pendingImageScene = edited
                                    scenePhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onGenerateSceneImage = onGenerateSceneImage,
                                onGenerateVideo = onGenerateVideo,
                                onAttachLocalClip = { edited ->
                                    pendingClipScene = edited
                                    localClipPicker.launch(arrayOf("video/mp4", "video/*"))
                                },
                                onSpeakDialogue = onSpeakDialogue,
                                onSaveHindiVoice = onSaveHindiVoice,
                                onPlaySavedVoice = onPlaySavedVoice,
                                onDownloadSceneMp4 = onDownloadSceneMp4,
                                onOpenSettings = onNavigateToSettings,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowScenes.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(scenes, key = { it.id }) { scene ->
                    SceneCardEditor(
                        scene = scene,
                        aspectRatio = currentAspect,
                        characters = characters,
                        isBackendConfigured = isBackendConfigured,
                        isGeneratingImage = scene.id in generatingImageSceneIds,
                        isGeneratingVoice = scene.id in generatingVoiceSceneIds,
                        onSaveScene = onSaveScene,
                        onDeleteScene = { onDeleteScene(scene.id) },
                        onMoveUp = { onMoveScene(scene.id, -1) },
                        onMoveDown = { onMoveScene(scene.id, 1) },
                        onToggleCharacterLink = { charId -> onToggleCharacterLink(scene, charId) },
                        onPickReferenceImage = { edited ->
                            pendingImageScene = edited
                            scenePhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onGenerateSceneImage = onGenerateSceneImage,
                        onGenerateVideo = onGenerateVideo,
                        onAttachLocalClip = { edited ->
                            pendingClipScene = edited
                            localClipPicker.launch(arrayOf("video/mp4", "video/*"))
                        },
                        onSpeakDialogue = onSpeakDialogue,
                        onSaveHindiVoice = onSaveHindiVoice,
                        onPlaySavedVoice = onPlaySavedVoice,
                        onDownloadSceneMp4 = onDownloadSceneMp4,
                        onOpenSettings = onNavigateToSettings
                    )
                }
            }
        }
    }

    if (showCharacterDialog) {
        CharacterEditorDialog(
            initial = editingCharacter,
            onDismiss = { showCharacterDialog = false },
            onSave = { id, name, visual, outfit, voice, refUri ->
                onSaveCharacter(id, name, visual, outfit, voice, refUri)
                showCharacterDialog = false
            }
        )
    }
}

@Composable
private fun CharacterEditorDialog(
    initial: CharacterEntity?,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var visual by remember { mutableStateOf(initial?.visualDescription.orEmpty()) }
    var outfit by remember { mutableStateOf(initial?.outfit.orEmpty()) }
    var voice by remember { mutableStateOf(initial?.voiceStyle ?: "गंभीर और स्पष्ट हिंदी आवाज़") }
    var refUri by remember { mutableStateOf(initial?.referenceImageUri) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) refUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("किरदार विवरण जोड़ें") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("किरदार का नाम (उदा. आरव)") },
                    singleLine = true,
                    modifier = Modifier.testTag("character_name_input")
                )
                OutlinedTextField(
                    value = visual,
                    onValueChange = { visual = it },
                    label = { Text("चेहरा, उम्र और बनावट") },
                    minLines = 2,
                    modifier = Modifier.testTag("character_visual_input")
                )
                OutlinedTextField(
                    value = outfit,
                    onValueChange = { outfit = it },
                    label = { Text("कपड़े / वेशभूषा (Outfit)") },
                    singleLine = true,
                    modifier = Modifier.testTag("character_outfit_input")
                )
                OutlinedTextField(
                    value = voice,
                    onValueChange = { voice = it },
                    label = { Text("आवाज़ का अंदाज़") },
                    singleLine = true
                )
                OutlinedButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.testTag("character_pick_photo_button")
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (refUri == null) "रेफ़रेंस फ़ोटो चुनें (Photo Picker)" else "फ़ोटो चुनी गई")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(initial?.id ?: 0L, name, visual, outfit, voice, refUri) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_character_dialog_button")
            ) {
                Text("सेव करें")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("रद्द करें") }
        }
    )
}
