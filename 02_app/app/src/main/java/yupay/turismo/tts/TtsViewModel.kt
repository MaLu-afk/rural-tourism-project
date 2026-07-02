package yupay.turismo.tts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import yupay.turismo.di.ServiceLocator
import yupay.turismo.tts.download.TtsDownloadRepository

/**
 * ViewModel del módulo TTS. Sigue la convención del proyecto: [AndroidViewModel] que obtiene los
 * singletons del [ServiceLocator] (no hay Hilt). Se instancia con `viewModel<TtsViewModel>()`.
 */
class TtsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TtsDownloadRepository = ServiceLocator.ttsDownloadRepository
    private val manager: TtsManager = ServiceLocator.ttsManager

    /** Estado de reproducción (para AudioButton y feedback en la UI). */
    val playback: StateFlow<TtsPlaybackState> = manager.playback

    /** Conectividad a internet (para deshabilitar "Descargar"/"Reanudar" sin red). */
    val isOnline: StateFlow<Boolean> = ServiceLocator.networkMonitor.isOnline

    /** Espacio liberado (bytes) tras eliminar un modelo; la UI lo muestra como mensaje. */
    private val _freedSpace = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 1)
    val freedSpace: SharedFlow<Long> = _freedSpace.asSharedFlow()

    /** Catálogo de modelos de un idioma (reacciona al idioma activo del perfil). */
    fun catalog(language: SupportedLanguage): List<TtsModelInfo> =
        TtsModelCatalog.catalogFor(language)

    /** Tamaño del modelo más pesado del idioma (para la barra comparativa de las tarjetas). */
    fun maxSizeBytes(language: SupportedLanguage): Long =
        TtsModelCatalog.maxSizeBytes(language)

    /** StateFlow del estado de un modelo concreto (requisito 4). */
    fun state(info: TtsModelInfo): StateFlow<TtsModelState> = repository.stateOf(info)

    /** Id del modelo activo del idioma (null = "Sin voz configurada"). */
    fun activeModelId(language: SupportedLanguage): Flow<String?> =
        repository.activeModelId(language)

    // ───────── Acciones ─────────
    fun download(info: TtsModelInfo) = repository.download(info)

    /** Pausa la descarga guardando el avance actual (0–100) como checkpoint. */
    fun pause(info: TtsModelInfo, progressPct: Int) = repository.pause(info.id, progressPct)

    /** Reanuda una descarga pausada (continúa desde el checkpoint). */
    fun resume(info: TtsModelInfo) = repository.resume(info)

    /** Anula la descarga y borra los parciales. */
    fun cancel(info: TtsModelInfo) = repository.cancel(info.id)

    fun activate(info: TtsModelInfo) {
        viewModelScope.launch { repository.setActive(info) }
    }

    fun delete(info: TtsModelInfo) {
        viewModelScope.launch {
            val freed = repository.delete(info)
            _freedSpace.emit(freed)
        }
    }

    /** Previsualiza/reproduce un texto con la voz activa del idioma. */
    fun play(text: String, language: SupportedLanguage, speed: Float = 1.0f) =
        manager.speak(text, language, speed)

    /** Previsualiza un texto con un modelo CONCRETO (descargado), para la pantalla de modelos de voz. */
    fun previewModel(text: String, info: TtsModelInfo, speed: Float = 1.0f) =
        manager.speakWithModel(text, info, speed)

    fun stop() = manager.stop()
}
