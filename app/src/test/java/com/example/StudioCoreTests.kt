package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.PlaceholderAiRepository
import com.example.data.local.CharacterEntity
import com.example.data.local.SceneEntity
import com.example.data.local.StudioDatabase
import com.example.data.local.StudioRepository
import com.example.data.video.BackendMediaResult
import com.example.data.video.SecureBackendConfigStore
import com.example.data.video.VideoGenerationGateway
import com.example.data.video.VideoJobUpdate
import com.example.media.MovieAssembler
import com.example.media.MovieExportState
import com.example.model.AspectRatioOption
import com.example.model.GenerationMode
import com.example.model.SceneJobStatus
import com.example.model.VideoStyle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudioCoreTests {

    private lateinit var context: Context
    private lateinit var db: StudioDatabase
    private lateinit var repository: StudioRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StudioRepository(db.studioDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `placeholder AI scales scene schedule for 30-minute story`() = runTest {
        val ai = PlaceholderAiRepository()
        val result = ai.splitStoryIntoScenes(
            story = "आरव हिमालय की यात्रा पर निकलता है। रास्ते में उसे एक प्राचीन रहस्यमयी नक्शा मिलता है।",
            style = VideoStyle.REALISTIC_CINEMA,
            targetMinutes = 30
        )
        assertTrue(result.isPlaceholderOutput)
        assertTrue(result.scenes.size >= 100)
        val totalSeconds = result.scenes.sumOf { it.durationSeconds }
        assertTrue("Expected >= 1500s for 30-min plan, got $totalSeconds", totalSeconds >= 1500)
        assertTrue(result.scenes.all { it.isPlaceholder })
    }

    @Test
    fun `saveScene updates existing row without replacing id and preserves edits during atomic updates`() = runTest {
        val projectId = repository.createProject(
            title = "टेस्ट सिनेमा",
            storyPrompt = "एक किसान की कहानी",
            style = VideoStyle.CARTOON_3D,
            aspectRatio = AspectRatioOption.RATIO_9_16,
            targetDurationMinutes = 5
        )
        val sceneId = repository.addBlankScene(projectId)
        val original = repository.getSceneById(sceneId)!!

        val edited = original.copy(
            videoPrompt = "अपडेटेड सिनेमैटिक शॉट",
            hindiDialogue = "आज मौसम बहुत सुहावना है।",
            cameraAngle = "Close-Up",
            background = "हरा-भरा खेत"
        )
        val savedId = repository.saveScene(edited)
        assertEquals(sceneId, savedId)

        repository.updateSceneJobStatus(
            sceneId = sceneId,
            jobStatus = SceneJobStatus.RUNNING.name,
            providerJobId = "job_123",
            statusMessage = "रनिंग",
            errorMessage = null
        )
        repository.updateSceneVoiceResult(sceneId, "file:///tmp/voice.wav")
        repository.updateSceneReferenceImage(sceneId, "file:///tmp/ref.jpg")
        repository.updateSceneVideoResult(
            sceneId = sceneId,
            jobStatus = SceneJobStatus.SUCCEEDED.name,
            providerJobId = "job_123",
            statusMessage = "तैयार",
            videoUri = "file:///tmp/out.mp4",
            errorMessage = null
        )

        val reloaded = repository.getSceneById(sceneId)!!
        assertEquals("अपडेटेड सिनेमैटिक शॉट", reloaded.videoPrompt)
        assertEquals("आज मौसम बहुत सुहावना है।", reloaded.hindiDialogue)
        assertEquals("file:///tmp/voice.wav", reloaded.voiceAudioUri)
        assertEquals("file:///tmp/ref.jpg", reloaded.referenceImageUri)
        assertEquals("file:///tmp/out.mp4", reloaded.videoUri)
        assertEquals("job_123", reloaded.providerJobId)
    }

    @Test
    fun `consistent character prompt injects linked characters`() = runTest {
        val projectId = repository.createProject(
            title = "मिशन भारत",
            storyPrompt = "कहानी",
            style = VideoStyle.REALISTIC_CINEMA,
            aspectRatio = AspectRatioOption.RATIO_16_9,
            targetDurationMinutes = 10
        )
        val charId = repository.upsertCharacter(
            CharacterEntity(
                projectId = projectId,
                name = "आरव",
                visualDescription = "28 वर्षीय भारतीय युवक, हल्की दाढ़ी",
                outfit = "नीली जैकेट"
            )
        )
        val project = db.studioDao().getProjectById(projectId)!!
        val scene = SceneEntity(
            projectId = projectId,
            sceneNumber = 1,
            videoPrompt = "आरव स्टेशन पर खड़ा है",
            characterDescription = "गंभीर भाव",
            linkedCharacterIdsCsv = charId.toString(),
            hindiDialogue = "हमें अभी निकलना होगा।",
            cameraAngle = "Medium Shot",
            background = "रेलवे स्टेशन"
        )
        val prompt = repository.buildConsistentGenerationPrompt(
            project = project,
            scene = scene,
            projectCharacters = repository.getCharactersForProject(projectId)
        )
        assertTrue(prompt.contains("आरव"))
        assertTrue(prompt.contains("नीली जैकेट"))
        assertTrue(prompt.contains("16:9"))
    }

    @Test
    fun `video gateway fails honestly when backend is unconfigured`() = runTest {
        val store = SecureBackendConfigStore(context)
        store.clearConfig()
        val gateway = VideoGenerationGateway(context, store)

        val videoRes = gateway.generateSceneVideo(
            sceneId = 1L,
            compiledPrompt = "Test prompt",
            style = VideoStyle.REALISTIC_CINEMA,
            aspectRatio = AspectRatioOption.RATIO_16_9,
            durationSeconds = 8,
            mode = GenerationMode.TEXT_TO_VIDEO,
            cameraAngle = "Wide Shot",
            background = "City",
            hindiDialogue = "नमस्ते",
            referenceImageUri = null,
            onProgress = {}
        )
        assertTrue(videoRes is VideoJobUpdate.Failed)
        assertTrue((videoRes as VideoJobUpdate.Failed).isConfigMissing)

        val imgRes = gateway.generateSceneImage(
            sceneId = 1L,
            compiledPrompt = "Test prompt",
            style = VideoStyle.CARTOON_3D,
            aspectRatio = AspectRatioOption.RATIO_1_1,
            characterDescription = "Hero",
            background = "Forest"
        )
        assertTrue(imgRes is BackendMediaResult.Failed)
        assertTrue((imgRes as BackendMediaResult.Failed).isConfigMissing)
    }

    @Test
    fun `movie assembler rejects empty clip list and saves real mp4 clip`() = runTest {
        val assembler = MovieAssembler(context)
        assembler.exportCombinedMovie("EmptyTest", emptyList())
        assertTrue(assembler.exportState.value is MovieExportState.Error)

        val fakeMp4 = File(context.cacheDir, "sample_clip.mp4").apply {
            writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70))
        }
        val saveRes = assembler.saveClipToDownloads(Uri.fromFile(fakeMp4), "Scene_1")
        assertTrue(saveRes.isSuccess)
        assertNotNull(saveRes.getOrNull()?.first)
    }
}
