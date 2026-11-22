package com.example.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.local.CharacterEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.SceneEntity
import com.example.model.AspectRatioOption
import com.example.model.VideoStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed interface ZipExportState {
    data object Idle : ZipExportState
    data class Packaging(val progressPercent: Int, val statusHindi: String) : ZipExportState
    data class Packaged(
        val zipFile: File,
        val fileName: String,
        val fileCount: Int,
        val sizeBytes: Long,
        val savedDownloadUri: Uri? = null,
        val savedDisplayPath: String? = null
    ) : ZipExportState
    data class Error(val messageHindi: String) : ZipExportState
}

data class RealApkInfo(
    val apkFile: File,
    val fileName: String,
    val sizeBytes: Long
)

/**
 * Packages the active studio project (metadata, storyboard, characters, all real scene MP4s,
 * Hindi TTS WAV audio files, reference images, README.md, and an Android Chrome-compatible
 * HTML/CSS/JS/React web studio bundle using JSZip + Blob) into a real ZIP archive.
 */
class ProjectZipExporter(private val context: Context) {

    private val _state = MutableStateFlow<ZipExportState>(ZipExportState.Idle)
    val state: StateFlow<ZipExportState> = _state.asStateFlow()

    fun reset() {
        _state.value = ZipExportState.Idle
    }

    /**
     * Checks whether a real, non-empty Android APK binary with valid ZIP/APK header ('PK')
     * actually exists in app build output storage. Never fabricates fake/empty APKs.
     */
    fun findRealBuiltApk(): RealApkInfo? {
        val candidates = listOfNotNull(
            File(context.filesDir, "builds/ashish-ai-video-studio.apk"),
            File(context.filesDir, "builds/app-release.apk"),
            File(context.filesDir, "builds/app-debug.apk"),
            context.getExternalFilesDir("builds")?.let { File(it, "ashish-ai-video-studio.apk") }
        )
        for (file in candidates) {
            if (file.exists() && file.isFile && file.length() > 1024L && hasValidPkHeader(file)) {
                return RealApkInfo(
                    apkFile = file,
                    fileName = file.name,
                    sizeBytes = file.length()
                )
            }
        }
        return null
    }

    private fun hasValidPkHeader(file: File): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(2)
                fis.read(header) == 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun packageProjectZip(
        project: ProjectEntity,
        characters: List<CharacterEntity>,
        scenes: List<SceneEntity>
    ): ZipExportState = withContext(Dispatchers.IO) {
        try {
            _state.value = ZipExportState.Packaging(5, "प्रोजेक्ट डेटा और एसेट्स तैयार किए जा रहे हैं...")

            val exportDir = File(context.cacheDir, "project_zips").apply { mkdirs() }
            val safeSlug = project.title
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "ashish_ai_video_project" }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFileName = "${safeSlug}_$timestamp.zip"
            val outFile = File(exportDir, zipFileName)

            val orderedScenes = scenes.sortedBy { it.sceneNumber }
            val style = VideoStyle.fromNameOrDefault(project.styleName)
            val aspect = AspectRatioOption.fromNameOrDefault(project.aspectRatioName)

            val entriesToWrite = mutableListOf<Pair<String, ByteArray>>()
            val projectJson = buildProjectJson(project, style, aspect, characters, orderedScenes)
            entriesToWrite += "README.md" to buildReadmeMd(project, style, aspect, orderedScenes).toByteArray(Charsets.UTF_8)
            entriesToWrite += "project.json" to projectJson.toString(2).toByteArray(Charsets.UTF_8)
            entriesToWrite += "storyboard.md" to buildStoryboardMd(project, style, aspect, characters, orderedScenes).toByteArray(Charsets.UTF_8)

            // Include complete Android Chrome-compatible HTML/CSS/JS/React web project & JSZip exporter
            entriesToWrite += "web/package.json" to buildWebPackageJson(safeSlug).toByteArray(Charsets.UTF_8)
            entriesToWrite += "web/vite.config.js" to buildViteConfigJs().toByteArray(Charsets.UTF_8)
            entriesToWrite += "web/index.html" to buildWebIndexHtml(project.title).toByteArray(Charsets.UTF_8)
            entriesToWrite += "web/styles.css" to buildWebStylesCss().toByteArray(Charsets.UTF_8)
            entriesToWrite += "web/app.js" to buildWebAppJs(projectJson).toByteArray(Charsets.UTF_8)
            entriesToWrite += "web/src/App.jsx" to buildReactAppJsx(projectJson).toByteArray(Charsets.UTF_8)

            // Collect real media files (reference images, Hindi WAV audio, MP4 video clips)
            val mediaFiles = mutableListOf<Pair<String, Uri>>()
            characters.forEachIndexed { idx, ch ->
                ch.referenceImageUri?.takeIf { it.isNotBlank() }?.let { uriStr ->
                    mediaFiles += "assets/character_${idx + 1}_${ch.id}.jpg" to Uri.parse(uriStr)
                }
            }
            orderedScenes.forEach { sc ->
                sc.referenceImageUri?.takeIf { it.isNotBlank() }?.let { uriStr ->
                    mediaFiles += "assets/scene_${sc.sceneNumber}_ref.jpg" to Uri.parse(uriStr)
                }
                sc.voiceAudioUri?.takeIf { it.isNotBlank() }?.let { uriStr ->
                    mediaFiles += "audio/scene_${sc.sceneNumber}_hindi.wav" to Uri.parse(uriStr)
                }
                sc.videoUri?.takeIf { it.isNotBlank() }?.let { uriStr ->
                    mediaFiles += "clips/scene_${sc.sceneNumber}.mp4" to Uri.parse(uriStr)
                }
            }

            val totalSteps = entriesToWrite.size + mediaFiles.size
            var completedSteps = 0
            var totalFilesWritten = 0

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
                for ((path, bytes) in entriesToWrite) {
                    zos.putNextEntry(ZipEntry(path))
                    zos.write(bytes)
                    zos.closeEntry()
                    completedSteps++
                    totalFilesWritten++
                    val pct = (10 + (completedSteps * 85) / totalSteps.coerceAtLeast(1)).coerceIn(10, 95)
                    _state.value = ZipExportState.Packaging(pct, "पैकेजिंग: $path ($pct%)")
                }

                for ((entryPath, uri) in mediaFiles) {
                    val copied = writeUriToZip(zos, entryPath, uri)
                    if (copied) totalFilesWritten++
                    completedSteps++
                    val pct = (10 + (completedSteps * 85) / totalSteps.coerceAtLeast(1)).coerceIn(10, 95)
                    _state.value = ZipExportState.Packaging(pct, "मीडिया जोड़ा जा रहा है: $entryPath ($pct%)")
                }
            }

            if (!outFile.exists() || outFile.length() == 0L) {
                val err = ZipExportState.Error("ZIP फ़ाइल बनाने में त्रुटि: आउटपुट फ़ाइल खाली रही।")
                _state.value = err
                return@withContext err
            }

            val result = ZipExportState.Packaged(
                zipFile = outFile,
                fileName = zipFileName,
                fileCount = totalFilesWritten,
                sizeBytes = outFile.length()
            )
            _state.value = result
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val err = ZipExportState.Error("ZIP एक्सपोर्ट विफल: ${e.localizedMessage ?: e.javaClass.simpleName}")
            _state.value = err
            err
        }
    }

    suspend fun saveZipToDownloads(packaged: ZipExportState.Packaged): ZipExportState =
        withContext(Dispatchers.IO) {
            try {
                if (!packaged.zipFile.exists() || packaged.zipFile.length() == 0L) {
                    val err = ZipExportState.Error("ZIP फ़ाइल उपलब्ध नहीं है। कृपया पहले 'प्रोजेक्ट एक्सपोर्ट करें' दबाएँ।")
                    _state.value = err
                    return@withContext err
                }

                val (uri, displayPath) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, packaged.fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                        put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/AshishAIVideoStudio"
                        )
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Downloads फोल्डर में प्रविष्टि बनाने में विफल")
                    resolver.openOutputStream(uri)?.use { out ->
                        BufferedInputStream(FileInputStream(packaged.zipFile)).use { input ->
                            input.copyTo(out)
                        }
                    } ?: error("आउटपुट स्ट्रीम नहीं खुल सकी")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri to "Downloads/AshishAIVideoStudio/${packaged.fileName}"
                } else {
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: File(context.filesDir, "Downloads").apply { mkdirs() }
                    val dest = File(dir, packaged.fileName)
                    packaged.zipFile.copyTo(dest, overwrite = true)
                    Uri.fromFile(dest) to dest.absolutePath
                }

                val updated = packaged.copy(
                    savedDownloadUri = uri,
                    savedDisplayPath = displayPath
                )
                _state.value = updated
                updated
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val err = ZipExportState.Error("डाउनलोड सहेजने में त्रुटि: ${e.localizedMessage ?: e.javaClass.simpleName}")
                _state.value = err
                err
            }
        }

    suspend fun writePackagedZipToUri(packaged: ZipExportState.Packaged, targetUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    BufferedInputStream(FileInputStream(packaged.zipFile)).use { input ->
                        input.copyTo(out)
                    }
                } ?: error("चयनित स्थान पर लिखने में विफल")
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun writeUriToZip(zos: ZipOutputStream, entryPath: String, uri: Uri): Boolean {
        return try {
            val stream = if (uri.scheme == "file") {
                val f = File(uri.path ?: return false)
                if (!f.exists() || !f.isFile) return false
                FileInputStream(f)
            } else {
                context.contentResolver.openInputStream(uri) ?: return false
            }
            stream.use { input ->
                zos.putNextEntry(ZipEntry(entryPath))
                input.copyTo(zos)
                zos.closeEntry()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildProjectJson(
        project: ProjectEntity,
        style: VideoStyle,
        aspect: AspectRatioOption,
        characters: List<CharacterEntity>,
        scenes: List<SceneEntity>
    ): JSONObject {
        val charArray = JSONArray()
        characters.forEach { ch ->
            charArray.put(
                JSONObject().apply {
                    put("id", ch.id)
                    put("name", ch.name)
                    put("visualDescription", ch.visualDescription)
                    put("outfit", ch.outfit)
                    put("voiceStyle", ch.voiceStyle)
                }
            )
        }

        val sceneArray = JSONArray()
        scenes.forEach { sc ->
            sceneArray.put(
                JSONObject().apply {
                    put("sceneNumber", sc.sceneNumber)
                    put("videoPrompt", sc.videoPrompt)
                    put("characterDescription", sc.characterDescription)
                    put("hindiDialogue", sc.hindiDialogue)
                    put("cameraAngle", sc.cameraAngle)
                    put("background", sc.background)
                    put("durationSeconds", sc.durationSeconds)
                    put("generationMode", sc.generationMode)
                    put("jobStatus", sc.jobStatus)
                    put("hasVideoClip", !sc.videoUri.isNullOrBlank())
                    put("hasHindiVoiceWav", !sc.voiceAudioUri.isNullOrBlank())
                }
            )
        }

        return JSONObject().apply {
            put("studio", "Ashish AI Video Studio")
            put("projectId", project.id)
            put("title", project.title)
            put("storyPrompt", project.storyPrompt)
            put("style", style.englishLabel)
            put("styleHindi", style.hindiLabel)
            put("aspectRatio", aspect.code)
            put("targetDurationMinutes", project.targetDurationMinutes)
            put("characters", charArray)
            put("scenes", sceneArray)
        }
    }

    private fun buildStoryboardMd(
        project: ProjectEntity,
        style: VideoStyle,
        aspect: AspectRatioOption,
        characters: List<CharacterEntity>,
        scenes: List<SceneEntity>
    ): String = buildString {
        appendLine("# ${project.title}")
        appendLine()
        appendLine("- **Style:** ${style.englishLabel} (${style.hindiLabel})")
        appendLine("- **Aspect Ratio:** ${aspect.code}")
        appendLine("- **Target Duration:** ${project.targetDurationMinutes} minutes")
        appendLine()
        appendLine("## Story")
        appendLine(project.storyPrompt.ifBlank { "_No story text_" })
        appendLine()
        if (characters.isNotEmpty()) {
            appendLine("## Character Consistency Bible")
            characters.forEach { ch ->
                appendLine("- **${ch.name}**: ${ch.visualDescription} | Outfit: ${ch.outfit}")
            }
            appendLine()
        }
        appendLine("## Scenes (${scenes.size})")
        scenes.forEach { sc ->
            appendLine("### Scene ${sc.sceneNumber} (${sc.durationSeconds}s)")
            appendLine("- **Prompt:** ${sc.videoPrompt}")
            appendLine("- **Character:** ${sc.characterDescription}")
            appendLine("- **Hindi Dialogue:** ${sc.hindiDialogue}")
            appendLine("- **Camera:** ${sc.cameraAngle}")
            appendLine("- **Background:** ${sc.background}")
            appendLine()
        }
    }

    private fun buildReadmeMd(
        project: ProjectEntity,
        style: VideoStyle,
        aspect: AspectRatioOption,
        scenes: List<SceneEntity>
    ): String = """
# Ashish AI Video Studio — Exported Project Bundle

**Project:** ${project.title}  
**Style:** ${style.englishLabel}  
**Aspect Ratio:** ${aspect.code}  
**Scenes:** ${scenes.size}  

## Contents
- `project.json` — Complete project, characters, and scene-by-scene metadata
- `storyboard.md` — Human-readable script & camera breakdown
- `clips/` — Generated or attached `.mp4` scene clips (if present)
- `audio/` — Synthesized Hindi dialogue `.wav` tracks (if present)
- `assets/` — Character and scene reference images (if present)
- `web/` — Standalone Android Chrome & Desktop Web Viewer/Exporter (HTML, CSS, JS, React JSX, Vite config, using JSZip + `Blob` + `URL.createObjectURL`)

## Installation Instructions (Web Bundle)
1. Install Node.js 18+ from https://nodejs.org
2. Open a terminal inside the `web/` folder:
   ```bash
   cd web
   npm install
   npm run dev
   ```
3. Or open `web/index.html` directly over a local HTTP server (works on Android Chrome & Desktop):
   ```bash
   npx serve web
   ```
4. To build for production:
   ```bash
   npm run build
   ```

## Android APK Note
This bundle was exported from the native **Ashish AI Video Studio** Android app. To build a signed Android APK from the full Android Studio workspace, use Android Studio (`Build > Build APK(s)`) or AI Studio's **Generate APK** menu.
""".trimIndent()

    private fun buildWebPackageJson(slug: String): String = """
{
  "name": "$slug-web-studio",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "jszip": "^3.10.1",
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^4.3.1",
    "vite": "^5.4.0"
  }
}
""".trimIndent()

    private fun buildViteConfigJs(): String = """
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
});
""".trimIndent()

    private fun buildWebIndexHtml(title: String): String = """
<!doctype html>
<html lang="hi">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${escapeHtml(title)} — Ashish AI Video Studio</title>
  <link rel="stylesheet" href="./styles.css" />
  <script src="https://cdn.jsdelivr.net/npm/jszip@3.10.1/dist/jszip.min.js" defer></script>
  <script src="./app.js" defer></script>
</head>
<body>
  <main class="studio-shell">
    <header class="hero">
      <span class="badge">ASHISH AI VIDEO STUDIO</span>
      <h1>${escapeHtml(title)}</h1>
      <p id="meta-line" class="muted"></p>
      <div class="actions">
        <button id="download-zip-btn" class="btn-primary">Download Web ZIP (JSZip)</button>
      </div>
      <p id="status-msg" class="status"></p>
    </header>
    <section id="scenes-grid" class="grid"></section>
  </main>
</body>
</html>
""".trimIndent()

    private fun buildWebStylesCss(): String = """
:root {
  color-scheme: dark;
  --bg: #090c12;
  --surface: #111622;
  --border: #2b3650;
  --amber: #ffb020;
  --cyan: #22d3ee;
  --text: #f3f6fc;
  --muted: #a7b3c9;
}
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: system-ui, -apple-system, 'Noto Sans Devanagari', sans-serif;
  background: var(--bg);
  color: var(--text);
}
.studio-shell { max-width: 960px; margin: 0 auto; padding: 20px; }
.hero {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 18px;
  padding: 20px;
  margin-bottom: 20px;
}
.badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255,176,32,0.16);
  color: var(--amber);
  font-size: 12px;
  font-weight: 700;
}
.btn-primary {
  background: var(--amber);
  color: #1a1100;
  border: none;
  border-radius: 10px;
  padding: 12px 18px;
  font-weight: 700;
  cursor: pointer;
}
.muted { color: var(--muted); }
.status { color: var(--cyan); font-size: 14px; }
.grid { display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 14px;
}
""".trimIndent()

    private fun buildWebAppJs(projectJson: JSONObject): String {
        val jsonLiteral = projectJson.toString(2)
        return """
const PROJECT_DATA = $jsonLiteral;

document.addEventListener('DOMContentLoaded', () => {
  const metaEl = document.getElementById('meta-line');
  const gridEl = document.getElementById('scenes-grid');
  const statusEl = document.getElementById('status-msg');
  const btn = document.getElementById('download-zip-btn');

  metaEl.textContent = `${'$'}{PROJECT_DATA.style} • ${'$'}{PROJECT_DATA.aspectRatio} • ${'$'}{PROJECT_DATA.scenes.length} Scenes`;

  PROJECT_DATA.scenes.forEach((sc) => {
    const card = document.createElement('article');
    card.className = 'card';
    card.innerHTML = `
      <strong>Scene #${'$'}{sc.sceneNumber} (${'$'}{sc.durationSeconds}s)</strong>
      <p>${'$'}{sc.videoPrompt || ''}</p>
      <p class="muted">Dialogue: ${'$'}{sc.hindiDialogue || '-'}</p>
      <p class="muted">Camera: ${'$'}{sc.cameraAngle || '-'}</p>
    `;
    gridEl.appendChild(card);
  });

  btn.addEventListener('click', async () => {
    if (typeof JSZip === 'undefined') {
      statusEl.textContent = 'Error: JSZip library failed to load.';
      return;
    }
    try {
      statusEl.textContent = 'Creating ZIP...';
      const zip = new JSZip();
      zip.file('project.json', JSON.stringify(PROJECT_DATA, null, 2));
      const blob = await zip.generateAsync({ type: 'blob' }, (meta) => {
        statusEl.textContent = `Packaging ZIP: ${'$'}{Math.round(meta.percent)}%`;
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${'$'}{(PROJECT_DATA.title || 'project').replace(/\s+/g, '_')}.zip`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      statusEl.textContent = 'ZIP download started successfully.';
    } catch (err) {
      statusEl.textContent = `Download error: ${'$'}{err.message}`;
    }
  });
});
""".trimIndent()
    }

    private fun buildReactAppJsx(projectJson: JSONObject): String {
        val jsonLiteral = projectJson.toString(2)
        return """
import React, { useState } from 'react';
import JSZip from 'jszip';

const INITIAL_PROJECT = $jsonLiteral;

export default function App() {
  const [status, setStatus] = useState('');
  const [progress, setProgress] = useState(0);

  const handleDownloadZip = async () => {
    try {
      setStatus('Generating project ZIP...');
      const zip = new JSZip();
      zip.file('project.json', JSON.stringify(INITIAL_PROJECT, null, 2));
      const blob = await zip.generateAsync({ type: 'blob' }, (m) => {
        setProgress(Math.round(m.percent));
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${'$'}{(INITIAL_PROJECT.title || 'ashish_project').replace(/\s+/g, '_')}.zip`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      setStatus('ZIP downloaded successfully.');
    } catch (e) {
      setStatus(`Export failed: ${'$'}{e.message}`);
    }
  };

  return (
    <div className="studio-shell">
      <header className="hero">
        <span className="badge">ASHISH AI VIDEO STUDIO</span>
        <h1>{INITIAL_PROJECT.title}</h1>
        <p className="muted">{INITIAL_PROJECT.style} • {INITIAL_PROJECT.aspectRatio}</p>
        <button className="btn-primary" onClick={handleDownloadZip}>
          Download ZIP ({progress}%)
        </button>
        {status && <p className="status">{status}</p>}
      </header>
      <section className="grid">
        {INITIAL_PROJECT.scenes.map((sc) => (
          <article className="card" key={sc.sceneNumber}>
            <strong>Scene #{sc.sceneNumber} ({sc.durationSeconds}s)</strong>
            <p>{sc.videoPrompt}</p>
            <p className="muted">Dialogue: {sc.hindiDialogue}</p>
          </article>
        ))}
      </section>
    </div>
  );
}
""".trimIndent()
    }

    private fun escapeHtml(raw: String): String =
        raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
