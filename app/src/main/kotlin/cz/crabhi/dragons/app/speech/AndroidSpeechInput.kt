package cz.crabhi.dragons.app.speech

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Android SpeechRecognizer in cs-CZ. Prefers on-device recognition and falls back to Google's online
 * recognition when the Czech on-device model is not available.
 */
class AndroidSpeechInput(
    private val context: Context,
) : SpeechInput {
    private val main = Handler(Looper.getMainLooper())
    private var onDeviceFailed = false
    private var abort: (() -> Unit)? = null

    /** Uptime (ms) when the recognizer detected the end of speech. Used by the latency probe. */
    @Volatile var onEndOfSpeech: ((Long) -> Unit)? = null

    val onDeviceAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun cancel() {
        main.post { abort?.invoke() }
    }

    override suspend fun listen(
        hints: List<String>,
        long: Boolean,
        audio: (() -> AudioStream?)?,
    ): String? {
        val useOnDevice = onDeviceAvailable && !onDeviceFailed
        val first = listenOnce(hints, long, audio, useOnDevice)
        if (first is Outcome.LanguageUnavailable && useOnDevice) {
            Log.i(TAG, "On-device Czech recognition unavailable, using online recognition")
            onDeviceFailed = true
            return (listenOnce(hints, long, audio, onDevice = false) as? Outcome.Text)?.text
        }
        return (first as? Outcome.Text)?.text
    }

    private sealed interface Outcome {
        data class Text(
            val text: String?,
        ) : Outcome

        data object LanguageUnavailable : Outcome
    }

    private suspend fun listenOnce(
        hints: List<String>,
        long: Boolean,
        audio: (() -> AudioStream?)?,
        onDevice: Boolean,
    ): Outcome =
        withContext(Dispatchers.Main) {
            val recognizer =
                if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            val stream = audio?.invoke()
            try {
                suspendCancellableCoroutine { cont ->
                    var finished = false

                    fun finish(outcome: Outcome) {
                        if (finished) return
                        finished = true
                        main.removeCallbacksAndMessages(TOKEN)
                        abort = null
                        recognizer.cancel()
                        if (cont.isActive) cont.resume(outcome)
                    }
                    abort = { finish(Outcome.Text(null)) }
                    cont.invokeOnCancellation { main.post { finish(Outcome.Text(null)) } }

                    recognizer.setRecognitionListener(
                        object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) = Unit

                            override fun onBeginningOfSpeech() {
                                // The player started talking: drop the start window, keep a hard cap.
                                main.removeCallbacksAndMessages(TOKEN)
                                main.postAtTime({ recognizer.stopListening() }, TOKEN, SystemClock.uptimeMillis() + maxUtteranceMs(long))
                            }

                            override fun onRmsChanged(rmsdB: Float) = Unit

                            override fun onBufferReceived(buffer: ByteArray?) = Unit

                            override fun onEndOfSpeech() {
                                onEndOfSpeech?.invoke(SystemClock.uptimeMillis())
                            }

                            override fun onError(error: Int) {
                                Log.i(TAG, "Recognition error $error")
                                val languageProblem =
                                    error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                                finish(if (languageProblem) Outcome.LanguageUnavailable else Outcome.Text(null))
                            }

                            override fun onResults(results: Bundle?) {
                                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                                finish(Outcome.Text(text?.takeIf { it.isNotBlank() }))
                            }

                            override fun onPartialResults(partialResults: Bundle?) = Unit

                            override fun onEvent(
                                eventType: Int,
                                params: Bundle?,
                            ) = Unit
                        },
                    )
                    recognizer.startListening(intent(hints, long, stream))
                    // Listening window: if nobody starts speaking in time, give up.
                    main.postAtTime({ finish(Outcome.Text(null)) }, TOKEN, SystemClock.uptimeMillis() + startWindowMs(long))
                }
            } finally {
                main.removeCallbacksAndMessages(TOKEN)
                recognizer.destroy()
                stream?.close()
            }
        }

    private fun intent(
        hints: List<String>,
        long: Boolean,
        stream: AudioStream?,
    ): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (long) 2500L else 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, if (long) 2000L else 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (hints.isNotEmpty()) putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(hints))
                if (stream != null) {
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, stream.fd)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, stream.sampleRate)
                }
            }
        }

    /**
     * Android 13+: reports whether Czech is installed for on-device recognition and starts the model
     * download when it is supported but missing.
     */
    fun checkCzechOnDevice(callback: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !onDeviceAvailable) {
            callback("on-device: not available")
            return
        }
        main.post {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val intent = intent(emptyList(), long = false, stream = null)
            recognizer.checkRecognitionSupport(
                intent,
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        val installed = support.installedOnDeviceLanguages.any { it.startsWith("cs") }
                        val supported = support.supportedOnDeviceLanguages.any { it.startsWith("cs") }
                        val pending = support.pendingOnDeviceLanguages.any { it.startsWith("cs") }
                        when {
                            installed -> {
                                callback("on-device cs-CZ: installed")
                            }

                            pending -> {
                                callback("on-device cs-CZ: downloading")
                            }

                            supported -> {
                                recognizer.triggerModelDownload(intent)
                                callback("on-device cs-CZ: download started")
                            }

                            else -> {
                                callback("on-device cs-CZ: not supported, online recognition will be used")
                            }
                        }
                        recognizer.destroy()
                    }

                    override fun onError(error: Int) {
                        callback("on-device check failed ($error)")
                        recognizer.destroy()
                    }
                },
            )
        }
    }

    companion object {
        private const val TAG = "AndroidSpeechInput"
        private const val LANGUAGE = "cs-CZ"
        private val TOKEN = Any()

        /** Time for the player to start speaking (the plan's ~8 second window). */
        fun startWindowMs(long: Boolean) = if (long) 12_000L else 8_000L

        fun maxUtteranceMs(long: Boolean) = if (long) 45_000L else 20_000L
    }
}
