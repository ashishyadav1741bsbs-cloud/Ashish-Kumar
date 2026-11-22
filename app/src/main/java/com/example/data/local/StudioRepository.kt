package com.example.data.local

import com.example.model.AspectRatioOption
import com.example.model.StudioPresets
import com.example.model.VideoStyle
import kotlinx.coroutines.flow.Flow

class StudioRepository(private val dao: StudioDao) {

    fun observeProjects(): Flow<List<ProjectEntity>> = dao.observeProjects()

    fun observeProject(projectId: Long): Flow<ProjectEntity?> = dao.observeProject(projectId)

    fun observeCharacters(projectId: Long): Flow<List<CharacterEntity>> =
        dao.observeCharacters(projectId)

    fun observeScenes(projectId: Long): Flow<List<SceneEntity>> = dao.observeScenes(projectId)

    suspend fun createProject(
        title: String,
        storyPrompt: String,
        style: VideoStyle,
        aspectRatio: AspectRatioOption,
        targetDurationMinutes: Int
    ): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertProject(
            ProjectEntity(
                title = title.trim().ifBlank { "नया सिनेमा प्रोजेक्ट" },
                storyPrompt = storyPrompt.trim(),
                styleName = style.name,
                aspectRatioName = aspectRatio.name,
                targetDurationMinutes = targetDurationMinutes.coerceIn(1, 30),
                createdAt = now,
                updatedAt = now
            )
        )
        return id
    }

    suspend fun updateProject(project: ProjectEntity) {
        dao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(projectId: Long) {
        dao.deleteProjectById(projectId)
    }

    suspend fun upsertCharacter(character: CharacterEntity): Long {
        return if (character.id > 0L) {
            dao.updateCharacter(character)
            character.id
        } else {
            dao.insertCharacter(character)
        }
    }

    suspend fun deleteCharacter(characterId: Long) {
        dao.deleteCharacterById(characterId)
    }

    suspend fun saveScene(scene: SceneEntity): Long {
        val updated = scene.copy(updatedAt = System.currentTimeMillis())
        return if (updated.id > 0L) {
            dao.updateScene(updated)
            updated.id
        } else {
            dao.insertScene(updated)
        }
    }

    suspend fun updateSceneJobStatus(
        sceneId: Long,
        jobStatus: String,
        providerJobId: String?,
        statusMessage: String,
        errorMessage: String?
    ) {
        dao.updateSceneJobStatus(
            sceneId = sceneId,
            jobStatus = jobStatus,
            providerJobId = providerJobId,
            statusMessage = statusMessage,
            errorMessage = errorMessage,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun updateSceneVideoResult(
        sceneId: Long,
        jobStatus: String,
        providerJobId: String?,
        statusMessage: String,
        videoUri: String,
        errorMessage: String?
    ) {
        dao.updateSceneVideoResult(
            sceneId = sceneId,
            jobStatus = jobStatus,
            providerJobId = providerJobId,
            statusMessage = statusMessage,
            videoUri = videoUri,
            errorMessage = errorMessage,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun updateSceneVoiceResult(sceneId: Long, voiceAudioUri: String) {
        dao.updateSceneVoiceResult(
            sceneId = sceneId,
            voiceAudioUri = voiceAudioUri,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun updateSceneReferenceImage(sceneId: Long, referenceImageUri: String) {
        dao.updateSceneReferenceImage(
            sceneId = sceneId,
            referenceImageUri = referenceImageUri,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun replaceScenes(projectId: Long, scenes: List<SceneEntity>) {
        dao.replaceProjectScenes(projectId, scenes)
        dao.getProjectById(projectId)?.let { project ->
            dao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteSceneAndRenumber(projectId: Long, sceneId: Long) {
        dao.deleteSceneById(sceneId)
        val remaining = dao.getScenesOnce(projectId).sortedBy { it.sceneNumber }
        remaining.forEachIndexed { idx, scene ->
            val expectedNum = idx + 1
            if (scene.sceneNumber != expectedNum) {
                dao.updateScene(scene.copy(sceneNumber = expectedNum))
            }
        }
    }

    suspend fun moveScene(projectId: Long, sceneId: Long, delta: Int) {
        val ordered = dao.getScenesOnce(projectId).sortedBy { it.sceneNumber }.toMutableList()
        val index = ordered.indexOfFirst { it.id == sceneId }
        if (index == -1) return
        val targetIndex = (index + delta).coerceIn(0, ordered.lastIndex)
        if (targetIndex == index) return
        val item = ordered.removeAt(index)
        ordered.add(targetIndex, item)
        ordered.forEachIndexed { idx, scene ->
            dao.updateScene(scene.copy(sceneNumber = idx + 1, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun addBlankScene(projectId: Long): Long {
        val current = dao.getScenesOnce(projectId)
        val nextNum = (current.maxOfOrNull { it.sceneNumber } ?: 0) + 1
        val totalDuration = current.sumOf { it.durationSeconds }
        val remainingBudget = (StudioPresets.MAX_MOVIE_DURATION_SECONDS - totalDuration).coerceAtLeast(4)
        val defaultDuration = 8.coerceAtMost(remainingBudget)
        return dao.insertScene(
            SceneEntity(
                projectId = projectId,
                sceneNumber = nextNum,
                videoPrompt = "",
                characterDescription = "",
                hindiDialogue = "",
                cameraAngle = StudioPresets.cameraAngles.first(),
                background = "",
                durationSeconds = defaultDuration
            )
        )
    }

    suspend fun getSceneById(sceneId: Long): SceneEntity? = dao.getSceneById(sceneId)

    suspend fun getCharactersForProject(projectId: Long): List<CharacterEntity> =
        dao.getCharactersOnce(projectId)

    suspend fun getScenesForProject(projectId: Long): List<SceneEntity> =
        dao.getScenesOnce(projectId)

    /**
     * Combines scene prompt + linked recurring characters from the Character Bible + style directive
     * to maintain character consistency across scenes.
     */
    fun buildConsistentGenerationPrompt(
        project: ProjectEntity,
        scene: SceneEntity,
        projectCharacters: List<CharacterEntity>
    ): String {
        val style = VideoStyle.fromNameOrDefault(project.styleName)
        val aspect = AspectRatioOption.fromNameOrDefault(project.aspectRatioName)
        val linkedIds = scene.linkedCharacterIdsCsv
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
        val matchedCharacters = projectCharacters.filter { it.id in linkedIds }

        val characterBlock = buildString {
            if (matchedCharacters.isNotEmpty()) {
                append("CONSISTENT CHARACTERS: ")
                matchedCharacters.forEach { char ->
                    append("[${char.name}: ${char.visualDescription}; Outfit: ${char.outfit}] ")
                }
            }
            if (scene.characterDescription.isNotBlank()) {
                append("Scene Character Action: ${scene.characterDescription}. ")
            }
        }.trim()

        return buildString {
            append("Style: ${style.promptDirective}. ")
            append("Aspect Ratio: ${aspect.code}. ")
            if (characterBlock.isNotBlank()) append("$characterBlock ")
            if (scene.background.isNotBlank()) append("Environment/Background: ${scene.background}. ")
            if (scene.cameraAngle.isNotBlank()) append("Camera: ${scene.cameraAngle}. ")
            append("Action & Visuals: ${scene.videoPrompt}. ")
            if (scene.hindiDialogue.isNotBlank()) {
                append("Spoken Hindi Dialogue context: \"${scene.hindiDialogue}\".")
            }
        }.trim()
    }
}
