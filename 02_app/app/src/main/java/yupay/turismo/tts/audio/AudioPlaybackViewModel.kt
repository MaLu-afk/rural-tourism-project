package yupay.turismo.tts.audio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import yupay.turismo.di.ServiceLocator
import yupay.turismo.tts.SupportedLanguage

/**
 * ViewModel fino del reproductor "ligado a página". Sigue la convención del proyecto
 * ([AndroidViewModel] + [ServiceLocator], sin Hilt); se instancia con `viewModel<AudioPlaybackViewModel>()`.
 * Solo delega en el singleton [AudioPlaybackController].
 */
class AudioPlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val controller: AudioPlaybackController = ServiceLocator.audioPlaybackController

    val state: StateFlow<AudioUiState> = controller.state

    fun prepare(ownerKey: String, text: String, language: SupportedLanguage) =
        controller.prepare(ownerKey, text, language)

    /** Prepara y arranca la reproducción en cuanto esté lista (botón único del dashboard). */
    fun prepareAndPlay(ownerKey: String, text: String, language: SupportedLanguage) =
        controller.prepare(ownerKey, text, language, autoPlay = true)

    fun togglePlayPause() = controller.togglePlayPause()
    fun play() = controller.play()
    fun seekToFraction(fraction: Float) = controller.seekToFraction(fraction)
    fun forward10() = controller.forward10()
    fun rewind10() = controller.rewind10()
    fun setSpeed(speed: Float) = controller.setSpeed(speed)
    fun pauseIfOwner(ownerKey: String) = controller.pauseIfOwner(ownerKey)
    fun releaseIfOwner(ownerKey: String) = controller.releaseIfOwner(ownerKey)
}
