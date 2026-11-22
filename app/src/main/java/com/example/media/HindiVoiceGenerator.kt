package com.example.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import java.util.UUID

sealed interface TtsResult {
    data class AudioSaved(val fileUri: String) : TtsResult
    data class Error(val messageHindi: String) : TtsResult
}

/**
 * On-device Hindi Dialogue Voice Synthesizer & Audio Player.
 * Supports retrying TTS init, hi-IN / hi locale fallback, real .wav synthesis,
 * and playing saved Hindi AI voice files (.wav/.mp3).
 */
class HindiVoiceGenerator(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var initDeferred: CompletableDeferred<Boolean>? = null
    private var initError: String? = null
    private var mediaPlayer: MediaPlayer? = null

    private suspend fun ensureReady(): Boolean {
        val currentDeferred = initDeferred
        if (currentDeferred != null && currentDeferred.isCompleted) {
            val ok = runCatching { currentDeferred.getCompleted() }.getOrDefault(false)
            if (ok && tts != null) return true
            // Re-initialize if previous initialization failed or TTS was released
            tts?.shutdown()
            tts = null
        }

        val deferred = synchronized(this) {
            val existing = initDeferred
            if (existing != null && (!existing.isCompleted || (runCatching { existing.getCompleted() }.getOrDefault(false) && tts != null))) {
                existing
            } else {
                val fresh = CompletableDeferred<Boolean>()
                initDeferred = fresh
                initError = null
                withInitOnMainThread(fresh)
                fresh
            }
        }

        return try {
            withTimeout(8_000L) { deferred.await() }
        } catch (_: Exception) {
            initError = "Android Text-to-Speech इंजन तैयार होने में समय समाप्त हुआ।"
            false
        }
    }

    private fun withInitOnMainThread(deferred: CompletableDeferred<Boolean>) {
        android.os.Handler(context.mainLooper).post {
            try {
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val engine = tts
                        val hiIn = Locale.forLanguageTag("hi-IN")
                        val hiBase = Locale.forLanguageTag("hi")
                        val resPrimary = engine?.setLanguage(hiIn)
                        val primaryOk = resPrimary != null &&
                            resPrimary != TextToSpeech.LANG_MISSING_DATA &&
                            resPrimary != TextToSpeech.LANG_NOT_SUPPORTED

                        if (primaryOk) {
                            if (!deferred.isCompleted) deferred.complete(true)
                        } else {
                            val resFallback = engine?.setLanguage(hiBase)
                            val fallbackOk = resFallback != null &&
                                resFallback != TextToSpeech.LANG_MISSING_DATA &&
                                resFallback != TextToSpeech.LANG_NOT_SUPPORTED
                            if (fallbackOk) {
                                if (!deferred.isCompleted) deferred.complete(true)
                            } else {
                                initError = "डिवाइस में हिंदी (hi-IN) TTS वॉइस पैक उपलब्ध नहीं है। Android सेटिंग्स > Speech Services में हिंदी वॉइस डाउनलोड करें या स्टूडियो सेटिंग्स में सर्वर TTS जोड़ें।"
                                if (!deferred.isCompleted) deferred.complete(false)
                            }
                        }
                    } else {
                        initError = "Android Text-to-Speech इंजन प्रारंभ नहीं हो सका (कोड: $status)।"
                        if (!deferred.isCompleted) deferred.complete(false)
                    }
                }
            } catch (e: Exception) {
                initError = "TTS इंजन त्रुटि: ${e.localizedMessage ?: e.javaClass.simpleName}"
                if (!deferred.isCompleted) deferred.complete(false)
            }
        }
    }

    suspend fun speakDialogue(text: String, pitch: Float = 1.0f, rate: Float = 0.95f): TtsResult {
        if (text.isBlank()) return TtsResult.Error("बोलने के लिए हिंदी संवाद खाली है।")
        stopAudioPlayback()
        val ready = ensureReady()
        if (!ready) {
            return TtsResult.Error(initError ?: "हिंदी TTS उपलब्ध नहीं है।")
        }
        val engine = tts ?: return TtsResult.Error("TTS इंजन तैयार नहीं है।")

        val utteranceId = "speak_${UUID.randomUUID()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val startedOrDone = CompletableDeferred<Boolean>()
        var utteranceErrorMsg: String? = null

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                if (id == utteranceId && !startedOrDone.isCompleted) {
                    startedOrDone.complete(true)
                }
            }
            override fun onDone(id: String?) {
                if (id == utteranceId && !startedOrDone.isCompleted) {
                    startedOrDone.complete(true)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && !startedOrDone.isCompleted) {
                    utteranceErrorMsg = "हिंदी आवाज़ चलाने में त्रुटि हुई।"
                    startedOrDone.complete(false)
                }
            }
            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && !startedOrDone.isCompleted) {
                    utteranceErrorMsg = "हिंदी आवाज़ चलाने में त्रुटि (TTS कोड: $errorCode)।"
                    startedOrDone.complete(false)
                }
            }
        })

        engine.setPitch(pitch)
        engine.setSpeechRate(rate)
        val res = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (res != TextToSpeech.SUCCESS) {
            return TtsResult.Error("संवाद चलाने में त्रुटि (TTS कोड: $res)")
        }

        val ok = try {
            withTimeout(5_000L) { startedOrDone.await() }
        } catch (_: Exception) {
            true // Speak queued; engine may take longer to start buffer on slow devices
        }

        return if (ok) {
            TtsResult.AudioSaved("")
        } else {
            TtsResult.Error(utteranceErrorMsg ?: "हिंदी TTS आवाज़ प्ले नहीं हो सकी।")
        }
    }

    suspend fun synthesizeDialogueToFile(
        sceneId: Long,
        text: String,
        pitch: Float = 1.0f,
        rate: Float = 0.95f
    ): TtsResult = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext TtsResult.Error("हिंदी संवाद खाली है।")
        val ready = ensureReady()
        if (!ready) {
            return@withContext TtsResult.Error(initError ?: "हिंदी TTS उपलब्ध नहीं है।")
        }
        val engine = tts ?: return@withContext TtsResult.Error("TTS इंजन उपलब्ध नहीं है।")

        val audioDir = File(context.filesDir, "hindi_voices").apply { mkdirs() }
        val outputFile = File(audioDir, "scene_${sceneId}_${System.currentTimeMillis()}.wav")
        val utteranceId = "synth_${sceneId}_${UUID.randomUUID()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val completion = CompletableDeferred<Boolean>()
        var errorDetail: String? = null

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId && !completion.isCompleted) {
                    completion.complete(true)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && !completion.isCompleted) {
                    errorDetail = "TTS फ़ाइल संश्लेषण विफल।"
                    completion.complete(false)
                }
            }
            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && !completion.isCompleted) {
                    errorDetail = "TTS फ़ाइल संश्लेषण विफल (कोड: $errorCode)।"
                    completion.complete(false)
                }
            }
        })

        engine.setPitch(pitch)
        engine.setSpeechRate(rate)
        val queued = engine.synthesizeToFile(text, params, outputFile, utteranceId)
        if (queued != TextToSpeech.SUCCESS) {
            return@withContext TtsResult.Error("ऑडियो फ़ाइल जनरेट नहीं हो सकी (कोड: $queued)")
        }

        val ok = try {
            withTimeout(25_000L) { completion.await() }
        } catch (_: Exception) {
            errorDetail = "हिंदी वॉइस जनरेशन समय सीमा समाप्त।"
            false
        }

        if (ok && outputFile.exists() && outputFile.length() > 44L) {
            TtsResult.AudioSaved(Uri.fromFile(outputFile).toString())
        } else {
            if (outputFile.exists() && outputFile.length() <= 44L) outputFile.delete()
            TtsResult.Error(
                errorDetail
                    ?: "हिंदी वॉइस फ़ाइल सेव नहीं हो सकी। कृपया डिवाइस सेटिंग्स में Google Speech Services हिंदी पैक जांचें या स्टूडियो सेटिंग्स में बैकएंड TTS कॉन्फ़िगर करें।"
            )
        }
    }

    fun playAudioFile(uriString: String, onError: (String) -> Unit) {
        if (uriString.isBlank()) {
            onError("चलाने के लिए कोई ऑडियो फ़ाइल उपलब्ध नहीं है।")
            return
        }
        stopAudioPlayback()
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file" && !uri.path.isNullOrBlank() && !File(uri.path!!).exists()) {
                onError("सेव की गई ऑडियो फ़ाइल डिवाइस पर मौजूद नहीं है।")
                return
            }
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(context, uri)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    it.release()
                    if (mediaPlayer === it) mediaPlayer = null
                }
                setOnErrorListener { mp, what, extra ->
                    mp.release()
                    if (mediaPlayer === mp) mediaPlayer = null
                    onError("ऑडियो चलाने में त्रुटि (कोड: $what/$extra)")
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            onError("ऑडियो फ़ाइल नहीं चल सकी: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    fun stopAudioPlayback() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    fun release() {
        stopAudioPlayback()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
