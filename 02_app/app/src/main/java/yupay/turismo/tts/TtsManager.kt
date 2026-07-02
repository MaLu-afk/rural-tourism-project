package yupay.turismo.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yupay.turismo.tts.download.TtsDownloadRepository
import yupay.turismo.tts.engine.SherpaOnnxTtsEngine
import kotlin.coroutines.coroutineContext

/** Fase de la reproducción TTS actual. */
enum class TtsPhase { IDLE, PREPARING, SYNTHESIZING, PLAYING }

/**
 * Estado observable de la reproducción. [language] y [text] identifican la "utterance" en curso,
 * para que un [yupay.turismo.ui.components.AudioButton] concreto sepa si es ÉL quien está sonando.
 */
data class TtsPlaybackState(
    val phase: TtsPhase = TtsPhase.IDLE,
    val language: SupportedLanguage? = null,
    val text: String? = null,
    /** Id del modelo que está sonando (para distinguir voces del mismo idioma en el preview). */
    val modelId: String? = null,
) {
    val isBusy: Boolean get() = phase != TtsPhase.IDLE
}

/**
 * Orquestador único de TTS (singleton en [yupay.turismo.di.ServiceLocator]).
 *
 * Carga perezosamente el modelo ACTIVO del idioma pedido (delegando la síntesis en [TtsEngine] —
 * hoy [SherpaOnnxTtsEngine]) y reproduce el PCM con [AudioTrack] en formato float. Solo suena una
 * cosa a la vez: pedir una nueva reproducción detiene la anterior.
 */
class TtsManager(
    private val appContext: Context,
    private val repository: TtsDownloadRepository,
    private val engine: TtsEngine = SherpaOnnxTtsEngine(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _playback = MutableStateFlow(TtsPlaybackState())
    val playback: StateFlow<TtsPlaybackState> = _playback.asStateFlow()

    private var currentJob: Job? = null
    @Volatile private var currentTrack: AudioTrack? = null
    /** Token monotónico: evita que el `finally` de una utterance vieja pise el estado de una nueva. */
    @Volatile private var playToken: Long = 0

    /** Id del modelo cargado actualmente en el motor (para no recargar si no cambió). */
    private var loadedModelId: String? = null

    /**
     * Sintetiza y reproduce [text] en [language] a velocidad [speed]. No bloquea: actualiza
     * [playback]. Si no hay voz configurada/instalada para ese idioma, no hace nada (log) —
     * el flujo de UI garantiza que activo = descargado, así que esto solo ocurre en casos límite.
     */
    fun speak(text: String, language: SupportedLanguage, speed: Float = 1.0f) =
        speakWith(text, language, speed) { repository.activeInstalledModel(language) }

    /**
     * Previsualiza [text] con un modelo CONCRETO (ya descargado), aunque no sea el activo del
     * idioma. Lo usa la pantalla de "Modelos de voz" para escuchar cada voz antes de activarla.
     */
    fun speakWithModel(text: String, info: TtsModelInfo, speed: Float = 1.0f) =
        speakWith(text, info.language, speed) { info }

    private fun speakWith(
        text: String,
        language: SupportedLanguage,
        speed: Float,
        resolveModel: suspend () -> TtsModelInfo?,
    ) {
        if (text.isBlank()) return
        val token = ++playToken
        stopInternal(resetState = false) // detiene lo anterior sin tocar el estado (lo fijamos abajo)
        _playback.value = TtsPlaybackState(TtsPhase.PREPARING, language, text)
        currentJob = scope.launch {
            try {
                val model = resolveModel()
                if (model == null || !ensureModelLoaded(model)) {
                    Log.w(TAG, "No hay voz instalada para $language; no se reproduce.")
                    return@launch
                }
                _playback.value = TtsPlaybackState(TtsPhase.SYNTHESIZING, language, text, model.id)
                val audio = withContext(Dispatchers.IO) { engine.synthesize(text, speed) }
                if (audio.isEmpty) return@launch
                coroutineContext.ensureActive()
                _playback.value = TtsPlaybackState(TtsPhase.PLAYING, language, text, model.id)
                playPcm(audio)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                // "Error silencioso en logs": no rompemos la UI si el motor falla.
                Log.e(TAG, "Fallo reproduciendo TTS ($language): ${t.message}", t)
            } finally {
                if (token == playToken) _playback.value = TtsPlaybackState()
            }
        }
    }

    /** Detiene la reproducción/síntesis actual. */
    fun stop() {
        stopInternal(resetState = true)
    }

    private fun stopInternal(resetState: Boolean) {
        currentJob?.cancel()
        currentJob = null
        currentTrack?.let { track ->
            runCatching {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause(); track.flush()
                }
            }
        }
        if (resetState) {
            playToken++ // invalida cualquier finally pendiente
            _playback.value = TtsPlaybackState()
        }
    }

    /** Libera todos los recursos (llamar en un reset/cierre de la app). */
    fun release() {
        stop()
        runCatching { engine.release() }
        loadedModelId = null
    }

    /** Carga en el motor un modelo concreto (si hace falta). Devuelve false si no se pudo cargar. */
    private suspend fun ensureModelLoaded(info: TtsModelInfo): Boolean {
        if (loadedModelId == info.id && engine.isReady) return true
        return try {
            withContext(Dispatchers.IO) { engine.load(repository.modelDir(info), info) }
            loadedModelId = info.id
            true
        } catch (t: Throwable) {
            loadedModelId = null
            Log.e(TAG, "No se pudo cargar el modelo '${info.id}': ${t.message}", t)
            false
        }
    }

    /** Reproduce PCM float mono con AudioTrack en modo stream; cancelable y con espera al final. */
    private suspend fun playPcm(audio: TtsAudio) = withContext(Dispatchers.IO) {
        val sampleRate = audio.sampleRate
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(sampleRate * BYTES_PER_FLOAT) // ~1 s de margen

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        currentTrack = track

        try {
            track.play()
            val samples = audio.samples
            var offset = 0
            while (offset < samples.size) {
                coroutineContext.ensureActive()
                val toWrite = minOf(WRITE_CHUNK, samples.size - offset)
                val written = track.write(samples, offset, toWrite, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    Log.w(TAG, "AudioTrack.write devolvió $written; abortando reproducción.")
                    break
                }
                offset += written
            }
            // Drenado: dejar que se reproduzca lo que queda en el buffer interno.
            track.stop()
            val totalFrames = samples.size // mono → 1 frame por sample
            while (totalFrames > 0 && track.playbackHeadPosition < totalFrames) {
                coroutineContext.ensureActive()
                delay(20)
            }
        } finally {
            runCatching {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause(); track.flush(); track.stop()
                }
            }
            runCatching { track.release() }
            if (currentTrack === track) currentTrack = null
        }
    }

    private companion object {
        const val TAG = "TtsManager"
        const val WRITE_CHUNK = 4096
        const val BYTES_PER_FLOAT = 4
    }
}
