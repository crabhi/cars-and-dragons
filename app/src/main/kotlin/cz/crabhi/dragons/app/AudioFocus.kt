package cz.crabhi.dragons.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Holds audio focus for the whole game. Navigation prompts and phone calls take focus away: the game
 * pauses and resumes (with a recap) when focus comes back.
 */
class AudioFocus(
    context: Context,
    private val onLoss: () -> Unit,
    private val onGain: () -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var held = false

    private val request =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            // Pause instead of being ducked under navigation prompts.
            .setWillPauseWhenDucked(true)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        held = true
                        onGain()
                    }

                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                    -> {
                        held = change != AudioManager.AUDIOFOCUS_LOSS
                        onLoss()
                    }
                }
            }.build()

    /** Returns true when focus was granted right away; false means wait for [onGain]. */
    fun request(): Boolean {
        val result = audioManager.requestAudioFocus(request)
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return held
    }

    fun abandon() {
        audioManager.abandonAudioFocusRequest(request)
        held = false
    }
}
