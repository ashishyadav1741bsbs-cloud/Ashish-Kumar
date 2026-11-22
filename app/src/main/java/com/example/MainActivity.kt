package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.ProjectEntity
import com.example.ui.StudioViewModel
import com.example.ui.components.ProjectExportDialog
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.StoryboardScreen
import com.example.ui.screens.StudioSettingsScreen
import com.example.ui.screens.TimelineMovieScreen
import com.example.ui.theme.AshishStudioTheme
import kotlinx.serialization.Serializable

@Serializable data object ProjectsRoute
@Serializable data object StoryboardRoute
@Serializable data object TimelineRoute
@Serializable data object SettingsRoute

private data class StudioNavItem(
    val route: Any,
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AshishStudioTheme {
                AshishStudioApp()
            }
        }
    }
}

@Composable
fun AshishStudioApp(
    viewModel: StudioViewModel = viewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val activeCharacters by viewModel.activeCharacters.collectAsStateWithLifecycle()
    val activeScenes by viewModel.activeScenes.collectAsStateWithLifecycle()
    val aiNotice by viewModel.aiNotice.collectAsStateWithLifecycle()
    val isSplittingStory by viewModel.isSplittingStory.collectAsStateWithLifecycle()
    val isGeneratingBatch by viewModel.isGeneratingBatch.collectAsStateWithLifecycle()
    val generatingImageSceneIds by viewModel.generatingImageSceneIds.collectAsStateWithLifecycle()
    val generatingVoiceSceneIds by viewModel.generatingVoiceSceneIds.collectAsStateWithLifecycle()
    val backendConfig by viewModel.backendConfig.collectAsStateWithLifecycle()
    val connectionCheck by viewModel.connectionCheck.collectAsStateWithLifecycle()
    val isCheckingConnection by viewModel.isCheckingConnection.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val zipExportState by viewModel.zipExportState.collectAsStateWithLifecycle()
    val realApkInfo by viewModel.realApkInfo.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    var exportDialogProject by remember { mutableStateOf<ProjectEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val navItems = remember {
        listOf(
            StudioNavItem(ProjectsRoute, R.string.nav_projects, Icons.Default.FolderSpecial, "nav_projects"),
            StudioNavItem(StoryboardRoute, R.string.nav_storyboard, Icons.Default.ViewCarousel, "nav_storyboard"),
            StudioNavItem(TimelineRoute, R.string.nav_timeline, Icons.Default.MovieFilter, "nav_timeline"),
            StudioNavItem(SettingsRoute, R.string.nav_settings, Icons.Default.Settings, "nav_settings")
        )
    }

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(item.labelRes),
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(item.labelRes),
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ProjectsRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<ProjectsRoute> {
                        ProjectsScreen(
                            projects = projects,
                            activeProjectId = selectedProjectId,
                            onCreateProject = { title, story, style, ratio, mins, autoSplit ->
                                viewModel.createProjectAndSplitStory(
                                    title = title,
                                    story = story,
                                    style = style,
                                    aspectRatio = ratio,
                                    targetMinutes = mins,
                                    autoSplit = autoSplit,
                                    onCreated = { navigateTopLevel(StoryboardRoute) }
                                )
                            },
                            onOpenProject = { id ->
                                viewModel.selectProject(id)
                                navigateTopLevel(StoryboardRoute)
                            },
                            onExportProject = { project ->
                                viewModel.resetZipExportState()
                                viewModel.refreshRealApkStatus()
                                exportDialogProject = project
                            },
                            onDeleteProject = { id -> viewModel.deleteProject(id) }
                        )
                    }

                    composable<StoryboardRoute> {
                        StoryboardScreen(
                            project = activeProject,
                            characters = activeCharacters,
                            scenes = activeScenes,
                            aiNotice = aiNotice,
                            isSplittingStory = isSplittingStory,
                            isGeneratingBatch = isGeneratingBatch,
                            isBackendConfigured = backendConfig.isConfigured,
                            generatingImageSceneIds = generatingImageSceneIds,
                            generatingVoiceSceneIds = generatingVoiceSceneIds,
                            onSplitStory = { story -> viewModel.splitStoryIntoScenes(story) },
                            onGenerateAllScenes = { viewModel.generateAllScenesSequential() },
                            onUpdateProjectStyleAndAspect = { style, aspect ->
                                activeProject?.let { p ->
                                    viewModel.updateProjectSettings(
                                        title = p.title,
                                        storyPrompt = p.storyPrompt,
                                        style = style,
                                        aspectRatio = aspect,
                                        targetDurationMinutes = p.targetDurationMinutes
                                    )
                                }
                            },
                            onAddBlankScene = { viewModel.addBlankScene() },
                            onSaveScene = { scene -> viewModel.saveScene(scene) },
                            onSaveAllScenes = { list -> viewModel.saveAllScenes(list) },
                            onDeleteScene = { sceneId -> viewModel.deleteScene(sceneId) },
                            onMoveScene = { sceneId, delta -> viewModel.moveScene(sceneId, delta) },
                            onSaveCharacter = { id, name, visual, outfit, voice, refUri ->
                                viewModel.saveCharacter(id, name, visual, outfit, voice, refUri)
                            },
                            onDeleteCharacter = { id -> viewModel.deleteCharacter(id) },
                            onToggleCharacterLink = { scene, charId ->
                                viewModel.toggleCharacterLinkOnScene(scene, charId)
                            },
                            onPersistPickedImage = { scene, uri ->
                                viewModel.persistPickedReferenceImage(scene, uri)
                            },
                            onGenerateSceneImage = { scene -> viewModel.generateSceneImage(scene) },
                            onGenerateVideo = { scene -> viewModel.generateVideoForScene(scene) },
                            onAttachLocalClip = { scene, uri -> viewModel.attachLocalClipToScene(scene, uri) },
                            onSpeakDialogue = { dialogue -> viewModel.speakHindiDialogue(dialogue) },
                            onSaveHindiVoice = { scene -> viewModel.synthesizeHindiVoiceToScene(scene) },
                            onPlaySavedVoice = { uri -> viewModel.playSavedSceneVoice(uri) },
                            onDownloadSceneMp4 = { scene -> viewModel.downloadSceneMp4(scene) },
                            onExportProject = { project ->
                                viewModel.resetZipExportState()
                                viewModel.refreshRealApkStatus()
                                exportDialogProject = project
                            },
                            onNavigateToProjects = { navigateTopLevel(ProjectsRoute) },
                            onNavigateToSettings = { navigateTopLevel(SettingsRoute) }
                        )
                    }

                    composable<TimelineRoute> {
                        TimelineMovieScreen(
                            project = activeProject,
                            scenes = activeScenes,
                            exportState = exportState,
                            onExportMovie = { viewModel.exportFullMovie() },
                            onCancelExport = { viewModel.cancelMovieExport() },
                            onExportProjectZip = { project ->
                                viewModel.resetZipExportState()
                                viewModel.refreshRealApkStatus()
                                exportDialogProject = project
                            },
                            onSpeakDialogue = { dialogue -> viewModel.speakHindiDialogue(dialogue) },
                            onNavigateToStoryboard = { navigateTopLevel(StoryboardRoute) }
                        )
                    }

                    composable<SettingsRoute> {
                        StudioSettingsScreen(
                            config = backendConfig,
                            connectionCheck = connectionCheck,
                            isCheckingConnection = isCheckingConnection,
                            onSaveConfig = { url, key, model, header, bearer ->
                                viewModel.saveBackendConfig(url, key, model, header, bearer)
                            },
                            onClearConfig = { viewModel.clearBackendConfig() },
                            onTestConnection = { viewModel.testBackendConnection() }
                        )
                    }
                }
            }
        }

        exportDialogProject?.let { targetProject ->
            ProjectExportDialog(
                project = targetProject,
                zipState = zipExportState,
                realApkInfo = realApkInfo,
                onPackageZip = { viewModel.exportProjectZip(targetProject.id) },
                onDownloadZipToDownloads = { packaged ->
                    viewModel.downloadPackagedZipToDownloads(packaged)
                },
                onSaveZipToCustomUri = { packaged, uri ->
                    viewModel.savePackagedZipToCustomUri(packaged, uri)
                },
                onDownloadRealApk = { apk ->
                    viewModel.downloadVerifiedApk(apk)
                },
                onDismiss = { exportDialogProject = null }
            )
        }
    }
}
