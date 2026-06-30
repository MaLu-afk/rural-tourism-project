package yupay.turismo.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object UiTranslations {
    
    fun getString(key: String, language: String, vararg args: Any): String {
        // This is a placeholder for when we have a Context.
        // In Compose, we should prefer stringResource(id) or Context.getString(id)
        return key // Should not be called without Context in the new system
    }

    fun getString(context: Context, key: String, language: String, vararg args: Any): String {
        val locale = when (language) {
            "Quechua" -> Locale("qu")
            "Inglés" -> Locale("en")
            "Portugués" -> Locale("pt")
            else -> Locale("es")
        }

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        val localizedContext = context.createConfigurationContext(config)
        
        val resId = context.resources.getIdentifier(key, "string", context.packageName)
        if (resId == 0) return key
        
        return try {
            localizedContext.getString(resId, *args)
        } catch (e: Exception) {
            localizedContext.getString(resId)
        }
    }

    fun translateService(service: String, language: String, context: Context): String {
        val key = when (service) {
            "Hospedaje" -> "service_lodging"
            "Alimentación" -> "service_food"
            "Artesanía" -> "service_handicraft"
            "Varios" -> "service_others"
            else -> return service
        }
        return getString(context, key, language)
    }

    fun translateServicesList(services: String, language: String, context: Context): String {
        if (services.isEmpty()) return ""
        return services.split(", ").joinToString(", ") { translateService(it, language, context) }
    }
}
