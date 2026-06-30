package yupay.turismo.tts

import java.io.File

/** PCM crudo producido por el motor: muestras normalizadas a [-1, 1] mono + frecuencia de muestreo. */
data class TtsAudio(val samples: FloatArray, val sampleRate: Int) {
    val isEmpty: Boolean get() = samples.isEmpty()

    // equals/hashCode por contenido (data class con FloatArray): necesario para comparaciones correctas.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TtsAudio) return false
        return sampleRate == other.sampleRate && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int = 31 * samples.contentHashCode() + sampleRate
}

/** Se lanza cuando el motor nativo no está disponible o el modelo no se pudo cargar. */
class TtsEngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Abstracción del motor TTS. Permite sustituir la implementación (hoy [yupay.turismo.tts.engine.SherpaOnnxTtsEngine])
 * sin tocar [TtsManager]. Solo se ocupa de la SÍNTESIS (texto → PCM); la reproducción (AudioTrack)
 * vive en [TtsManager].
 */
interface TtsEngine {
    /** ¿Hay un modelo cargado y listo para sintetizar? */
    val isReady: Boolean

    /** Frecuencia de muestreo del modelo cargado (0 si no hay modelo). */
    val sampleRate: Int

    /**
     * Carga el modelo desde su carpeta en disco. Operación pesada (bloqueante): llamar fuera del
     * hilo principal. Lanza [TtsEngineException] si falta la librería nativa o el modelo es inválido.
     */
    fun load(modelDir: File, info: TtsModelInfo)

    /**
     * Sintetiza [text] a PCM a la velocidad [speed] (1.0 = normal). Bloqueante.
     * @throws TtsEngineException si no hay modelo cargado o falla la inferencia.
     */
    fun synthesize(text: String, speed: Float): TtsAudio

    /** Libera los recursos nativos del modelo. Idempotente. */
    fun release()
}
