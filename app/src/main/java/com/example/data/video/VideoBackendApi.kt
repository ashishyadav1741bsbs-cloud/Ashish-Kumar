package com.example.data.video

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class VideoGenerationRequest(
    val model: String,
    val prompt: String,
    val style: String,
    val aspectRatio: String,
    val durationSeconds: Int,
    val mode: String,
    val cameraAngle: String,
    val background: String,
    val hindiDialogue: String? = null,
    val referenceImageBase64: String? = null
)

@JsonClass(generateAdapter = true)
data class VideoGenerationJobResponse(
    val jobId: String? = null,
    val status: String? = null,
    val progressPercent: Int? = null,
    val videoUrl: String? = null,
    val videoBase64: String? = null,
    val error: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ImageGenerationRequest(
    val prompt: String,
    val style: String,
    val aspectRatio: String,
    val characterDescription: String? = null,
    val background: String? = null
)

@JsonClass(generateAdapter = true)
data class ImageGenerationResponse(
    val imageUrl: String? = null,
    val imageBase64: String? = null,
    val error: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class AudioTtsRequest(
    val text: String,
    val languageCode: String = "hi-IN",
    val voiceStyle: String? = null
)

@JsonClass(generateAdapter = true)
data class AudioTtsResponse(
    val audioUrl: String? = null,
    val audioBase64: String? = null,
    val format: String? = "wav",
    val error: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String? = null,
    val provider: String? = null,
    val message: String? = null
)

interface VideoBackendApi {
    @GET("health")
    suspend fun checkHealth(): Response<HealthResponse>

    @POST("v1/videos/generate")
    suspend fun submitGeneration(
        @Body request: VideoGenerationRequest
    ): Response<VideoGenerationJobResponse>

    @GET("v1/videos/operations/{jobId}")
    suspend fun getOperationStatus(
        @Path("jobId") jobId: String
    ): Response<VideoGenerationJobResponse>

    @POST("v1/images/generate")
    suspend fun generateSceneImage(
        @Body request: ImageGenerationRequest
    ): Response<ImageGenerationResponse>

    @POST("v1/audio/tts")
    suspend fun generateHindiTts(
        @Body request: AudioTtsRequest
    ): Response<AudioTtsResponse>
}
