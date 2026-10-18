package cz.crabhi.dragons.app

import android.content.Context
import androidx.core.content.edit
import cz.crabhi.dragons.engine.LlmConfig

/** Phone-side settings. Keys entered here never leave the phone except in API calls. */
class Settings(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var openAiKey: String
        get() = prefs.getString("openai_key", null) ?: BuildConfig.OPENAI_API_KEY
        set(value) = prefs.edit { putString("openai_key", value.trim()) }

    var model: String
        get() = prefs.getString("model", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.OPENAI_MODEL.ifBlank { LlmConfig.DEFAULT_MODEL }
        set(value) = prefs.edit { putString("model", value.trim()) }

    var baseUrl: String
        get() = prefs.getString("base_url", null)?.takeIf { it.isNotBlank() } ?: "https://api.openai.com/v1"
        set(value) = prefs.edit { putString("base_url", value.trim()) }

    var jevEndpoint: String
        get() = prefs.getString("jev_endpoint", null) ?: BuildConfig.JEV_ENDPOINT
        set(value) = prefs.edit { putString("jev_endpoint", value.trim()) }

    var jevKey: String
        get() = prefs.getString("jev_key", null) ?: BuildConfig.JEV_API_KEY
        set(value) = prefs.edit { putString("jev_key", value.trim()) }

    var costCapUsd: Double
        get() = prefs.getFloat("cost_cap", DEFAULT_COST_CAP).toDouble()
        set(value) = prefs.edit { putFloat("cost_cap", value.toFloat()) }

    var offlineOnly: Boolean
        get() = prefs.getBoolean("offline", false)
        set(value) = prefs.edit { putBoolean("offline", value) }

    var useCarMic: Boolean
        get() = prefs.getBoolean("car_mic", false)
        set(value) = prefs.edit { putBoolean("car_mic", value) }

    companion object {
        const val DEFAULT_COST_CAP = 3f
    }
}
