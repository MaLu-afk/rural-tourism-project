package yupay.turismo.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import yupay.turismo.tts.SupportedLanguage
import yupay.turismo.tts.TtsPhase
import yupay.turismo.tts.TtsViewModel
import yupay.turismo.utils.UiTranslations

/**
 * Botón de audio reutilizable (requisito 6). Lee la voz activa del [language] y el estado de
 * reproducción del [TtsViewModel]:
 *  - Sin voz activa para el idioma → ícono deshabilitado con tooltip "Configura una voz en tu perfil".
 *  - Listo → ícono ▶ que sintetiza y reproduce [text].
 *  - Durante la síntesis → [CircularProgressIndicator].
 *  - Reproduciendo → ícono ⏹ para detener.
 *
 * El caso "activo pero no descargado" no debería ocurrir (el flujo garantiza activo = descargado);
 * si ocurriera, [yupay.turismo.tts.TtsManager] lo trata con error silencioso en logs y este botón
 * simplemente no producirá sonido.
 *
 * @param uiLanguage idioma de la UI para el texto del tooltip (cadena del perfil, p.ej. "Español").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioButton(
    text: String,
    language: SupportedLanguage,
    modifier: Modifier = Modifier,
    speed: Float = 1.0f,
    uiLanguage: String = "Español",
    viewModel: TtsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activeModelId by viewModel.activeModelId(language).collectAsState(initial = null)
    val playback by viewModel.playback.collectAsState()

    val hasVoice = activeModelId != null
    // ¿Es ESTE botón el que está sonando/sintetizando? (mismo idioma y mismo texto).
    val isThisUtterance = playback.language == language && playback.text == text
    val isSynthesizing = isThisUtterance &&
        (playback.phase == TtsPhase.PREPARING || playback.phase == TtsPhase.SYNTHESIZING)
    val isPlaying = isThisUtterance && playback.phase == TtsPhase.PLAYING

    when {
        !hasVoice -> {
            val tooltipState = rememberTooltipState()
            val scope = rememberCoroutineScope()
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip {
                        Text(UiTranslations.getString(context, "audio_btn_no_voice", uiLanguage))
                    }
                },
                state = tooltipState,
            ) {
                IconButton(onClick = { scope.launch { tooltipState.show() } }, modifier = modifier) {
                    Icon(
                        imageVector = Icons.Default.VolumeOff,
                        contentDescription = UiTranslations.getString(context, "audio_btn_no_voice", uiLanguage),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }
        }

        isSynthesizing -> {
            IconButton(onClick = { viewModel.stop() }, modifier = modifier) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        isPlaying -> {
            IconButton(onClick = { viewModel.stop() }, modifier = modifier) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = UiTranslations.getString(context, "audio_btn_stop", uiLanguage),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        else -> {
            IconButton(
                onClick = { viewModel.play(text, language, speed) },
                modifier = modifier,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = UiTranslations.getString(context, "audio_btn_play", uiLanguage),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Conveniencia: acepta la cadena de idioma que ya usa la app ("Español", "Inglés", ...) y la
 * mapea a [SupportedLanguage]. Útil para pantallas existentes que manejan `language: String`.
 */
@Composable
fun AudioButton(
    text: String,
    uiLanguageString: String,
    modifier: Modifier = Modifier,
    speed: Float = 1.0f,
) {
    AudioButton(
        text = text,
        language = SupportedLanguage.fromSettings(uiLanguageString),
        modifier = modifier,
        speed = speed,
        uiLanguage = uiLanguageString,
    )
}
