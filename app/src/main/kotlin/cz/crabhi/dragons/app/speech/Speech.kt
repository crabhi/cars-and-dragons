package cz.crabhi.dragons.app.speech

import android.os.ParcelFileDescriptor
import java.io.Closeable

/** Text-to-speech. [speak] returns false when it was interrupted. */
interface SpeechOutput {
    suspend fun speak(text: String): Boolean

    fun stop()
}

/** A PCM stream (16 kHz mono 16-bit) to feed speech recognition from, e.g. the car microphone. */
interface AudioStream : Closeable {
    val fd: ParcelFileDescriptor
    val sampleRate: Int
}

/** Speech recognition. Returns null on silence, timeout or error. Planned alternative: Vosk. */
interface SpeechInput {
    suspend fun listen(
        hints: List<String>,
        long: Boolean,
        audio: (() -> AudioStream?)? = null,
    ): String?

    /** Aborts a listen in progress (it then returns null). */
    fun cancel()
}
