package yupay.turismo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yupay.turismo.utils.UiTranslations

@Composable
fun AudioPlayerUI(
    currentTime: Long,
    totalDuration: Long,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onFastForward: () -> Unit,
    onRewind: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    language: String = "Español",
    // Estado del audio para mostrar indicadores en vez de un botón "muerto":
    //  - !hasText → "No hay texto" (aún no hay insights que convertir).
    //  - isSynthesizing → "Convirtiendo texto a audio…" (síntesis real, con spinner).
    //  - isPreparing → "Cargando audio…" (WAV ya cacheado: spinner neutro, sin "convirtiendo").
    //  - !ready && !hasVoice → "Configura una voz en tu perfil".
    //  - !ready && hasVoice → "No hay audio del texto" (síntesis fallida/vacía).
    isPreparing: Boolean = false,
    isSynthesizing: Boolean = false,
    ready: Boolean = true,
    hasVoice: Boolean = true,
    hasText: Boolean = true,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            !hasText -> AudioStatusRow(
                text = UiTranslations.getString(context, "audio_status_no_text", language),
                icon = Icons.Default.SpeakerNotesOff,
            )

            isSynthesizing -> AudioStatusRow(
                text = UiTranslations.getString(context, "audio_status_preparing", language),
                icon = null, // spinner
            )

            isPreparing -> AudioStatusRow(
                text = UiTranslations.getString(context, "audio_status_loading", language),
                icon = null, // spinner neutro (cargando audio cacheado)
            )

            !ready -> AudioStatusRow(
                text = if (!hasVoice) {
                    UiTranslations.getString(context, "audio_btn_no_voice", language)
                } else {
                    UiTranslations.getString(context, "audio_status_no_audio", language)
                },
                icon = Icons.Default.VolumeOff,
            )

            else -> AudioControls(
                currentTime = currentTime,
                totalDuration = totalDuration,
                isPlaying = isPlaying,
                onPlayPauseClick = onPlayPauseClick,
                onSeek = onSeek,
                onFastForward = onFastForward,
                onRewind = onRewind,
                compact = compact,
                language = language,
            )
        }
    }
}

/** Fila de estado del reproductor: spinner (icon == null) o un icono + texto. */
@Composable
private fun AudioStatusRow(text: String, icon: ImageVector?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Controles reales del reproductor (slider + play/pausa + ±10s), solo cuando hay audio listo. */
@Composable
private fun AudioControls(
    currentTime: Long,
    totalDuration: Long,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onFastForward: () -> Unit,
    onRewind: () -> Unit,
    compact: Boolean,
    language: String,
) {
    val context = LocalContext.current
    if (!compact) {
        Slider(
            value = if (totalDuration > 0) currentTime.toFloat() / totalDuration else 0f,
            onValueChange = onSeek,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(currentTime), fontSize = 12.sp)
            Text(text = formatTime(totalDuration), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(onClick = onRewind) {
            Icon(Icons.Default.Replay10, contentDescription = UiTranslations.getString(context, "audio_cd_rewind", language))
        }

        FloatingActionButton(
            onClick = onPlayPauseClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(if (compact) 48.dp else 56.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = UiTranslations.getString(context, if (isPlaying) "audio_cd_pause" else "audio_cd_play", language)
            )
        }

        IconButton(onClick = onFastForward) {
            Icon(Icons.Default.Forward10, contentDescription = UiTranslations.getString(context, "audio_cd_forward", language))
        }
    }

    if (compact) {
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (totalDuration > 0) currentTime.toFloat() / totalDuration else 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(currentTime), fontSize = 10.sp)
            Text(text = formatTime(totalDuration), fontSize = 10.sp)
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
