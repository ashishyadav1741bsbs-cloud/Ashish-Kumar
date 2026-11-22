package com.example.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

sealed interface MovieExportState {
    data object Idle : MovieExportState
    data class Exporting(val progressPercent: Int, val statusText: String) : MovieExportState
    data class Completed(
        val exportedUri: Uri,
        val displayPath: String,
        val downloadsUri: Uri? = null,
        val downloadsDisplayPath: String? = null
    ) : MovieExportState
    data class Error(val message: String) : MovieExportState
}

/**
 * Uses Jetpack Media3 Transformer + Composition to concatenate multiple scene clips
 * into a single movie MP4 (supporting timelines up to 30 minutes) and export via MediaStore
 * to both Movies/AshishAIVideoStudio and Downloads/AshishAIVideoStudio.
 */
class MovieAssembler(private val context: Context) {

    private val _exportState = MutableStateFlow<MovieExportState>(MovieExportState.Idle)
    val exportState: StateFlow<MovieExportState> = _exportState.asStateFlow()

    private var activeTransformer: Transformer? = null

    @OptIn(UnstableApi::class)
    suspend fun exportCombinedMovie(
        projectTitle: String,
        clipUris: List<Uri>
    ) {
        if (clipUris.isEmpty()) {
            _exportState.value = MovieExportState.Error("एक्सपोर्ट करने के लिए कम से कम 1 तैयार वीडियो क्लिप आवश्यक है।")
            return
        }

        _exportState.value = MovieExportState.Exporting(0, "Media3 कंपोज़िशन तैयार की जा रही है...")

        val exportDir = File(context.cacheDir, "movie_exports").apply { mkdirs() }
        val safeName = projectTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "AshishStudioMovie" }
        val fileNameWithTs = "${safeName}_${System.currentTimeMillis()}.mp4"
        val tempOutputFile = File(exportDir, fileNameWithTs)

        if (clipUris.size == 1) {
            // Fast & lossless copy when exporting a single MP4 clip
            _exportState.value = MovieExportState.Exporting(40, "MP4 वीडियो फ़ाइल तैयार की जा रही है...")
            val copied = copyUriToFile(clipUris.first(), tempOutputFile)
            if (copied) {
                finalizeExportFile(tempOutputFile, fileNameWithTs)
                return
            }
        }

        val editedItems = clipUris.map { uri ->
            EditedMediaItem.Builder(MediaItem.fromUri(uri)).build()
        }
        val videoSequence = EditedMediaItemSequence.Builder(editedItems).build()
        val composition = Composition.Builder(listOf(videoSequence)).build()

        var completedFlag = false
        var exportFailedMessage: String? = null

        withContext(Dispatchers.Main) {
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        completedFlag = true
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        exportFailedMessage = exportException.localizedMessage
                            ?: "Media3 एक्सपोर्ट त्रुटि (कोड: ${exportException.errorCode})"
                        completedFlag = true
                    }
                })
                .build()

            activeTransformer = transformer
            transformer.start(composition, tempOutputFile.absolutePath)

            val holder = ProgressHolder()
            while (!completedFlag) {
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _exportState.value = MovieExportState.Exporting(
                        progressPercent = holder.progress.coerceIn(0, 99),
                        statusText = "सभी सीन जोड़े जा रहे हैं (${holder.progress}%)..."
                    )
                }
                delay(350L)
            }
            activeTransformer = null
        }

        val err = exportFailedMessage
        if (err != null) {
            _exportState.value = MovieExportState.Error(err)
            return
        }

        if (!tempOutputFile.exists() || tempOutputFile.length() == 0L) {
            _exportState.value = MovieExportState.Error("एक्सपोर्ट की गई फ़ाइल खाली है।")
            return
        }

        finalizeExportFile(tempOutputFile, fileNameWithTs)
    }

    private suspend fun finalizeExportFile(tempOutputFile: File, displayName: String) {
        val savedResult = saveMovieToGalleryOrAppMovies(tempOutputFile, displayName)
        val downloadsResult = saveMp4ToDownloads(tempOutputFile, displayName).getOrNull()
        savedResult.fold(
            onSuccess = { (uri, pathLabel) ->
                _exportState.value = MovieExportState.Completed(
                    exportedUri = uri,
                    displayPath = pathLabel,
                    downloadsUri = downloadsResult?.first,
                    downloadsDisplayPath = downloadsResult?.second
                )
            },
            onFailure = { ex ->
                if (downloadsResult != null) {
                    _exportState.value = MovieExportState.Completed(
                        exportedUri = downloadsResult.first,
                        displayPath = downloadsResult.second,
                        downloadsUri = downloadsResult.first,
                        downloadsDisplayPath = downloadsResult.second
                    )
                } else {
                    _exportState.value = MovieExportState.Error(
                        ex.localizedMessage ?: "मूवी सेव करने में त्रुटि हुई।"
                    )
                }
            }
        )
    }

    suspend fun saveClipToDownloads(sourceUri: Uri, suggestedName: String): Result<Pair<Uri, String>> =
        withContext(Dispatchers.IO) {
            try {
                val safeName = suggestedName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "scene_clip" }
                val fileName = "${safeName}_${System.currentTimeMillis()}.mp4"
                val cacheDir = File(context.cacheDir, "clip_downloads").apply { mkdirs() }
                val tempFile = File(cacheDir, fileName)
                if (!copyUriToFile(sourceUri, tempFile) || tempFile.length() == 0L) {
                    return@withContext Result.failure(IllegalStateException("क्लिप फ़ाइल पढ़ी नहीं जा सकी या खाली है।"))
                }
                val savedDownloads = saveMp4ToDownloads(tempFile, fileName)
                saveMovieToGalleryOrAppMovies(tempFile, fileName)
                savedDownloads
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun copyUriToFile(uri: Uri, destFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val stream: InputStream? = if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                val f = File(uri.path!!)
                if (f.exists()) FileInputStream(f) else null
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { input ->
                destFile.outputStream().use { out ->
                    input.copyTo(out)
                }
            } ?: return@withContext false
            destFile.exists() && destFile.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    @OptIn(UnstableApi::class)
    fun cancelExport() {
        activeTransformer?.cancel()
        activeTransformer = null
        _exportState.value = MovieExportState.Idle
    }

    fun resetState() {
        _exportState.value = MovieExportState.Idle
    }

    private suspend fun saveMovieToGalleryOrAppMovies(
        sourceFile: File,
        displayName: String
    ): Result<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mediaStoreResult = runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(
                            MediaStore.Video.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_MOVIES + "/AshishAIVideoStudio"
                        )
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        val outStream = resolver.openOutputStream(uri)
                        if (outStream != null) {
                            outStream.use { out ->
                                sourceFile.inputStream().use { input -> input.copyTo(out) }
                            }
                            values.clear()
                            values.put(MediaStore.Video.Media.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                            return@runCatching uri to "Movies/AshishAIVideoStudio/$displayName"
                        }
                    }
                    null
                }.getOrNull()
                if (mediaStoreResult != null) {
                    return@withContext Result.success(mediaStoreResult)
                }
            }
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?.let { File(it, "AshishAIVideoStudio") }
                ?: File(context.filesDir, "Movies/AshishAIVideoStudio")
            moviesDir.mkdirs()
            val dest = File(moviesDir, displayName)
            sourceFile.copyTo(dest, overwrite = true)
            Result.success(shareableUriForFile(dest) to dest.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveMp4ToDownloads(
        sourceFile: File,
        displayName: String
    ): Result<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mediaStoreResult = runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                        put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                        put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/AshishAIVideoStudio"
                        )
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        val outStream = resolver.openOutputStream(uri)
                        if (outStream != null) {
                            outStream.use { out ->
                                sourceFile.inputStream().use { input -> input.copyTo(out) }
                            }
                            values.clear()
                            values.put(MediaStore.Downloads.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                            return@runCatching uri to "Downloads/AshishAIVideoStudio/$displayName"
                        }
                    }
                    null
                }.getOrNull()
                if (mediaStoreResult != null) {
                    return@withContext Result.success(mediaStoreResult)
                }
            }
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?.let { File(it, "AshishAIVideoStudio") }
                ?: File(context.filesDir, "Downloads/AshishAIVideoStudio")
            dir.mkdirs()
            val dest = File(dir, displayName)
            sourceFile.copyTo(dest, overwrite = true)
            Result.success(shareableUriForFile(dest) to dest.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun shareableUriForFile(file: File): Uri {
        return runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrElse { Uri.fromFile(file) }
    }
}
