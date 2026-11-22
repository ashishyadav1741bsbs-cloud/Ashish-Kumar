package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ProjectEntity
import com.example.model.AspectRatioOption
import com.example.model.VideoStyle
import com.example.ui.components.FirebaseAiNoticeBanner
import com.example.ui.theme.AnamorphicCyan
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.ObsidianAbyss
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(
    projects: List<ProjectEntity>,
    activeProjectId: Long?,
    onCreateProject: (
        title: String,
        story: String,
        style: VideoStyle,
        aspectRatio: AspectRatioOption,
        targetMinutes: Int,
        autoSplit: Boolean
    ) -> Unit,
    onOpenProject: (Long) -> Unit,
    onExportProject: (ProjectEntity) -> Unit,
    onDeleteProject: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var newTitle by rememberSaveable { mutableStateOf("") }
    var newStory by rememberSaveable { mutableStateOf("") }
    var selectedStyle by rememberSaveable { mutableStateOf(VideoStyle.REALISTIC_CINEMA) }
    var selectedAspect by rememberSaveable { mutableStateOf(AspectRatioOption.RATIO_16_9) }
    var targetMinutes by rememberSaveable { mutableFloatStateOf(5f) }
    var projectPendingDelete by remember { mutableStateOf<ProjectEntity?>(null) }

    val filteredProjects = remember(projects, searchQuery) {
        if (searchQuery.isBlank()) projects
        else projects.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.storyPrompt.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Luxury Google Flow Hero Studio Banner
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_cinema_hero_1795277502709),
                            contentDescription = stringResource(R.string.desc_cinema_hero),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            ObsidianAbyss.copy(alpha = 0.25f),
                                            ObsidianAbyss.copy(alpha = 0.92f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(18.dp)
                        ) {
                            Surface(
                                color = CinemaAmber.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, CinemaAmber.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "GOOGLE FLOW STYLE STUDIO • 30 MIN MAX",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CinemaAmber,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.studio_brand_hindi),
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.studio_tagline),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                FirebaseAiNoticeBanner()
            }

            item {
                // Quick Project Creation + Auto Story-to-Scene Builder
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MovieCreation,
                                contentDescription = null,
                                tint = CinemaAmber
                            )
                            Text(
                                text = "नया मूवी प्रोजेक्ट बनाएँ",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }

                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text("प्रोजेक्ट का नाम (उदा. हिमालय का रहस्य)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("project_title_input")
                        )

                        OutlinedTextField(
                            value = newStory,
                            onValueChange = { newStory = it },
                            label = { Text("अपनी पूरी कहानी लिखें (सीन में स्वतः विभाजित होगी)") },
                            placeholder = {
                                Text("उदा. सुबह के कोहरे में आरव पुराने मंदिर की ओर बढ़ता है। \"यह दरवाज़ा सदियों से बंद है,\" वह धीरे से कहता है। अचानक नीली रोशनी चमकती है...")
                            },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("project_story_input")
                        )

                        Text(
                            text = "वीडियो स्टाइल चुनें:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VideoStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = selectedStyle == style,
                                    onClick = { selectedStyle = style },
                                    label = { Text(style.hindiLabel) },
                                    modifier = Modifier.testTag("style_chip_${style.name.lowercase()}")
                                )
                            }
                        }

                        Text(
                            text = "आस्पेक्ट रेशियो (Aspect Ratio):",
                            style = MaterialTheme.typography.titleSmall
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AspectRatioOption.entries.forEach { ratio ->
                                FilterChip(
                                    selected = selectedAspect == ratio,
                                    onClick = { selectedAspect = ratio },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AspectRatio,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(ratio.hindiLabel) },
                                    modifier = Modifier.testTag("aspect_chip_${ratio.code.replace(':', '_')}")
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "लक्ष्य मूवी अवधि: ${targetMinutes.roundToInt()} मिनट (अधिकतम 30 मिनट)",
                                style = MaterialTheme.typography.titleSmall,
                                color = AnamorphicCyan
                            )
                            Slider(
                                value = targetMinutes,
                                onValueChange = { targetMinutes = it },
                                valueRange = 1f..30f,
                                steps = 28,
                                modifier = Modifier.testTag("target_duration_slider")
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    onCreateProject(
                                        newTitle,
                                        newStory,
                                        selectedStyle,
                                        selectedAspect,
                                        targetMinutes.roundToInt(),
                                        true
                                    )
                                    newTitle = ""
                                    newStory = ""
                                },
                                enabled = newStory.isNotBlank() || newTitle.isNotBlank(),
                                modifier = Modifier.testTag("create_and_split_project_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("प्रोजेक्ट बनाएँ और सीन विभाजित करें")
                            }

                            OutlinedButton(
                                onClick = {
                                    onCreateProject(
                                        newTitle.ifBlank { "खाली प्रोजेक्ट" },
                                        newStory,
                                        selectedStyle,
                                        selectedAspect,
                                        targetMinutes.roundToInt(),
                                        false
                                    )
                                    newTitle = ""
                                    newStory = ""
                                },
                                modifier = Modifier.testTag("create_blank_project_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("खाली स्टोरीबोर्ड शुरू करें")
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "सहेजे गए प्रोजेक्ट्स (${projects.size})",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "खोजें") },
                    label = { Text("प्रोजेक्ट खोजें...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_projects_input")
                )
            }

            if (filteredProjects.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_storyboard_1795277501952),
                                contentDescription = stringResource(R.string.desc_empty_storyboard),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 220.dp, height = 150.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Text(
                                text = "अभी कोई सेव किया गया प्रोजेक्ट नहीं है",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "ऊपर अपनी कहानी लिखें और 'प्रोजेक्ट बनाएँ' पर टैप करके अपनी पहली हिंदी एआई मूवी शुरू करें।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredProjects, key = { it.id }) { project ->
                    val style = VideoStyle.fromNameOrDefault(project.styleName)
                    val ratio = AspectRatioOption.fromNameOrDefault(project.aspectRatioName)
                    val isSelected = project.id == activeProjectId
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) CinemaAmber else MaterialTheme.colorScheme.outline
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProject(project.id) }
                            .testTag("project_card_${project.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = project.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) CinemaAmber else MaterialTheme.colorScheme.onSurface
                                )
                                if (project.storyPrompt.isNotBlank()) {
                                    Text(
                                        text = project.storyPrompt,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = style.hindiLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        color = AnamorphicCyan.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = ratio.code,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AnamorphicCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "${project.targetDurationMinutes} मिनट लक्ष्य",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onExportProject(project) },
                                    modifier = Modifier.testTag("export_project_${project.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Archive,
                                        contentDescription = "प्रोजेक्ट एक्सपोर्ट करें",
                                        tint = AnamorphicCyan
                                    )
                                }
                                Button(
                                    onClick = { onOpenProject(project.id) },
                                    modifier = Modifier.testTag("open_project_${project.id}")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.size(4.dp))
                                    Text("खोलें")
                                }
                                IconButton(
                                    onClick = { projectPendingDelete = project },
                                    modifier = Modifier.testTag("delete_project_${project.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "प्रोजेक्ट हटाएं",
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

    projectPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { projectPendingDelete = null },
            title = { Text("प्रोजेक्ट हटाएं?") },
            text = { Text("क्या आप '${target.title}' और इसके सभी सीन हमेशा के लिए हटाना चाहते हैं?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(target.id)
                        projectPendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_project")
                ) {
                    Text("हटाएं")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectPendingDelete = null }) {
                    Text("रद्द करें")
                }
            }
        )
    }
}
