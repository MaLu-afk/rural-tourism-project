package yupay.turismo.tts

/**
 * Idiomas con soporte de Text-to-Speech (Sherpa-ONNX).
 *
 * - [code] es el código corto usado para organizar los modelos en disco
 *   (`filesDir/tts/models/<code>/<modelId>/`) y para mapear locales.
 * - [settingsValues] son las cadenas EXACTAS con las que el resto de la app guarda el idioma
 *   en `AppSettings.language` (ver [yupay.turismo.ui.features.onboarding.LanguageSelectionScreen]
 *   y [yupay.turismo.utils.UiTranslations]): "Español", "Inglés", "Portugués", "Quechua".
 *   Se incluyen alias (p.ej. "English", "Runa Simi") por robustez.
 */
enum class SupportedLanguage(
    val code: String,
    val settingsValues: List<String>,
) {
    SPANISH("es", listOf("Español", "Espanol", "Spanish")),
    ENGLISH("en", listOf("Inglés", "Ingles", "English")),
    PORTUGUESE("pt", listOf("Portugués", "Portugues", "Portuguese", "Português")),
    // Quechua Cusco-Collao (ISO 639-3: quz). El locale Android es "qu" (genérico) porque
    // Android no distingue la variante; el código del modelo MMS sí es "quz".
    QUECHUA("quz", listOf("Quechua", "Runa Simi", "Runasimi", "Qhichwa"));

    companion object {
        /**
         * Convierte la cadena de idioma persistida en `AppSettings.language` al enum.
         * Devuelve [SPANISH] por defecto (igual que el resto de la app, que asume español
         * cuando el valor no coincide con inglés/portugués/quechua).
         */
        fun fromSettings(language: String?): SupportedLanguage {
            if (language == null) return SPANISH
            val normalized = language.trim()
            return entries.firstOrNull { lang ->
                lang.settingsValues.any { it.equals(normalized, ignoreCase = true) }
            } ?: SPANISH
        }
    }
}
