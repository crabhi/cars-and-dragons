package cz.crabhi.dragons.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Phone-side setup and diagnostics: API keys, microphone permission, Czech voice and recognition
 * checks, the latency probe (M0) and a way to play on the phone without a car. Never used while driving.
 */
class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settings: Settings
    private lateinit var log: TextView
    private lateinit var gameStatus: TextView
    private val controller get() = DragonsApp.controller(this)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Czech.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        title = getString(R.string.settings_title)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
            }

        fun field(
            label: Int,
            value: String,
            secret: Boolean = false,
            number: Boolean = false,
        ): EditText {
            root.addView(TextView(this).apply { setText(label) })
            return EditText(this).apply {
                setText(value)
                inputType =
                    when {
                        secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        number -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    }
                isSingleLine = true
                root.addView(this)
            }
        }

        fun button(
            label: Int,
            action: () -> Unit,
        ) = Button(this).apply {
            setText(label)
            setOnClickListener { action() }
            root.addView(this)
        }

        gameStatus = TextView(this).apply { textSize = 16f }
        root.addView(gameStatus)

        val key = field(R.string.settings_openai_key, settings.openAiKey, secret = true)
        val model = field(R.string.settings_model, settings.model)
        val baseUrl = field(R.string.settings_base_url, settings.baseUrl)
        val jevEndpoint = field(R.string.settings_jev_endpoint, settings.jevEndpoint)
        val jevKey = field(R.string.settings_jev_key, settings.jevKey, secret = true)
        val cap = field(R.string.settings_cost_cap, settings.costCapUsd.toString(), number = true)
        val offline =
            CheckBox(this).apply {
                setText(R.string.settings_offline)
                isChecked = settings.offlineOnly
                root.addView(this)
            }
        val carMic =
            CheckBox(this).apply {
                setText(R.string.settings_car_mic)
                isChecked = settings.useCarMic
                root.addView(this)
            }
        button(R.string.settings_save) {
            settings.openAiKey = key.text.toString()
            settings.model = model.text.toString()
            settings.baseUrl = baseUrl.text.toString()
            settings.jevEndpoint = jevEndpoint.text.toString()
            settings.jevKey = jevKey.text.toString()
            settings.costCapUsd = cap.text.toString().replace(',', '.').toDoubleOrNull() ?: Settings.DEFAULT_COST_CAP.toDouble()
            settings.offlineOnly = offline.isChecked
            settings.useCarMic = carMic.isChecked
            say(getString(R.string.settings_saved))
        }
        button(R.string.action_grant_mic) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        button(R.string.action_check_voice) { checkSpeech() }
        button(R.string.action_install_voice) {
            runCatching { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
        }
        button(R.string.action_latency) { measureLatency() }
        button(R.string.action_start) { controller.start(newGame = false) }
        button(R.string.action_new_game) { controller.start(newGame = true) }
        button(R.string.action_stop) { controller.stop() }
        root.addView(TextView(this).apply { setText(R.string.sideload_hint) })
        log =
            TextView(this).apply {
                setTextIsSelectable(true)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
        root.addView(log)
        setContentView(ScrollView(this).apply { addView(root) })

        scope.launch {
            controller.state.collect { state ->
                gameStatus.text =
                    when (state) {
                        GameUiState.Idle -> "—"
                        is GameUiState.Finished -> getString(R.string.car_game_over)
                        is GameUiState.Running -> {
                            val s = state.status
                            val rows =
                                s?.rows?.joinToString("\n") { r ->
                                    val hp = resources.getQuantityString(R.plurals.hp_count, r.hp.coerceAtLeast(0), r.hp, r.maxHp)
                                    (if (r.current) "▶ " else "") + "${r.label}: $hp, ${r.status}"
                                } ?: ""
                            listOfNotNull(
                                s?.title,
                                if (state.paused) getString(R.string.car_paused) else null,
                                rows,
                                s?.enemies?.let { getString(R.string.car_enemies, it) },
                            ).joinToString("\n")
                        }
                    }
            }
        }
        checkSpeech()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) say(getString(R.string.mic_granted))
    }

    private fun say(line: String) {
        log.append(line + "\n")
    }

    private fun checkSpeech() {
        scope.launch {
            say(getString(if (controller.tts.hasOfflineCzechVoice()) R.string.tts_ok else R.string.tts_missing))
            say(getString(if (controller.speechInput.onDeviceAvailable) R.string.asr_on_device_ok else R.string.asr_on_device_missing))
            controller.speechInput.checkCzechOnDevice { runOnUiThread { say(it) } }
        }
    }

    /** M0 latency probe: end of the player's speech until the stub answer starts playing. */
    private fun measureLatency() {
        if (controller.isRunning) controller.stop()
        scope.launch {
            say(getString(R.string.latency_speak_now))
            var speechEnd = 0L
            var ttsStart = 0L
            controller.speechInput.onEndOfSpeech = { speechEnd = it }
            controller.tts.onUtteranceStart = { ttsStart = it }
            val heard = controller.speechInput.listen(emptyList(), long = false)
            controller.tts.speak("Rozumím: ${heard ?: "nic"}.")
            controller.speechInput.onEndOfSpeech = null
            controller.tts.onUtteranceStart = null
            if (speechEnd > 0 && ttsStart > 0) say(getString(R.string.latency_result, ttsStart - speechEnd))
            say("„${heard ?: "—"}“")
        }
    }
}
