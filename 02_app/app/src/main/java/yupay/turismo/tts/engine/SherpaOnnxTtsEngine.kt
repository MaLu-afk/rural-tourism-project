package yupay.turismo.tts.engine

import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import yupay.turismo.tts.TtsAudio
import yupay.turismo.tts.TtsEngine
import yupay.turismo.tts.TtsEngineException
import yupay.turismo.tts.TtsEngineType
import yupay.turismo.tts.TtsModelInfo
import java.io.File

/**
 * Motor TTS ÚNICO basado en Sherpa-ONNX (no se usa el TTS nativo de Android).
 *
 * Soporta los dos sabores VITS del catálogo:
 *  - Piper ([TtsEngineType.PIPER]): necesita la carpeta `espeak-ng-data/` (dataDir) para fonemizar.
 *  - MMS  ([TtsEngineType.MMS]):   solo `model.onnx` + `tokens.txt`, sin dataDir.
 *
 * No localiza los ficheros por nombre fijo: busca el único `*.onnx`, el `tokens.txt` y la carpeta
 * `espeak-ng-data` dentro del directorio del modelo, así funciona aunque el .onnx tenga el nombre
 * del locutor (p.ej. `es_ES-davefx-medium.onnx`).
 */
class SherpaOnnxTtsEngine : TtsEngine {

    private var tts: OfflineTts? = null
    private var loadedSampleRate: Int = 0

    override val isReady: Boolean get() = tts != null
    override val sampleRate: Int get() = loadedSampleRate

    override fun load(modelDir: File, info: TtsModelInfo) {
        release()

        if (!modelDir.isDirectory) {
            throw TtsEngineException("El directorio del modelo no existe: ${modelDir.absolutePath}")
        }

        val onnx = modelDir.walkTopDown().firstOrNull { it.isFile && it.extension == "onnx" }
            ?: throw TtsEngineException("No se encontró ningún .onnx en ${modelDir.absolutePath}")
        val tokens = modelDir.walkTopDown().firstOrNull { it.isFile && it.name == "tokens.txt" }
            ?: throw TtsEngineException("No se encontró tokens.txt en ${modelDir.absolutePath}")
        val espeakDir = if (info.engine == TtsEngineType.PIPER) {
            modelDir.walkTopDown().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }
                ?: throw TtsEngineException("Modelo Piper sin espeak-ng-data en ${modelDir.absolutePath}")
        } else null

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = onnx.absolutePath,
                    tokens = tokens.absolutePath,
                    // Piper requiere dataDir = espeak-ng-data; MMS lo deja vacío.
                    dataDir = espeakDir?.absolutePath ?: "",
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        )

        try {
            // Instanciar OfflineTts dispara System.loadLibrary("sherpa-onnx-jni"). Si los .so no
            // están en jniLibs, salta UnsatisfiedLinkError aquí (lo convertimos en excepción de dominio).
            val engine = OfflineTts(config = config)
            loadedSampleRate = engine.sampleRate()
            tts = engine
            Log.i(TAG, "Modelo '${info.id}' cargado (sampleRate=$loadedSampleRate, engine=${info.engine}).")
        } catch (t: Throwable) {
            loadedSampleRate = 0
            tts = null
            throw TtsEngineException(
                "No se pudo inicializar Sherpa-ONNX para '${info.id}'. " +
                    "¿Están los .so en app/src/main/jniLibs/? Detalle: ${t.message}",
                t,
            )
        }
    }

    override fun synthesize(text: String, speed: Float): TtsAudio {
        val engine = tts ?: throw TtsEngineException("No hay modelo cargado.")
        if (text.isBlank()) return TtsAudio(FloatArray(0), loadedSampleRate)
        return try {
            // sid = 0: nuestras voces son de un solo locutor. speed > 1 = más rápido.
            val generated = engine.generate(text = text, sid = 0, speed = speed.coerceIn(0.25f, 2.0f))
            TtsAudio(generated.samples, generated.sampleRate)
        } catch (t: Throwable) {
            throw TtsEngineException("Falló la síntesis: ${t.message}", t)
        }
    }

    override fun release() {
        tts?.let {
            try {
                it.release()
            } catch (t: Throwable) {
                Log.w(TAG, "Error liberando OfflineTts", t)
            }
        }
        tts = null
        loadedSampleRate = 0
    }

    private companion object {
        const val TAG = "SherpaOnnxTtsEngine"
    }
}
