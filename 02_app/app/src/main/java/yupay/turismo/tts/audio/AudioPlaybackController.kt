package yupay.turismo.tts.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yupay.turismo.tts.SupportedLanguage
import yupay.turismo.tts.TtsEngine
import yupay.turismo.tts.download.TtsDownloadRepository
import yupay.turismo.tts.engine.SherpaOnnxTtsEngine

/** Estado observable del reproductor de audio "ligado a página" (tips/mapas/dashboard). */
data class AudioUiState(
    /** Pantalla dueña del audio actual (ver claves en [AudioPlaybackController.prepare]). */
    val ownerKey: String? = null,
    /** Cargando el [MediaPlayer] desde un WAV ya cacheado: spinner neutro (NO "convirtiendo"). */
    val isPreparing: Boolean = false,
    /** Sintetizando el audio por primera vez (no estaba en caché): la UI muestra "Convirtiendo…". */
    val isSynthesizing: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** Hay audio listo para reproducir (voz instalada + WAV preparado). */
    val ready: Boolean = false,
    /**
     * ¿Hay una voz activa instalada para este idioma? Si es `false`, la UI muestra "configura una
     * voz"; si es `true` pero `ready` sigue `false` (síntesis fallida/vacía), muestra "no hay audio
     * del texto". Permite distinguir ambos casos en el reproductor.
     */
    val hasVoice: Boolean = true,
    /** ¿Hay texto que convertir? Si es `false` la UI muestra "No hay texto" (no intenta sintetizar). */
    val hasText: Boolean = true,
)

/**
 * Controlador ÚNICO (singleton en [yupay.turismo.di.ServiceLocator]) de la reproducción de audio
 * con seek y velocidad para tips/mapas/dashboard. A diferencia de [yupay.turismo.tts.TtsManager]
 * (que reproduce PCM en memoria con AudioTrack, sin seek, para el preview de voces), aquí:
 *
 *  1. Se sintetiza UNA vez a velocidad NEUTRA (1.0) y se cachea como WAV ([AudioCache]).
 *  2. Se reproduce con [MediaPlayer] → posición/duración/`seekTo`/velocidad ([PlaybackParams]).
 *  3. La velocidad del perfil se aplica en REPRODUCCIÓN (no se re-sintetiza).
 *
 * "Ligado a página": solo una pantalla es dueña ([AudioUiState.ownerKey]). Pedir otro dueño
 * detiene el anterior; al salir de la pantalla se llama [releaseIfOwner].
 */
class AudioPlaybackController(
    appContext: Context,
    private val repository: TtsDownloadRepository,
    private val engine: TtsEngine = SherpaOnnxTtsEngine(),
) {
    private val appContext = appContext.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(AudioUiState())
    val state: StateFlow<AudioUiState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var prepareJob: Job? = null
    private var tickerJob: Job? = null

    /** Id del modelo cargado en el motor (para no recargar si no cambió). */
    private var loadedModelId: String? = null
    /** Identidad de la última preparación lanzada (owner|idioma|texto), para idempotencia. */
    private var currentKey: String? = null
    /** Velocidad de reproducción del perfil; se aplica al sonar (no en estado pausado). */
    private var pendingSpeed: Float = 1.0f

    init {
        // Pausar al mandar la app a segundo plano (no liberar: al volver se sigue donde estaba).
        Handler(Looper.getMainLooper()).post {
            runCatching {
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onStop(owner: LifecycleOwner) = pauseInternal()
                })
            }
        }
    }

    /**
     * Prepara (sin reproducir) el audio de [text] en [language] para la pantalla [ownerKey].
     * Si la identidad (owner+idioma+texto) no cambió, no hace nada. Si [text] está en blanco,
     * libera y deja el estado vacío (`ready=false`). Síntesis + WAV en IO; si no hay voz
     * instalada, queda `ready=false`.
     */
    fun prepare(ownerKey: String, text: String, language: SupportedLanguage, autoPlay: Boolean = false) {
        val key = "$ownerKey|${language.code}|${text.hashCode()}|${text.length}"
        if (key == currentKey) {
            if (autoPlay) togglePlayPause()
            return
        }
        currentKey = key

        prepareJob?.cancel()
        releasePlayer()

        if (text.isBlank()) {
            // Sin texto que convertir (p.ej. cuenta recién creada, sin insights aún).
            _state.value = AudioUiState(ownerKey = ownerKey, hasText = false)
            return
        }

        // Estado neutro de "cargando" mientras resolvemos voz/caché; NO es "convirtiendo".
        _state.value = AudioUiState(ownerKey = ownerKey, isPreparing = true)
        prepareJob = scope.launch {
            try {
                val model = repository.activeInstalledModel(language)
                if (model == null) {
                    // Sin voz instalada para este idioma: la UI mostrará "configura una voz".
                    _state.value = AudioUiState(ownerKey = ownerKey, ready = false, hasVoice = false)
                    return@launch
                }

                val cacheKey = AudioCache.keyFor(text, language, model.id)
                val cacheFile = AudioCache.fileFor(appContext, cacheKey)
                val cached = withContext(Dispatchers.IO) { cacheFile.exists() && cacheFile.length() > 0L }
                // Registra este audio como el actual del owner y borra el del texto anterior.
                withContext(Dispatchers.IO) { AudioCache.updateOwnerAudio(appContext, ownerKey, cacheKey) }

                // Solo mostramos "Convirtiendo…" cuando hay que sintetizar de verdad. Si el WAV ya
                // está cacheado (al volver a la pantalla) seguimos en "isPreparing" (spinner neutro)
                // y pasamos a los controles en cuanto el MediaPlayer esté listo.
                if (!cached) {
                    _state.value = AudioUiState(ownerKey = ownerKey, isSynthesizing = true)
                }

                // Síntesis (si no está cacheada) + construcción y prepare() del MediaPlayer en IO,
                // para no bloquear el hilo principal con un fichero local.
                val mp = withContext(Dispatchers.IO) {
                    if (!cacheFile.exists() || cacheFile.length() == 0L) {
                        if (loadedModelId != model.id || !engine.isReady) {
                            engine.load(repository.modelDir(model), model)
                            loadedModelId = model.id
                        }
                        val audio = engine.synthesize(text, NEUTRAL_SPEED)
                        if (audio.isEmpty) return@withContext null
                        WavFileWriter.write(cacheFile, audio.samples, audio.sampleRate)
                    }
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        setDataSource(cacheFile.absolutePath)
                        prepare() // fichero local: rápido
                    }
                }
                if (mp == null || !isActive) {
                    mp?.let { runCatching { it.release() } }
                    _state.value = AudioUiState(ownerKey = ownerKey, ready = false)
                    return@launch
                }
                mp.setOnCompletionListener { onCompletion() }
                player = mp
                _state.value = AudioUiState(
                    ownerKey = ownerKey,
                    isPreparing = false,
                    isSynthesizing = false,
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = mp.duration.toLong().coerceAtLeast(0L),
                    ready = true,
                )
                if (autoPlay) play()
            } catch (t: Throwable) {
                Log.e(TAG, "No se pudo preparar el audio ($ownerKey): ${t.message}", t)
                _state.value = AudioUiState(ownerKey = ownerKey, ready = false)
            }
        }
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pauseInternal() else play()
    }

    fun play() {
        val mp = player ?: return
        runCatching {
            // Si terminó, reiniciar desde el principio.
            if (mp.currentPosition >= mp.duration && mp.duration > 0) mp.seekTo(0)
            mp.start()
            applySpeedToPlayer(pendingSpeed)
        }.onFailure { Log.e(TAG, "play() falló: ${it.message}") }
        _state.value = _state.value.copy(isPlaying = true)
        startTicker()
    }

    private fun pauseInternal() {
        val mp = player
        if (mp != null) runCatching { if (mp.isPlaying) mp.pause() }
        tickerJob?.cancel()
        if (_state.value.isPlaying) _state.value = _state.value.copy(isPlaying = false)
    }

    fun seekToFraction(fraction: Float) {
        val mp = player ?: return
        val dur = mp.duration
        if (dur <= 0) return
        val target = (dur * fraction).toInt().coerceIn(0, dur)
        runCatching { mp.seekTo(target) }
        _state.value = _state.value.copy(positionMs = target.toLong())
    }

    fun forward10() = seekByMs(10_000)
    fun rewind10() = seekByMs(-10_000)

    private fun seekByMs(deltaMs: Int) {
        val mp = player ?: return
        val dur = mp.duration
        if (dur <= 0) return
        val target = (mp.currentPosition + deltaMs).coerceIn(0, dur)
        runCatching { mp.seekTo(target) }
        _state.value = _state.value.copy(positionMs = target.toLong())
    }

    /** Velocidad de reproducción del perfil (0.25–2.0). Aplica en vivo solo si está sonando. */
    fun setSpeed(speed: Float) {
        pendingSpeed = speed.coerceIn(0.25f, 2.0f)
        if (_state.value.isPlaying) applySpeedToPlayer(pendingSpeed)
    }

    private fun applySpeedToPlayer(speed: Float) {
        val mp = player ?: return
        runCatching {
            mp.playbackParams = (mp.playbackParams ?: PlaybackParams()).setSpeed(speed)
        }.onFailure { Log.w(TAG, "setSpeed($speed) ignorado: ${it.message}") }
    }

    /** Pausa el audio si la pantalla [ownerKey] es la dueña (p.ej. el mapa al salir del pager). */
    fun pauseIfOwner(ownerKey: String) {
        if (_state.value.ownerKey == ownerKey) pauseInternal()
    }

    /** Libera el audio si la pantalla [ownerKey] es la dueña (onDispose de la pantalla). */
    fun releaseIfOwner(ownerKey: String) {
        if (_state.value.ownerKey == ownerKey) {
            prepareJob?.cancel()
            releasePlayer()
            currentKey = null
            _state.value = AudioUiState()
        }
    }

    /** Libera TODO (logout/reset). El motor también se libera. */
    fun release() {
        prepareJob?.cancel()
        releasePlayer()
        runCatching { engine.release() }
        loadedModelId = null
        currentKey = null
        _state.value = AudioUiState()
    }

    private fun onCompletion() {
        tickerJob?.cancel()
        val dur = _state.value.durationMs
        _state.value = _state.value.copy(isPlaying = false, positionMs = dur)
    }

    private fun releasePlayer() {
        tickerJob?.cancel()
        tickerJob = null
        player?.let { mp -> runCatching { mp.stop() }; runCatching { mp.release() } }
        player = null
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val mp = player ?: break
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)
                _state.value = _state.value.copy(positionMs = pos)
                delay(TICK_MS)
            }
        }
    }

    private companion object {
        const val TAG = "AudioPlaybackCtrl"
        const val NEUTRAL_SPEED = 1.0f
        const val TICK_MS = 200L
    }
}
