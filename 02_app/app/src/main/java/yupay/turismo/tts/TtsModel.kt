package yupay.turismo.tts

/** Calidad/peso del modelo. Mapea a la variante cuantizada (int8 = ligero) vs completa. */
enum class ModelQuality {
    /** Menor tamaño, menor calidad. Ideal para ahorrar datos/almacenamiento (variante int8). */
    LIGHT,
    /** Mayor tamaño, mejor calidad (modelo completo fp32). */
    HIGH,
}

/** Género de la voz. NEUTRAL para modelos de un solo locutor sin género definido (p.ej. MMS). */
enum class VoiceGender { MALE, FEMALE, NEUTRAL }

/** Tipo de motor VITS, que determina cómo se configura [com.k2fsa.sherpa.onnx.OfflineTts]. */
enum class TtsEngineType {
    /** Voces Piper: requieren la carpeta `espeak-ng-data/` (dataDir) para la fonemización. */
    PIPER,
    /** Voces MMS (Facebook): solo `model.onnx` + `tokens.txt`, sin espeak-ng-data. */
    MMS,
}

/** Un fichero remoto individual (para modelos que NO vienen empaquetados en .tar.bz2). */
data class RemoteFile(
    val url: String,
    /** Ruta relativa dentro de la carpeta del modelo, p.ej. "model.onnx" o "tokens.txt". */
    val relativePath: String,
    /** SHA-256 esperado en hex minúsculas, o null si no hay hash publicado (verificación blanda). */
    val sha256: String? = null,
)

/**
 * Origen de descarga del modelo. Hay dos formas en el ecosistema Sherpa-ONNX:
 *  - [Archive]: un único `.tar.bz2` (bzip2 + tar) que se descomprime; así se publican las voces
 *    Piper en el release oficial `tts-models` (incluyen `espeak-ng-data/`).
 *  - [Files]: ficheros sueltos (`model.onnx`, `tokens.txt`); así está la conversión ONNX de la
 *    voz MMS de quechua (no existe en el release oficial, se usa una conversión comunitaria).
 */
sealed interface ModelSource {
    data class Archive(val url: String, val sha256: String) : ModelSource
    data class Files(val files: List<RemoteFile>) : ModelSource
}

/**
 * Metadatos de un modelo de voz del catálogo. Es la fuente de verdad para la UI (tarjetas) y
 * para la descarga/activación. Todos los valores de [sizeBytes]/SHA-256 son REALES, obtenidos de
 * la API de releases de GitHub (k2-fsa/sherpa-onnx) y de la API de HuggingFace; no hay valores
 * inventados.
 */
data class TtsModelInfo(
    /** Identificador único y estable (también es el nombre de carpeta en disco). */
    val id: String,
    val language: SupportedLanguage,
    /** Nombre legible para mostrar (locutor + variante regional). */
    val displayName: String,
    val gender: VoiceGender,
    val quality: ModelQuality,
    /** Tamaño REAL de la descarga en bytes (del .tar.bz2 o suma de ficheros). */
    val sizeBytes: Long,
    val engine: TtsEngineType,
    val source: ModelSource,
    /**
     * Si el modelo existe realmente en Sherpa-ONNX para ese idioma/género/calidad. Siempre true
     * en el catálogo actual (solo incluimos modelos reales), pero el campo permite documentar
     * huecos sin romper la UI.
     */
    val available: Boolean = true,
    /** Nota de documentación (p.ej. acento regional, o por qué no hay otra variante). */
    val note: String? = null,
) {
    /** Tamaño en MB para mostrar en la UI. */
    val sizeMb: Double get() = sizeBytes / 1024.0 / 1024.0
}
