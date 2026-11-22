package com.example.data.ai

import com.example.model.AspectRatioOption
import com.example.model.StudioPresets
import com.example.model.VideoStyle

data class GeneratedSceneDraft(
    val sceneNumber: Int,
    val videoPrompt: String,
    val characterDescription: String,
    val hindiDialogue: String,
    val cameraAngle: String,
    val background: String,
    val durationSeconds: Int,
    val isPlaceholder: Boolean
)

data class AiSceneBreakdownResult(
    val scenes: List<GeneratedSceneDraft>,
    val isPlaceholderOutput: Boolean,
    val noticeHindi: String
)

sealed interface AiImageResult {
    data class Error(val messageHindi: String) : AiImageResult
}

/**
 * Unified AI interface for story-to-scene breakdown and per-scene image generation.
 * Since Firebase setup was declined, this is backed by PlaceholderAiRepository
 * and clearly surfaces a Firebase AI Logic notice in the UI.
 */
interface AiRepository {
    suspend fun splitStoryIntoScenes(
        story: String,
        style: VideoStyle,
        targetMinutes: Int
    ): AiSceneBreakdownResult

    suspend fun generateSceneImageUnavailableNotice(
        sceneNumber: Int,
        prompt: String,
        style: VideoStyle,
        aspectRatio: AspectRatioOption
    ): AiImageResult
}

class PlaceholderAiRepository : AiRepository {
    override suspend fun splitStoryIntoScenes(
        story: String,
        style: VideoStyle,
        targetMinutes: Int
    ): AiSceneBreakdownResult {
        val cleaned = story.trim()
        require(cleaned.isNotEmpty()) { "कृपया सीन बनाने के लिए पहले कहानी लिखें।" }

        val rawSegments = cleaned
            .split(Regex("[।.!?\\n]+"))
            .map { it.trim() }
            .filter { it.length >= 4 }
            .ifEmpty { listOf(cleaned) }

        val targetTotalSeconds = targetMinutes.coerceIn(1, 30) * 60
        // Scale scene count so longer stories (up to 30 minutes) produce full multi-act scene schedules.
        val desiredSceneCount = when {
            rawSegments.size >= (targetTotalSeconds / 15) -> rawSegments.size.coerceAtMost(120)
            else -> (targetTotalSeconds / 15).coerceIn(rawSegments.size, 120)
        }

        val perSceneDuration = (targetTotalSeconds / desiredSceneCount.coerceAtLeast(1))
            .coerceIn(5, 15)

        val styleTag = if (style == VideoStyle.REALISTIC_CINEMA) {
            "सिनेमैटिक रियलिस्टिक 35mm लेंस, नेचुरल लाइटिंग"
        } else {
            "3D पिक्सार-स्टाइल कार्टून एनिमेशन, वाइब्रेंट लाइटिंग"
        }

        val narrativeBeats = listOf(
            "प्रस्तावना और माहौल स्थापना",
            "मुख्य किरदार प्रवेश",
            "कहानी का विकास",
            "संघर्ष और नाटकीय मोड़",
            "चरम दृश्य (Climax)",
            "निष्कर्ष और उपसंहार"
        )

        val drafts = (0 until desiredSceneCount).map { index ->
            val sourceSegment = rawSegments[index % rawSegments.size]
            val angle = StudioPresets.cameraAngles[index % StudioPresets.cameraAngles.size]
            val beatLabel = narrativeBeats[(index * narrativeBeats.size) / desiredSceneCount.coerceAtLeast(1)]
            val dialogueMatch = Regex("[\"“”']([^\"“”']+)[\"“”']").find(sourceSegment)?.groupValues?.getOrNull(1)
            val dialogue = dialogueMatch ?: sourceSegment.take(120)

            GeneratedSceneDraft(
                sceneNumber = index + 1,
                videoPrompt = "[नमूना AI प्रारूप • $beatLabel] $sourceSegment — $styleTag",
                characterDescription = "मुख्य किरदार (सीन ${index + 1} में समान वेशभूषा और चेहरे की निरंतरता बनाए रखें)",
                hindiDialogue = dialogue,
                cameraAngle = angle,
                background = "$beatLabel के अनुसार दृश्य पृष्ठभूमि (सीन ${index + 1})",
                durationSeconds = perSceneDuration,
                isPlaceholder = true
            )
        }

        return AiSceneBreakdownResult(
            scenes = drafts,
            isPlaceholderOutput = true,
            noticeHindi = "नमूना AI आउटपुट (${drafts.size} सीन • ${targetMinutes.coerceIn(1, 30)} मिनट योजना): यह विभाजन स्थानीय टेम्पलेट से तैयार किया गया है। लाइव Gemini AI विश्लेषण के लिए Firebase AI Logic सक्षम करना आवश्यक है।"
        )
    }

    override suspend fun generateSceneImageUnavailableNotice(
        sceneNumber: Int,
        prompt: String,
        style: VideoStyle,
        aspectRatio: AspectRatioOption
    ): AiImageResult {
        return AiImageResult.Error(
            "सीन #$sceneNumber की AI फ़ोटो बनाने के लिए 'स्टूडियो सेटिंग्स' में अपना HTTPS बैकएंड (/v1/images/generate) जोड़ें, या Firebase AI Logic (Gemini Nano Banana) सक्षम करें।"
        )
    }
}
