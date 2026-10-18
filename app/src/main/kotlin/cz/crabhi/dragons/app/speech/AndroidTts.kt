package cz.crabhi.dragons.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import cz.crabhi.dragons.app.Czech
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Android TextToSpeech in cs-CZ. */
class AndroidTts(
    context: Context,
) : SpeechOutput {
    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val tts: TextToSpeech

    /** Called with the uptime (ms) when an utterance actually starts playing. Used by the latency probe. */
    @Volatile var onUtteranceStart: ((Long) -> Unit)? = null

    init {
        tts =
            TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready.complete(setCzech())
                } else {
                    Log.w(TAG, "TTS init failed: $status")
                    ready.complete(false)
                }
            }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    onUtteranceStart?.invoke(SystemClock.uptimeMillis())
                }

                override fun onDone(utteranceId: String) {
                    pending.remove(utteranceId)?.complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) {
                    pending.remove(utteranceId)?.complete(true)
                }

                override fun onError(
                    utteranceId: String,
                    errorCode: Int,
                ) {
                    Log.w(TAG, "TTS error $errorCode")
                    pending.remove(utteranceId)?.complete(true)
                }

                override fun onStop(
                    utteranceId: String,
                    interrupted: Boolean,
                ) {
                    pending.remove(utteranceId)?.complete(false)
                }
            },
        )
    }

    private fun setCzech(): Boolean {
        val result = tts.setLanguage(Czech.locale)
        val offlineVoice =
            tts.voices?.firstOrNull { v ->
                v.locale.language == "cs" &&
                    !v.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features
            }
        if (offlineVoice != null) tts.voice = offlineVoice
        return result >= TextToSpeech.LANG_AVAILABLE
    }

    suspend fun awaitReady(): Boolean = ready.await()

    /** True when a Czech voice is installed for offline use. */
    suspend fun hasOfflineCzechVoice(): Boolean {
        if (!awaitReady()) return false
        return tts.voices.orEmpty().any { v ->
            v.locale.language == "cs" && !v.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features
        }
    }

    override suspend fun speak(text: String): Boolean {
        awaitReady()
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>()
        pending[id] = done
        val params = Bundle()
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) {
            pending.remove(id)
            return true
        }
        return try {
            done.await()
        } finally {
            pending.remove(id)
        }
    }

    override fun stop() {
        tts.stop()
        pending.values.forEach { it.complete(false) }
        pending.clear()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    companion object {
        private const val TAG = "AndroidTts"
    }
}
