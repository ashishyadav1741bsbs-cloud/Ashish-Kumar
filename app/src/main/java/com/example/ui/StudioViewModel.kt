package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiImageResult
import com.example.data.ai.AiRepository
import com.example.data.ai.PlaceholderAiRepository
import com.example.data.local.CharacterEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.SceneEntity
import com.example.data.local.StudioDatabase
import com.example.data.local.StudioRepository
import com.example.data.video.BackendMediaResult
import com.example.data.video.ConnectionCheckResult
import com.example.data.video.OwnerBackendConfig
import com.example.data.video.SecureBackendConfigStore
import com.example.data.video.VideoGenerationGateway
import com.example.data.video.VideoJobUpdate
import com.example.media.HindiVoiceGenerator
import com.example.media.MovieAssembler
import com.example.media.MovieExportState
import com.example.media.ProjectZipExporter
import com.example.media.RealApkInfo
import com.example.media.TtsResult
import com.example.media.ZipExportState
import com.example.model.AspectRatioOption
import com.example.model.GenerationMode
import com.example.model.SceneJobStatus
import com.example.model.StudioPresets
import com.example.model.VideoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val database = StudioDatabase.getInstance(application)
    private val repository = StudioRepository(database.studioDao())
    private val aiRepository: AiRepository = PlaceholderAiRepository()
    private val configStore = SecureBackendConfigStore(application)
    private val videoGateway = VideoGenerationGateway(application, configStore)
    private val voiceGenerator = HindiVoiceGenerator(application)
    private val movieAssembler = MovieAssembler(application)
    private val zipExporter = ProjectZipExporter(application)

    private val _selectedProjectId = MutableStateFlow<Long?>(null)
    val selectedProjectId: StateFlow<Long?> = _selectedProjectId.asStateFlow()

    val projects: StateFlow<List<ProjectEntity>> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProject: StateFlow<ProjectEntity?> = _selectedProjectId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeCharacters: StateFlow<List<CharacterEntity>> = _selectedProjectId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeCharacters(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeScenes: StateFlow<List<SceneEntity>> = _selectedProjectId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeScenes(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backendConfig: StateFlow<OwnerBackendConfig> = configStore.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OwnerBackendConfig())

    val exportState: StateFlow<MovieExportState> = movieAssembler.exportState

    val zipExportState: StateFlow<ZipExportState> = zipExporter.state

    private val _realApkInfo = MutableStateFlow<RealApkInfo?>(zipExporter.findRealBuiltApk())
    val realApkInfo: StateFlow<RealApkInfo?> = _realApkInfo.asStateFlow()

    private val _aiNotice = MutableStateFlow<String?>(null)
    val aiNotice: StateFlow<String?> = _aiNotice.asStateFlow()

    private val _isSplittingStory = MutableStateFlow(false)
    val isSplittingStory: StateFlow<Boolean> = _isSplittingStory.asStateFlow()

    private val _isGeneratingBatch = MutableStateFlow(false)
    val isGeneratingBatch: StateFlow<Boolean> = _isGeneratingBatch.asStateFlow()

    private val _generatingImageSceneIds = MutableStateFlow<Set<Long>>(emptySet())
    val generatingImageSceneIds: StateFlow<Set<Long>> = _generatingImageSceneIds.asStateFlow()

    private val _generatingVoiceSceneIds = MutableStateFlow<Set<Long>>(emptySet())
    val generatingVoiceSceneIds: StateFlow<Set<Long>> = _generatingVoiceSceneIds.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _connectionCheck = MutableStateFlow<ConnectionCheckResult?>(null)
    val connectionCheck: StateFlow<ConnectionCheckResult?> = _connectionCheck.asStateFlow()

    private val _isCheckingConnection = MutableStateFlow(false)
    val isCheckingConnection: StateFlow<Boolean> = _isCheckingConnection.asStateFlow()

    private val activeVideoJobs = mutableMapOf<Long, Job>()

    fun selectProject(projectId: Long) {
        _selectedProjectId.value = projectId
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun createProjectAndSplitStory(
        title: String,
        story: String,
        style: VideoStyle,
        aspectRatio: AspectRatioOption,
        targetMinutes: Int,
        autoSplit: Boolean,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val newId = repository.createProject(title, story, style, aspectRatio, targetMinutes)
            _selectedProjectId.value = newId
            if (autoSplit && story.isNotBlank()) {
                splitStoryIntoScenesForProject(newId, story, style, targetMinutes)
            } else {
                repository.addBlankScene(newId)
            }
            _feedbackMessage.value = "प्रोजेक्ट सुरक्षित रूप से सहेजा गया।"
            onCreated(newId)
        }
    }

    fun updateProjectSettings(
        title: String,
        storyPrompt: String,
        style: VideoStyle,
        aspectRatio: AspectRatioOption,
        targetDurationMinutes: Int
    ) {
        val current = activeProject.value ?: return
        viewModelScope.launch {
            repository.updateProject(
                current.copy(
                    title = title.trim().ifBlank { current.title },
                    storyPrompt = storyPrompt,
                    styleName = style.name,
                    aspectRatioName = aspectRatio.name,
                    targetDurationMinutes = targetDurationMinutes.coerceIn(1, 30)
                )
            )
            _feedbackMessage.value = "प्रोजेक्ट अपडेट किया गया।"
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            if (_selectedProjectId.value == projectId) {
                _selectedProjectId.value = null
            }
            _feedbackMessage.value = "प्रोजेक्ट हटाया गया।"
        }
    }

    fun splitStoryIntoScenes(story: String) {
        val project = activeProject.value ?: return
        val style = VideoStyle.fromNameOrDefault(project.styleName)
        viewModelScope.launch {
            repository.updateProject(project.copy(storyPrompt = story))
            splitStoryIntoScenesForProject(project.id, story, style, project.targetDurationMinutes)
        }
    }

    private suspend fun splitStoryIntoScenesForProject(
        projectId: Long,
        story: String,
        style: VideoStyle,
        targetMinutes: Int
    ) {
        _isSplittingStory.value = true
        try {
            val result = aiRepository.splitStoryIntoScenes(story, style, targetMinutes)
            val entities = result.scenes.map { draft ->
                SceneEntity(
                    projectId = projectId,
                    sceneNumber = draft.sceneNumber,
                    videoPrompt = draft.videoPrompt,
                    characterDescription = draft.characterDescription,
                    hindiDialogue = draft.hindiDialogue,
                    cameraAngle = draft.cameraAngle,
                    background = draft.background,
                    durationSeconds = draft.durationSeconds,
                    isAiPlaceholder = draft.isPlaceholder
                )
            }
            repository.replaceScenes(projectId, entities)
            _aiNotice.value = result.noticeHindi
            _feedbackMessage.value = "${entities.size} सीन तैयार होकर सेव हो गए।"
        } catch (e: Exception) {
            _feedbackMessage.value = e.localizedMessage ?: "कहानी विभाजन में त्रुटि हुई।"
        } finally {
            _isSplittingStory.value = false
        }
    }

    fun addBlankScene() {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            val totalSeconds = activeScenes.value.sumOf { it.durationSeconds }
            if (totalSeconds >= StudioPresets.MAX_MOVIE_DURATION_SECONDS) {
                _feedbackMessage.value = "अधिकतम 30 मिनट (1800 सेकंड) की सीमा पूरी हो चुकी है।"
                return@launch
            }
            repository.addBlankScene(projectId)
            _feedbackMessage.value = "नया सीन जोड़ा गया।"
        }
    }

    fun saveScene(scene: SceneEntity, showToast: Boolean = true) {
        viewModelScope.launch {
            repository.saveScene(scene)
            if (showToast) {
                _feedbackMessage.value = "सीन #${scene.sceneNumber} सुरक्षित रूप से सेव हो गया।"
            }
        }
    }

    fun saveAllScenes(scenes: List<SceneEntity>) {
        if (scenes.isEmpty()) return
        viewModelScope.launch {
            scenes.forEach { repository.saveScene(it) }
            _feedbackMessage.value = "सभी ${scenes.size} सीन सेव हो गए।"
        }
    }

    fun deleteScene(sceneId: Long) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            activeVideoJobs.remove(sceneId)?.cancel()
            repository.deleteSceneAndRenumber(projectId, sceneId)
            _feedbackMessage.value = "सीन हटाया गया।"
        }
    }

    fun moveScene(sceneId: Long, delta: Int) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            repository.moveScene(projectId, sceneId, delta)
        }
    }

    fun saveCharacter(
        existingId: Long = 0,
        name: String,
        visualDescription: String,
        outfit: String,
        voiceStyle: String,
        referenceImageUri: String?
    ) {
        val projectId = _selectedProjectId.value ?: return
        if (name.isBlank()) {
            _feedbackMessage.value = "किरदार का नाम खाली नहीं हो सकता।"
            return
        }
        viewModelScope.launch {
            repository.upsertCharacter(
                CharacterEntity(
                    id = existingId,
                    projectId = projectId,
                    name = name.trim(),
                    visualDescription = visualDescription.trim(),
                    outfit = outfit.trim(),
                    voiceStyle = voiceStyle.trim(),
                    referenceImageUri = referenceImageUri
                )
            )
            _feedbackMessage.value = "किरदार '${name.trim()}' सेव हो गया — सभी सीन में निरंतरता लागू होगी।"
        }
    }

    fun deleteCharacter(characterId: Long) {
        viewModelScope.launch {
            repository.deleteCharacter(characterId)
        }
    }

    fun toggleCharacterLinkOnScene(scene: SceneEntity, characterId: Long) {
        val currentIds = scene.linkedCharacterIdsCsv
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .toMutableSet()
        if (!currentIds.add(characterId)) {
            currentIds.remove(characterId)
        }
        val updatedCsv = currentIds.sorted().joinToString(",")
        viewModelScope.launch {
            repository.saveScene(scene.copy(linkedCharacterIdsCsv = updatedCsv))
        }
    }

    fun persistPickedReferenceImage(scene: SceneEntity, sourceUri: Uri) {
        viewModelScope.launch {
            repository.saveScene(scene)
            val persistedUri = copyPickedImageToInternalStorage(scene.id, sourceUri)
                ?: sourceUri.toString()
            repository.updateSceneReferenceImage(scene.id, persistedUri)
            _feedbackMessage.value = "सीन #${scene.sceneNumber} के लिए रेफ़रेंस इमेज सेव हो गई।"
        }
    }

    private suspend fun copyPickedImageToInternalStorage(sceneId: Long, sourceUri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val imagesDir = File(app.filesDir, "scene_images").apply { mkdirs() }
                val dest = File(imagesDir, "scene_ref_${sceneId}_${System.currentTimeMillis()}.jpg")
                app.contentResolver.openInputStream(sourceUri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                } ?: return@withContext null
                if (dest.exists() && dest.length() > 0L) Uri.fromFile(dest).toString() else null
            } catch (_: Exception) {
                null
            }
        }

    fun attachLocalClipToScene(scene: SceneEntity, clipUri: Uri) {
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    clipUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            repository.saveScene(
                scene.copy(
                    videoUri = clipUri.toString(),
                    jobStatus = SceneJobStatus.SUCCEEDED.name,
                    statusMessage = "उपयोगकर्ता द्वारा संलग्न वास्तविक MP4 क्लिप",
                    errorMessage = null
                )
            )
            _feedbackMessage.value = "सीन ${scene.sceneNumber} में वीडियो क्लिप जोड़ दी गई।"
        }
    }

    fun generateSceneImage(editedScene: SceneEntity) {
        val project = activeProject.value ?: return
        viewModelScope.launch {
            repository.saveScene(editedScene)
            _generatingImageSceneIds.value = _generatingImageSceneIds.value + editedScene.id
            try {
                val latestScene = repository.getSceneById(editedScene.id) ?: editedScene
                val projectCharacters = repository.getCharactersForProject(project.id)
                val compiledPrompt = repository.buildConsistentGenerationPrompt(
                    project = project,
                    scene = latestScene,
                    projectCharacters = projectCharacters
                )
                val style = VideoStyle.fromNameOrDefault(project.styleName)
                val aspect = AspectRatioOption.fromNameOrDefault(project.aspectRatioName)

                when (
                    val res = videoGateway.generateSceneImage(
                        sceneId = latestScene.id,
                        compiledPrompt = compiledPrompt,
                        style = style,
                        aspectRatio = aspect,
                        characterDescription = latestScene.characterDescription,
                        background = latestScene.background
                    )
                ) {
                    is BackendMediaResult.Success -> {
                        repository.updateSceneReferenceImage(latestScene.id, res.localUri)
                        _feedbackMessage.value = "सीन #${latestScene.sceneNumber} की AI फ़ोटो तैयार हो गई।"
                    }
                    is BackendMediaResult.Failed -> {
                        val fallbackMsg = if (res.isConfigMissing || res.endpointUnsupported) {
                            when (val aiNoticeRes = aiRepository.generateSceneImageUnavailableNotice(
                                sceneNumber = latestScene.sceneNumber,
                                prompt = compiledPrompt,
                                style = style,
                                aspectRatio = aspect
                            )) {
                                is AiImageResult.Error -> aiNoticeRes.messageHindi
                            }
                        } else {
                            res.errorMessage
                        }
                        repository.updateSceneJobStatus(
                            sceneId = latestScene.id,
                            jobStatus = latestScene.jobStatus,
                            providerJobId = latestScene.providerJobId,
                            statusMessage = latestScene.statusMessage,
                            errorMessage = fallbackMsg
                        )
                        _feedbackMessage.value = fallbackMsg
                    }
                }
            } finally {
                _generatingImageSceneIds.value = _generatingImageSceneIds.value - editedScene.id
            }
        }
    }

    fun speakHindiDialogue(dialogue: String) {
        viewModelScope.launch {
            when (val res = voiceGenerator.speakDialogue(dialogue)) {
                is TtsResult.AudioSaved -> Unit
                is TtsResult.Error -> _feedbackMessage.value = res.messageHindi
            }
        }
    }

    fun playSavedSceneVoice(uriString: String) {
        voiceGenerator.playAudioFile(uriString) { errMsg ->
            _feedbackMessage.value = errMsg
        }
    }

    fun synthesizeHindiVoiceToScene(editedScene: SceneEntity) {
        viewModelScope.launch {
            repository.saveScene(editedScene)
            if (editedScene.hindiDialogue.isBlank()) {
                _feedbackMessage.value = "कृपया पहले हिंदी संवाद लिखें।"
                return@launch
            }

            _generatingVoiceSceneIds.value = _generatingVoiceSceneIds.value + editedScene.id
            try {
                val config = configStore.getConfigOnce()
                // 1. Try server-side AI Hindi TTS if owner backend is configured
                if (config.isConfigured && config.backendBaseUrl.startsWith("https://")) {
                    val serverRes = videoGateway.generateServerHindiVoice(
                        sceneId = editedScene.id,
                        dialogue = editedScene.hindiDialogue
                    )
                    if (serverRes is BackendMediaResult.Success) {
                        repository.updateSceneVoiceResult(editedScene.id, serverRes.localUri)
                        _feedbackMessage.value = "सीन #${editedScene.sceneNumber} की AI हिंदी आवाज़ तैयार और सेव हो गई।"
                        return@launch
                    } else if (serverRes is BackendMediaResult.Failed && !serverRes.endpointUnsupported && !serverRes.isConfigMissing) {
                        // Fall through to device TTS while surfacing note if device TTS also fails
                    }
                }

                // 2. Fall back to on-device Android hi-IN TextToSpeech
                when (val res = voiceGenerator.synthesizeDialogueToFile(editedScene.id, editedScene.hindiDialogue)) {
                    is TtsResult.AudioSaved -> {
                        repository.updateSceneVoiceResult(editedScene.id, res.fileUri)
                        _feedbackMessage.value = "सीन #${editedScene.sceneNumber} की हिंदी आवाज़ (.wav) तैयार और सेव हो गई।"
                    }
                    is TtsResult.Error -> {
                        _feedbackMessage.value = res.messageHindi
                    }
                }
            } finally {
                _generatingVoiceSceneIds.value = _generatingVoiceSceneIds.value - editedScene.id
            }
        }
    }

    fun generateVideoForScene(editedScene: SceneEntity) {
        val project = activeProject.value ?: return
        val sceneId = editedScene.id
        activeVideoJobs[sceneId]?.cancel()

        activeVideoJobs[sceneId] = viewModelScope.launch {
            // Always persist user's current edits BEFORE reading scene/generating video
            repository.saveScene(editedScene)
            val latestScene = repository.getSceneById(sceneId) ?: editedScene
            runVideoGenerationInternal(project, latestScene)
        }
    }

    fun generateAllScenesSequential() {
        val project = activeProject.value ?: return
        val currentScenes = activeScenes.value.sortedBy { it.sceneNumber }
        if (currentScenes.isEmpty()) {
            _feedbackMessage.value = "जनरेट करने के लिए पहले सीन जोड़ें।"
            return
        }

        viewModelScope.launch {
            val config = configStore.getConfigOnce()
            if (!config.isConfigured) {
                _feedbackMessage.value = "सुरक्षित वीडियो बैकएंड कॉन्फ़िगर नहीं है। पहले 'स्टूडियो सेटिंग्स' में बैकएंड URL और API कुंजी सेव करें।"
                return@launch
            }
            _isGeneratingBatch.value = true
            try {
                var successCount = 0
                for (scene in currentScenes) {
                    val fresh = repository.getSceneById(scene.id) ?: scene
                    if (!fresh.videoUri.isNullOrBlank()) {
                        successCount++
                        continue
                    }
                    val ok = runVideoGenerationInternal(project, fresh)
                    if (ok) {
                        successCount++
                    } else {
                        _feedbackMessage.value = "सीन #${fresh.sceneNumber} पर जनरेशन रुकी — कृपया त्रुटि संदेश देखें।"
                        break
                    }
                }
                if (successCount == currentScenes.size) {
                    _feedbackMessage.value = "सभी $successCount सीन के वीडियो सफलतापूर्वक तैयार हो गए!"
                }
            } finally {
                _isGeneratingBatch.value = false
            }
        }
    }

    private suspend fun runVideoGenerationInternal(
        project: ProjectEntity,
        latestScene: SceneEntity
    ): Boolean {
        val sceneId = latestScene.id
        val projectCharacters = repository.getCharactersForProject(project.id)
        val compiledPrompt = repository.buildConsistentGenerationPrompt(
            project = project,
            scene = latestScene,
            projectCharacters = projectCharacters
        )

        val refUri = latestScene.referenceImageUri ?: run {
            val linkedIds = latestScene.linkedCharacterIdsCsv
                .split(",")
                .mapNotNull { it.trim().toLongOrNull() }
            projectCharacters.firstOrNull { it.id in linkedIds && !it.referenceImageUri.isNullOrBlank() }?.referenceImageUri
        }

        val result = videoGateway.generateSceneVideo(
            sceneId = sceneId,
            compiledPrompt = compiledPrompt,
            style = VideoStyle.fromNameOrDefault(project.styleName),
            aspectRatio = AspectRatioOption.fromNameOrDefault(project.aspectRatioName),
            durationSeconds = latestScene.durationSeconds,
            mode = GenerationMode.fromNameOrDefault(latestScene.generationMode),
            cameraAngle = latestScene.cameraAngle,
            background = latestScene.background,
            hindiDialogue = latestScene.hindiDialogue,
            referenceImageUri = refUri,
            onProgress = { progress ->
                repository.updateSceneJobStatus(
                    sceneId = sceneId,
                    jobStatus = progress.status.name,
                    providerJobId = progress.jobId,
                    statusMessage = progress.message,
                    errorMessage = null
                )
            }
        )

        return when (result) {
            is VideoJobUpdate.Completed -> {
                repository.updateSceneVideoResult(
                    sceneId = sceneId,
                    jobStatus = SceneJobStatus.SUCCEEDED.name,
                    providerJobId = result.jobId,
                    statusMessage = "बैकएंड से वीडियो सफलतापूर्वक तैयार",
                    videoUri = result.localVideoPath,
                    errorMessage = null
                )
                _feedbackMessage.value = "सीन #${latestScene.sceneNumber} का वीडियो तैयार हो गया!"
                true
            }
            is VideoJobUpdate.Failed -> {
                repository.updateSceneJobStatus(
                    sceneId = sceneId,
                    jobStatus = SceneJobStatus.FAILED.name,
                    providerJobId = result.jobId,
                    statusMessage = "जनरेशन विफल",
                    errorMessage = result.errorMessage
                )
                _feedbackMessage.value = result.errorMessage
                false
            }
            is VideoJobUpdate.InProgress -> false
        }
    }

    fun downloadSceneMp4(scene: SceneEntity) {
        val rawUri = scene.videoUri?.takeIf { it.isNotBlank() } ?: run {
            _feedbackMessage.value = "डाउनलोड करने के लिए इस सीन में कोई MP4 वीडियो नहीं है।"
            return
        }
        val projectTitle = activeProject.value?.title ?: "AshishStudio"
        viewModelScope.launch {
            val res = movieAssembler.saveClipToDownloads(
                sourceUri = Uri.parse(rawUri),
                suggestedName = "${projectTitle}_Scene_${scene.sceneNumber}"
            )
            res.fold(
                onSuccess = { (_, path) ->
                    _feedbackMessage.value = "MP4 डाउनलोड पूर्ण: $path"
                },
                onFailure = { ex ->
                    _feedbackMessage.value = "MP4 डाउनलोड विफल: ${ex.localizedMessage ?: "फ़ाइल त्रुटि"}"
                }
            )
        }
    }

    fun exportFullMovie() {
        val project = activeProject.value ?: return
        val readyUris = activeScenes.value
            .sortedBy { it.sceneNumber }
            .mapNotNull { it.videoUri?.takeIf { u -> u.isNotBlank() }?.let(Uri::parse) }

        val totalSeconds = activeScenes.value
            .filter { !it.videoUri.isNullOrBlank() }
            .sumOf { it.durationSeconds }
        if (totalSeconds > StudioPresets.MAX_MOVIE_DURATION_SECONDS) {
            _feedbackMessage.value = "चयनित क्लिप्स की कुल अवधि 30 मिनट से अधिक है।"
            return
        }

        viewModelScope.launch {
            movieAssembler.exportCombinedMovie(project.title, readyUris)
        }
    }

    fun cancelMovieExport() {
        movieAssembler.cancelExport()
    }

    fun resetMovieExportState() {
        movieAssembler.resetState()
    }

    fun refreshRealApkStatus() {
        _realApkInfo.value = zipExporter.findRealBuiltApk()
    }

    fun exportProjectZip(projectId: Long? = _selectedProjectId.value) {
        val targetId = projectId ?: return
        viewModelScope.launch {
            _selectedProjectId.value = targetId
            val project = projects.value.firstOrNull { it.id == targetId } ?: activeProject.value ?: return@launch
            val chars = repository.getCharactersForProject(targetId)
            val scenesList = repository.getScenesForProject(targetId)
            zipExporter.packageProjectZip(project, chars, scenesList)
            refreshRealApkStatus()
        }
    }

    fun downloadPackagedZipToDownloads(packaged: ZipExportState.Packaged) {
        viewModelScope.launch {
            val res = zipExporter.saveZipToDownloads(packaged)
            if (res is ZipExportState.Packaged && !res.savedDisplayPath.isNullOrBlank()) {
                _feedbackMessage.value = "ZIP डाउनलोड पूर्ण: ${res.savedDisplayPath}"
            } else if (res is ZipExportState.Error) {
                _feedbackMessage.value = res.messageHindi
            }
        }
    }

    fun savePackagedZipToCustomUri(packaged: ZipExportState.Packaged, targetUri: Uri) {
        viewModelScope.launch {
            val res = zipExporter.writePackagedZipToUri(packaged, targetUri)
            res.fold(
                onSuccess = { _feedbackMessage.value = "ZIP चयनित फ़ोल्डर में सहेजा गया।" },
                onFailure = { ex ->
                    _feedbackMessage.value = "ZIP सहेजने में त्रुटि: ${ex.localizedMessage ?: "अज्ञात त्रुटि"}"
                }
            )
        }
    }

    fun downloadVerifiedApk(apkInfo: RealApkInfo) {
        viewModelScope.launch {
            if (!apkInfo.apkFile.exists() || apkInfo.apkFile.length() == 0L) {
                _feedbackMessage.value = "सत्यापित APK फ़ाइल उपलब्ध नहीं है।"
                refreshRealApkStatus()
                return@launch
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    apkInfo.apkFile
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(
                Intent.createChooser(intent, "APK साझा/सेव करें")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun resetZipExportState() {
        zipExporter.reset()
    }

    fun saveBackendConfig(
        backendBaseUrl: String,
        apiKey: String,
        videoModelId: String,
        authHeaderName: String,
        usesBearerPrefix: Boolean
    ) {
        val cleanUrl = backendBaseUrl.trim()
        if (cleanUrl.isNotEmpty() && !cleanUrl.startsWith("https://")) {
            _feedbackMessage.value = "सुरक्षा नियम: बैकएंड URL 'https://' से शुरू होना चाहिए।"
            return
        }
        viewModelScope.launch {
            configStore.saveConfig(
                backendBaseUrl = cleanUrl,
                apiKey = apiKey,
                videoModelId = videoModelId,
                authHeaderName = authHeaderName,
                usesBearerPrefix = usesBearerPrefix
            )
            _connectionCheck.value = null
            _feedbackMessage.value = "API कुंजी Android Keystore द्वारा एन्क्रिप्ट कर सुरक्षित सेव की गई।"
        }
    }

    fun clearBackendConfig() {
        viewModelScope.launch {
            configStore.clearConfig()
            _connectionCheck.value = null
            _feedbackMessage.value = "बैकएंड कॉन्फ़िगरेशन हटाया गया।"
        }
    }

    fun testBackendConnection() {
        viewModelScope.launch {
            _isCheckingConnection.value = true
            _connectionCheck.value = videoGateway.testConnection()
            _isCheckingConnection.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceGenerator.release()
    }
}
