package com.example.data.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.model.AspectRatioOption
import com.example.model.GenerationMode
import com.example.model.SceneJobStatus
import com.example.model.VideoStyle
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed interface VideoJobUpdate {
    data class InProgress(val status: SceneJobStatus, val jobId: String?, val message: String) : VideoJobUpdate
    data class Completed(val localVideoPath: String, val jobId: String?) : VideoJobUpdate
    data class Failed(
        val errorMessage: String,
        val jobId: String? = null,
        val isConfigMissing: Boolean = false
    ) : VideoJobUpdate
}

sealed interface BackendMediaResult {
    data class Success(val localUri: String) : BackendMediaResult
    data class Failed(
        val errorMessage: String,
        val isConfigMissing: Boolean = false,
        val endpointUnsupported: Boolean = false
    ) : BackendMediaResult
}

data class ConnectionCheckResult(
    val success: Boolean,
    val statusCode: Int?,
    val detailHindi: String
)

/**
 * Communicates strictly with the owner-configured Secure Video Generation Backend.
 * Never fabricates fake videos or masks provider/quota errors.
 */
class VideoGenerationGateway(
    private val context: Context,
    private val configStore: SecureBackendConfigStore
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun buildClient(config: OwnerBackendConfig): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val headerVal = if (config.usesBearerPrefix) {
                    "Bearer ${config.apiKey}"
                } else {
                    config.apiKey
                }
                val req = chain.request().newBuilder()
                    .header(config.authHeaderName, headerVal)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    private fun buildApi(config: OwnerBackendConfig): VideoBackendApi {
        val baseUrl = config.backendBaseUrl.trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(buildClient(config))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(VideoBackendApi::class.java)
    }

    private fun resolveMediaUrl(rawUrl: String, config: OwnerBackendConfig): String {
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed
        }
        val base = config.backendBaseUrl.trimEnd('/')
        val suffix = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return "$base$suffix"
    }

    suspend fun testConnection(): ConnectionCheckResult = withContext(Dispatchers.IO) {
        val config = configStore.getConfigOnce()
        if (config.backendBaseUrl.isBlank()) {
            return@withContext ConnectionCheckResult(
                success = false,
                statusCode = null,
                detailHindi = "बैकएंड URL खाली है। कृपया पहले सुरक्षित वीडियो बैकएंड URL दर्ज करें।"
            )
        }
        if (!config.backendBaseUrl.startsWith("https://")) {
            return@withContext ConnectionCheckResult(
                success = false,
                statusCode = null,
                detailHindi = "सुरक्षा नियम: बैकएंड URL केवल HTTPS (https://...) होना चाहिए।"
            )
        }
        if (config.apiKey.isBlank()) {
            return@withContext ConnectionCheckResult(
                success = false,
                statusCode = null,
                detailHindi = "API कुंजी खाली है। कृपया मालिक की API कुंजी दर्ज करें।"
            )
        }

        try {
            val api = buildApi(config)
            val response = api.checkHealth()
            if (response.isSuccessful) {
                val body = response.body()
                ConnectionCheckResult(
                    success = true,
                    statusCode = response.code(),
                    detailHindi = "कनेक्शन सफल (HTTP ${response.code()}) — प्रदाता: ${body?.provider ?: "Ready"} ${body?.message.orEmpty()}"
                )
            } else {
                val errBody = response.errorBody()?.string().orEmpty().take(300)
                ConnectionCheckResult(
                    success = false,
                    statusCode = response.code(),
                    detailHindi = "बैकएंड से त्रुटि मिली: HTTP ${response.code()} — $errBody"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ConnectionCheckResult(
                success = false,
                statusCode = null,
                detailHindi = "कनेक्शन विफल: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }

    suspend fun generateSceneImage(
        sceneId: Long,
        compiledPrompt: String,
        style: VideoStyle,
        aspectRatio: AspectRatioOption,
        characterDescription: String,
        background: String
    ): BackendMediaResult = withContext(Dispatchers.IO) {
        val config = configStore.getConfigOnce()
        if (!config.isConfigured) {
            return@withContext BackendMediaResult.Failed(
                errorMessage = "सीन फ़ोटो बनाने के लिए 'स्टूडियो सेटिंग्स' में सुरक्षित HTTPS बैकएंड कॉन्फ़िगर करें या Firebase AI Logic सक्षम करें।",
                isConfigMissing = true
            )
        }
        if (!config.backendBaseUrl.startsWith("https://")) {
            return@withContext BackendMediaResult.Failed(
                errorMessage = "असुरक्षित URL: केवल HTTPS बैकएंड एंडपॉइंट समर्थित हैं।",
                isConfigMissing = true
            )
        }
        if (compiledPrompt.isBlank()) {
            return@withContext BackendMediaResult.Failed("फ़ोटो बनाने के लिए वीडियो प्रॉम्प्ट खाली है।")
        }

        try {
            val api = buildApi(config)
            val resp = api.generateSceneImage(
                ImageGenerationRequest(
                    prompt = compiledPrompt,
                    style = style.name,
                    aspectRatio = aspectRatio.code,
                    characterDescription = characterDescription.ifBlank { null },
                    background = background.ifBlank { null }
                )
            )
            if (!resp.isSuccessful) {
                val errBody = resp.errorBody()?.string().orEmpty().take(300)
                return@withContext BackendMediaResult.Failed(
                    errorMessage = "सीन फ़ोटो API त्रुटि (HTTP ${resp.code()}): $errBody",
                    endpointUnsupported = resp.code() == 404 || resp.code() == 501
                )
            }
            val body = resp.body()
                ?: return@withContext BackendMediaResult.Failed("सीन फ़ोटो बैकएंड से खाली उत्तर मिला।")
            if (!body.error.isNullOrBlank()) {
                return@withContext BackendMediaResult.Failed(body.error)
            }

            val imagesDir = File(context.filesDir, "scene_images").apply { mkdirs() }
            val outFile = File(imagesDir, "scene_ai_${sceneId}_${System.currentTimeMillis()}.jpg")

            val b64 = body.imageBase64?.trim()
            val url = body.imageUrl?.trim()
            when {
                !b64.isNullOrBlank() -> {
                    val cleanB64 = b64.substringAfter("base64,", b64)
                    val bytes = Base64.decode(cleanB64, Base64.DEFAULT)
                    if (bytes.isEmpty()) {
                        return@withContext BackendMediaResult.Failed("इमेज डेटा खाली प्राप्त हुआ।")
                    }
                    outFile.writeBytes(bytes)
                }
                !url.isNullOrBlank() -> {
                    downloadBinaryToFile(resolveMediaUrl(url, config), outFile, config)
                }
                else -> {
                    return@withContext BackendMediaResult.Failed("बैकएंड ने कोई imageUrl या imageBase64 नहीं लौटाया।")
                }
            }

            if (!outFile.exists() || outFile.length() == 0L) {
                return@withContext BackendMediaResult.Failed("सीन फ़ोटो फ़ाइल सहेजी नहीं जा सकी।")
            }
            BackendMediaResult.Success(Uri.fromFile(outFile).toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackendMediaResult.Failed("सीन फ़ोटो नेटवर्क त्रुटि: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    suspend fun generateServerHindiVoice(
        sceneId: Long,
        dialogue: String,
        voiceStyle: String? = null
    ): BackendMediaResult = withContext(Dispatchers.IO) {
        val config = configStore.getConfigOnce()
        if (!config.isConfigured || !config.backendBaseUrl.startsWith("https://")) {
            return@withContext BackendMediaResult.Failed(
                errorMessage = "सर्वर AI वॉइस के लिए HTTPS बैकएंड कॉन्फ़िगर नहीं है।",
                isConfigMissing = true
            )
        }
        if (dialogue.isBlank()) {
            return@withContext BackendMediaResult.Failed("हिंदी संवाद खाली है।")
        }

        try {
            val api = buildApi(config)
            val resp = api.generateHindiTts(
                AudioTtsRequest(
                    text = dialogue,
                    languageCode = "hi-IN",
                    voiceStyle = voiceStyle
                )
            )
            if (!resp.isSuccessful) {
                val errBody = resp.errorBody()?.string().orEmpty().take(300)
                return@withContext BackendMediaResult.Failed(
                    errorMessage = "AI वॉइस API त्रुटि (HTTP ${resp.code()}): $errBody",
                    endpointUnsupported = resp.code() == 404 || resp.code() == 501
                )
            }
            val body = resp.body()
                ?: return@withContext BackendMediaResult.Failed("AI वॉइस बैकएंड से खाली उत्तर मिला।")
            if (!body.error.isNullOrBlank()) {
                return@withContext BackendMediaResult.Failed(body.error)
            }

            val ext = body.format?.lowercase()?.takeIf { it in setOf("wav", "mp3", "m4a", "ogg") } ?: "wav"
            val audioDir = File(context.filesDir, "hindi_voices").apply { mkdirs() }
            val outFile = File(audioDir, "scene_ai_${sceneId}_${System.currentTimeMillis()}.$ext")

            val b64 = body.audioBase64?.trim()
            val url = body.audioUrl?.trim()
            when {
                !b64.isNullOrBlank() -> {
                    val cleanB64 = b64.substringAfter("base64,", b64)
                    val bytes = Base64.decode(cleanB64, Base64.DEFAULT)
                    if (bytes.isEmpty()) {
                        return@withContext BackendMediaResult.Failed("AI वॉइस डेटा खाली प्राप्त हुआ।")
                    }
                    outFile.writeBytes(bytes)
                }
                !url.isNullOrBlank() -> {
                    downloadBinaryToFile(resolveMediaUrl(url, config), outFile, config)
                }
                else -> {
                    return@withContext BackendMediaResult.Failed("बैकएंड ने कोई audioUrl या audioBase64 नहीं लौटाया।")
                }
            }

            if (!outFile.exists() || outFile.length() == 0L) {
                return@withContext BackendMediaResult.Failed("AI वॉइस फ़ाइल सहेजी नहीं जा सकी।")
            }
            BackendMediaResult.Success(Uri.fromFile(outFile).toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackendMediaResult.Failed("AI वॉइस सर्वर त्रुटि: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    suspend fun generateSceneVideo(
        sceneId: Long,
        compiledPrompt: String,
        style: VideoStyle,
        aspectRatio: AspectRatioOption,
        durationSeconds: Int,
        mode: GenerationMode,
        cameraAngle: String,
        background: String,
        hindiDialogue: String,
        referenceImageUri: String?,
        onProgress: suspend (VideoJobUpdate.InProgress) -> Unit
    ): VideoJobUpdate = withContext(Dispatchers.IO) {
        val config = configStore.getConfigOnce()
        if (!config.isConfigured) {
            return@withContext VideoJobUpdate.Failed(
                errorMessage = "सुरक्षित वीडियो बैकएंड कॉन्फ़िगर नहीं है। 'स्टूडियो सेटिंग्स' में अपना HTTPS बैकएंड URL और API कुंजी जोड़ें। (नकली वीडियो नहीं बनाया जाएगा)",
                isConfigMissing = true
            )
        }
        if (!config.backendBaseUrl.startsWith("https://")) {
            return@withContext VideoJobUpdate.Failed(
                errorMessage = "असुरक्षित URL: केवल HTTPS बैकएंड एंडपॉइंट समर्थित हैं।",
                isConfigMissing = true
            )
        }
        if (compiledPrompt.isBlank()) {
            return@withContext VideoJobUpdate.Failed(
                errorMessage = "वीडियो प्रॉम्प्ट खाली है। कृपया पहले दृश्य का विवरण लिखें।"
            )
        }

        val imageBase64 = if (mode == GenerationMode.IMAGE_TO_VIDEO) {
            if (referenceImageUri.isNullOrBlank()) {
                return@withContext VideoJobUpdate.Failed(
                    errorMessage = "इमेज-टू-वीडियो मोड के लिए कृपया पहले एक रेफ़रेंस इमेज चुनें या सीन फ़ोटो बनाएँ।"
                )
            }
            readUriAsCompressedBase64(referenceImageUri)
                ?: return@withContext VideoJobUpdate.Failed(
                    errorMessage = "चयनित रेफ़रेंस इमेज पढ़ी नहीं जा सकी। कृपया इमेज दोबारा चुनें।"
                )
        } else {
            null
        }

        try {
            onProgress(VideoJobUpdate.InProgress(SceneJobStatus.QUEUED, null, "बैकएंड को अनुरोध भेजा जा रहा है..."))
            val api = buildApi(config)
            val submitResponse = api.submitGeneration(
                VideoGenerationRequest(
                    model = config.videoModelId,
                    prompt = compiledPrompt,
                    style = style.name,
                    aspectRatio = aspectRatio.code,
                    durationSeconds = durationSeconds,
                    mode = mode.apiValue,
                    cameraAngle = cameraAngle,
                    background = background,
                    hindiDialogue = hindiDialogue.ifBlank { null },
                    referenceImageBase64 = imageBase64
                )
            )

            if (!submitResponse.isSuccessful) {
                val errBody = submitResponse.errorBody()?.string().orEmpty().take(350)
                val quotaHint = if (submitResponse.code() == 429) " (प्रदाता कोटा सीमा समाप्त)" else ""
                return@withContext VideoJobUpdate.Failed(
                    errorMessage = "वीडियो अनुरोध अस्वीकृत: HTTP ${submitResponse.code()}$quotaHint — $errBody"
                )
            }

            val initialBody = submitResponse.body()
                ?: return@withContext VideoJobUpdate.Failed("बैकएंड से खाली उत्तर प्राप्त हुआ।")

            if (!initialBody.error.isNullOrBlank()) {
                return@withContext VideoJobUpdate.Failed(initialBody.error, initialBody.jobId)
            }

            // Immediate completion if backend returns videoUrl or videoBase64 synchronously
            if (initialBody.status.equals("SUCCEEDED", ignoreCase = true) ||
                initialBody.status.equals("COMPLETED", ignoreCase = true)
            ) {
                if (!initialBody.videoBase64.isNullOrBlank()) {
                    onProgress(VideoJobUpdate.InProgress(SceneJobStatus.RUNNING, initialBody.jobId, "MP4 क्लिप सेव हो रही है..."))
                    val localPath = saveBase64Mp4ToLocalCache(sceneId, initialBody.videoBase64)
                    return@withContext VideoJobUpdate.Completed(localPath, initialBody.jobId)
                }
                if (!initialBody.videoUrl.isNullOrBlank()) {
                    onProgress(VideoJobUpdate.InProgress(SceneJobStatus.RUNNING, initialBody.jobId, "वीडियो डाउनलोड हो रहा है..."))
                    val localPath = downloadMp4ToLocalCache(sceneId, resolveMediaUrl(initialBody.videoUrl, config), config)
                    return@withContext VideoJobUpdate.Completed(localPath, initialBody.jobId)
                }
            }

            val jobId = initialBody.jobId?.takeIf { it.isNotBlank() }
                ?: return@withContext VideoJobUpdate.Failed("बैकएंड ने कोई Job ID या Video URL वापस नहीं किया।")

            // Poll operation status cleanly with real backoff
            repeat(60) { attempt ->
                delay(4_000L)
                val pollResp = api.getOperationStatus(jobId)
                if (!pollResp.isSuccessful) {
                    val errText = pollResp.errorBody()?.string().orEmpty().take(300)
                    return@withContext VideoJobUpdate.Failed(
                        errorMessage = "स्टेटस जाँच त्रुटि (HTTP ${pollResp.code()}): $errText",
                        jobId = jobId
                    )
                }
                val op = pollResp.body()
                    ?: return@withContext VideoJobUpdate.Failed("पोलिंग के दौरान खाली उत्तर मिला।", jobId)

                when (op.status?.uppercase()) {
                    "SUCCEEDED", "COMPLETED", "DONE" -> {
                        val b64 = op.videoBase64
                        val url = op.videoUrl
                        if (!b64.isNullOrBlank()) {
                            onProgress(VideoJobUpdate.InProgress(SceneJobStatus.RUNNING, jobId, "MP4 क्लिप सेव हो रही है..."))
                            val localPath = saveBase64Mp4ToLocalCache(sceneId, b64)
                            return@withContext VideoJobUpdate.Completed(localPath, jobId)
                        }
                        if (url.isNullOrBlank()) {
                            return@withContext VideoJobUpdate.Failed("प्रक्रिया पूर्ण हुई पर videoUrl खाली मिला।", jobId)
                        }
                        onProgress(VideoJobUpdate.InProgress(SceneJobStatus.RUNNING, jobId, "MP4 क्लिप डाउनलोड हो रही है..."))
                        val localPath = downloadMp4ToLocalCache(sceneId, resolveMediaUrl(url, config), config)
                        return@withContext VideoJobUpdate.Completed(localPath, jobId)
                    }
                    "FAILED", "ERROR", "CANCELLED" -> {
                        return@withContext VideoJobUpdate.Failed(
                            errorMessage = op.error ?: op.message ?: "बैकएंड ने वीडियो जनरेशन विफल घोषित किया।",
                            jobId = jobId
                        )
                    }
                    else -> {
                        val pct = op.progressPercent?.let { " ($it%)" } ?: ""
                        onProgress(
                            VideoJobUpdate.InProgress(
                                status = SceneJobStatus.RUNNING,
                                jobId = jobId,
                                message = "जनरेशन प्रगति पर$pct — चरण ${attempt + 1}"
                            )
                        )
                    }
                }
            }

            VideoJobUpdate.Failed(
                errorMessage = "समय सीमा समाप्त (4 मिनट): बैकएंड जॉब $jobId अभी भी चल रही है।",
                jobId = jobId
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VideoJobUpdate.Failed(
                errorMessage = "नेटवर्क / सर्वर त्रुटि: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun openUriStream(uriString: String): InputStream? {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
            val file = File(uri.path!!)
            if (file.exists()) FileInputStream(file) else null
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    private fun readUriAsCompressedBase64(uriString: String): String? {
        return try {
            val rawBytes = openUriStream(uriString)?.use { it.readBytes() } ?: return null
            if (rawBytes.isEmpty()) return null
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            if (bitmap == null) {
                if (rawBytes.size > 10 * 1024 * 1024) return null
                return Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            }
            val maxDim = 1280
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            scaled.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveBase64Mp4ToLocalCache(sceneId: Long, base64Video: String): String {
        val clipsDir = File(context.filesDir, "generated_clips").apply { mkdirs() }
        val targetFile = File(clipsDir, "scene_${sceneId}_${System.currentTimeMillis()}.mp4")
        val cleanB64 = base64Video.substringAfter("base64,", base64Video)
        val bytes = Base64.decode(cleanB64, Base64.DEFAULT)
        if (bytes.isEmpty()) {
            throw IllegalStateException("प्राप्त वीडियो डेटा खाली है")
        }
        targetFile.writeBytes(bytes)
        return Uri.fromFile(targetFile).toString()
    }

    private fun downloadMp4ToLocalCache(
        sceneId: Long,
        videoUrl: String,
        config: OwnerBackendConfig
    ): String {
        val clipsDir = File(context.filesDir, "generated_clips").apply { mkdirs() }
        val targetFile = File(clipsDir, "scene_${sceneId}_${System.currentTimeMillis()}.mp4")
        downloadBinaryToFile(videoUrl, targetFile, config)
        return Uri.fromFile(targetFile).toString()
    }

    private fun downloadBinaryToFile(
        url: String,
        targetFile: File,
        config: OwnerBackendConfig
    ) {
        val client = buildClient(config)
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("फ़ाइल डाउनलोड विफल: HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IllegalStateException("डाउनलोड फ़ाइल बॉडी खाली है")
            targetFile.outputStream().use { out ->
                body.byteStream().copyTo(out)
            }
        }
    }
}
