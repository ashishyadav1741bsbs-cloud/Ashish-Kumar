package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CharacterEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.SceneEntity
import com.example.media.ProjectZipExporter
import com.example.media.ZipExportState
import com.example.model.AspectRatioOption
import com.example.model.VideoStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProjectZipExporterTest {

    @Test
    fun `packageProjectZip creates valid zip with readme, web bundle, and media assets`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exporter = ProjectZipExporter(context)

        val dummyClip = File(context.cacheDir, "test_scene_1.mp4").apply {
            writeBytes(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112))
        }

        val project = ProjectEntity(
            id = 42L,
            title = "Himalaya Mystery",
            storyPrompt = "एक रहस्यमयी यात्रा।",
            styleName = VideoStyle.REALISTIC_CINEMA.name,
            aspectRatioName = AspectRatioOption.RATIO_16_9.name,
            targetDurationMinutes = 5
        )
        val characters = listOf(
            CharacterEntity(
                id = 1L,
                projectId = 42L,
                name = "आरव",
                visualDescription = "युवा यात्री",
                outfit = "नीली जैकेट"
            )
        )
        val scenes = listOf(
            SceneEntity(
                id = 10L,
                projectId = 42L,
                sceneNumber = 1,
                videoPrompt = "Snowy mountain drone shot",
                characterDescription = "आरव",
                hindiDialogue = "यहाँ बहुत ठंड है।",
                cameraAngle = "Aerial Drone",
                background = "Himalayas",
                durationSeconds = 8,
                videoUri = Uri.fromFile(dummyClip).toString()
            )
        )

        val state = exporter.packageProjectZip(project, characters, scenes)
        assertTrue(state is ZipExportState.Packaged)
        val packaged = state as ZipExportState.Packaged
        assertTrue(packaged.zipFile.exists())
        assertTrue(packaged.sizeBytes > 0L)

        ZipFile(packaged.zipFile).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue("README.md" in names)
            assertTrue("project.json" in names)
            assertTrue("storyboard.md" in names)
            assertTrue("web/index.html" in names)
            assertTrue("web/styles.css" in names)
            assertTrue("web/app.js" in names)
            assertTrue("web/src/App.jsx" in names)
            assertTrue("web/package.json" in names)
            assertTrue("web/vite.config.js" in names)
            assertTrue("clips/scene_1.mp4" in names)
        }
    }

    @Test
    fun `findRealBuiltApk returns null when missing or fake empty file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exporter = ProjectZipExporter(context)
        val buildsDir = File(context.filesDir, "builds").apply { mkdirs() }
        val fakeApk = File(buildsDir, "ashish-ai-video-studio.apk")
        fakeApk.delete()

        assertNull(exporter.findRealBuiltApk())

        // Empty or non-PK fake file must still be rejected
        fakeApk.writeText("not-an-apk")
        assertNull(exporter.findRealBuiltApk())

        // Valid PK header + non-trivial payload is accepted
        val validBytes = ByteArray(2048).apply {
            this[0] = 'P'.code.toByte()
            this[1] = 'K'.code.toByte()
        }
        fakeApk.writeBytes(validBytes)
        val found = exporter.findRealBuiltApk()
        assertNotNull(found)
        assertEquals("ashish-ai-video-studio.apk", found?.fileName)
        fakeApk.delete()
    }
}
