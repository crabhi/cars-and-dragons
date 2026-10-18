package cz.crabhi.dragons.app

import android.content.Context
import android.util.Log
import cz.crabhi.dragons.app.speech.AndroidSpeechInput
import cz.crabhi.dragons.app.speech.AndroidTts
import cz.crabhi.dragons.app.speech.AudioStream
import cz.crabhi.dragons.engine.GameConfig
import cz.crabhi.dragons.engine.GameMaster
import cz.crabhi.dragons.engine.HeuristicTactics
import cz.crabhi.dragons.engine.JevConfig
import cz.crabhi.dragons.engine.JevTacticsAdvisor
import cz.crabhi.dragons.engine.ListenRequest
import cz.crabhi.dragons.engine.LlmConfig
import cz.crabhi.dragons.engine.NarratorFactory
import cz.crabhi.dragons.engine.OpenAiNarrator
import cz.crabhi.dragons.engine.Phase
import cz.crabhi.dragons.engine.PlayerIo
import cz.crabhi.dragons.engine.SaveStore
import cz.crabhi.dragons.engine.StatusSnapshot
import cz.crabhi.dragons.engine.TranscriptStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the screens show. */
sealed interface GameUiState {
    data object Idle : GameUiState

    data class Running(
        val status: StatusSnapshot?,
        val paused: Boolean,
    ) : GameUiState

    data class Finished(
        val status: StatusSnapshot?,
    ) : GameUiState
}

/**
 * Owns the running game on the phone: speech in/out, audio focus, media buttons and the foreground
 * service. Both the car screen and the phone activity drive it.
 */
class GameController(
    private val app: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<GameUiState>(GameUiState.Idle)
    val state: StateFlow<GameUiState> = _state

    val tts by lazy { AndroidTts(app) }
    val speechInput by lazy { AndroidSpeechInput(app) }

    private val paused = MutableStateFlow(false)
    private var job: Job? = null
    private var master: GameMaster? = null
    private var focus: AudioFocus? = null
    private var buttons: MediaButtons? = null
    private var lastStatus: StatusSnapshot? = null

    /** Set by the car session when the car microphone should be used. */
    @Volatile var carMic: (() -> AudioStream?)? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start(newGame: Boolean = false) {
        if (isRunning) {
            if (!newGame) return
            stop()
        }
        val settings = Settings(app)
        val config = GameConfig(costCapUsd = settings.costCapUsd)
        paused.value = false
        val io = AndroidPlayerIo()
        val gm =
            GameMaster(
                io = io,
                saves = SaveStore(File(app.filesDir, "saves")),
                config = config,
                transcripts = TranscriptStore(File(app.filesDir, "transcripts")),
                narratorFactory = narratorFactory(settings, config),
                tacticsFactory = { dice ->
                    val heuristic = HeuristicTactics(dice)
                    if (settings.jevEndpoint.isNotBlank() && settings.jevKey.isNotBlank()) {
                        JevTacticsAdvisor(JevConfig(settings.jevEndpoint, settings.jevKey), heuristic)
                    } else {
                        heuristic
                    }
                },
                log = { Log.i(TAG, it) },
            )
        master = gm
        _state.value = GameUiState.Running(lastStatus, paused = false)
        GameService.start(app)
        job =
            scope.launch {
                withContext(Dispatchers.Main) {
                    focus = AudioFocus(app, onLoss = ::pause, onGain = ::resume).also { if (!it.request()) pause() }
                    buttons = MediaButtons(app, ::onMediaButton)
                }
                try {
                    gm.run(newGame)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Game crashed", e)
                } finally {
                    withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) { cleanup() }
                }
            }
    }

    private fun narratorFactory(
        settings: Settings,
        config: GameConfig,
    ) = NarratorFactory { onUsage ->
        if (settings.offlineOnly || settings.openAiKey.isBlank()) {
            null
        } else {
            OpenAiNarrator(LlmConfig(settings.openAiKey, settings.model, settings.baseUrl), config.tone, onUsage)
        }
    }

    private fun cleanup() {
        focus?.abandon()
        focus = null
        buttons?.release()
        buttons = null
        GameService.stop(app)
        val status = master?.currentStatus ?: lastStatus
        _state.value =
            if (status?.phase == Phase.VICTORY || status?.phase == Phase.DEFEAT) GameUiState.Finished(status) else GameUiState.Idle
        master = null
    }

    fun stop() {
        master?.stop()
        tts.stop()
        speechInput.cancel()
        job?.cancel()
        job = null
    }

    fun pause() {
        if (!isRunning || paused.value) return
        paused.value = true
        tts.stop()
        speechInput.cancel()
        publish()
    }

    fun resume() {
        if (!paused.value) return
        paused.value = false
        master?.requestRecap()
        publish()
    }

    fun togglePause() = if (paused.value) resume() else pause()

    /** Steering-wheel button: interrupt narration so a player can speak, or resume when paused. */
    private fun onMediaButton() {
        if (paused.value) resume() else tts.stop()
    }

    private fun publish() {
        if (isRunning) _state.value = GameUiState.Running(lastStatus, paused.value)
    }

    private inner class AndroidPlayerIo : PlayerIo {
        override suspend fun say(text: String) {
            paused.first { !it }
            Log.i(TAG, "DM: $text")
            tts.speak(text)
        }

        override suspend fun listen(request: ListenRequest): String? {
            while (true) {
                paused.first { !it }
                val heard = speechInput.listen(request.hints, request.long, carMic)
                if (paused.value) continue // interrupted by a call or navigation: listen again after resume
                Log.i(TAG, "Heard: $heard")
                return heard
            }
        }

        override fun onStatus(status: StatusSnapshot) {
            lastStatus = status
            publish()
        }
    }

    companion object {
        private const val TAG = "GameController"
    }
}
