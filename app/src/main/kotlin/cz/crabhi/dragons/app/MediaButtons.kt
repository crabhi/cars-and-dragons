package cz.crabhi.dragons.app

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.view.KeyEvent

/**
 * A media session so steering-wheel media buttons reach the game. Any press interrupts narration
 * (barge-in) or resumes a paused game.
 */
class MediaButtons(
    context: Context,
    private val onPress: () -> Unit,
) {
    private val session = MediaSession(context, "CarsAndDragons")

    init {
        session.setCallback(
            object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        }
                    if (event?.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onPress()
                    return true
                }

                override fun onPlay() = onPress()

                override fun onPause() = onPress()

                override fun onSkipToNext() = onPress()
            },
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT,
                ).setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .build(),
        )
        session.isActive = true
    }

    fun release() {
        session.isActive = false
        session.release()
    }
}
