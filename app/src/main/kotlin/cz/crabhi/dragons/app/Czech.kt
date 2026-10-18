package cz.crabhi.dragons.app

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** Players always get Czech, whatever the phone language is. */
object Czech {
    val locale: Locale = Locale.forLanguageTag("cs-CZ")

    fun wrap(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun resources(context: Context): Resources = wrap(context).resources
}
