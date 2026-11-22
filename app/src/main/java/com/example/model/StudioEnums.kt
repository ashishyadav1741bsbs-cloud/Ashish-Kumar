package com.example.model

enum class VideoStyle(
    val hindiLabel: String,
    val englishLabel: String,
    val promptDirective: String
) {
    REALISTIC_CINEMA(
        hindiLabel = "रियलिस्टिक ह्यूमन सिनेमा",
        englishLabel = "Realistic Human Cinema",
        promptDirective = "Photorealistic live-action cinema, 35mm anamorphic film lens, natural skin textures, realistic human expressions, dramatic volumetric film lighting, 24fps movie color grading"
    ),
    CARTOON_3D(
        hindiLabel = "कार्टून और 3D एनिमेशन",
        englishLabel = "Cartoon & 3D Animation",
        promptDirective = "High-end Pixar-style 3D animation and stylized cartoon cinema, expressive character rigs, rich PBR materials, vibrant cinematic rim lighting, smooth stylized motion"
    );

    companion object {
        fun fromNameOrDefault(raw: String?): VideoStyle =
            entries.firstOrNull { it.name == raw } ?: REALISTIC_CINEMA
    }
}

enum class AspectRatioOption(
    val code: String,
    val hindiLabel: String,
    val ratioFloat: Float,
    val widthPx: Int,
    val heightPx: Int
) {
    RATIO_16_9("16:9", "सिनेमा वाइड (16:9)", 16f / 9f, 1280, 720),
    RATIO_9_16("9:16", "रील्स / वर्टिकल (9:16)", 9f / 16f, 720, 1280),
    RATIO_1_1("1:1", "स्क्वायर (1:1)", 1f, 1024, 1024);

    companion object {
        fun fromNameOrDefault(raw: String?): AspectRatioOption =
            entries.firstOrNull { it.name == raw || it.code == raw } ?: RATIO_16_9
    }
}

enum class GenerationMode(val hindiLabel: String, val apiValue: String) {
    TEXT_TO_VIDEO("टेक्स्ट से वीडियो", "text_to_video"),
    IMAGE_TO_VIDEO("इमेज से वीडियो", "image_to_video");

    companion object {
        fun fromNameOrDefault(raw: String?): GenerationMode =
            entries.firstOrNull { it.name == raw } ?: TEXT_TO_VIDEO
    }
}

enum class SceneJobStatus(val hindiLabel: String) {
    IDLE("तैयार नहीं"),
    QUEUED("कतार में"),
    RUNNING("जनरेट हो रहा है"),
    SUCCEEDED("वीडियो तैयार"),
    FAILED("त्रुटि");

    companion object {
        fun fromNameOrDefault(raw: String?): SceneJobStatus =
            entries.firstOrNull { it.name == raw } ?: IDLE
    }
}

object StudioPresets {
    const val MAX_MOVIE_DURATION_SECONDS = 1800 // 30 minutes

    val cameraAngles = listOf(
        "वाइड एस्टैब्लिशिंग शॉट (Wide Shot)",
        "मीडियम शॉट (Medium Shot)",
        "क्लोज़-अप शॉट (Close-up)",
        "ड्रोन एरियल शॉट (Aerial Drone)",
        "ट्रैकिंग डॉली शॉट (Tracking Shot)",
        "लो एंगल हीरो शॉट (Low Angle)",
        "ओवर-द-शोल्डर शॉट (Over-the-Shoulder)"
    )

    val durationOptionsSeconds = listOf(4, 6, 8, 10, 15, 20)
}
