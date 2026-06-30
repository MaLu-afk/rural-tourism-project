package yupay.turismo.ui.features.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yupay.turismo.tts.ModelQuality
import yupay.turismo.tts.SupportedLanguage
import yupay.turismo.tts.TtsModelInfo
import yupay.turismo.tts.TtsModelState
import yupay.turismo.tts.TtsPhase
import yupay.turismo.tts.TtsViewModel
import yupay.turismo.tts.VoiceGender
import yupay.turismo.utils.UiTranslations

private val LightGreen = Color(0xFF2E7D32)
private val HighBlue = Color(0xFF1565C0)

/** Frase de prueba "hola, esta es mi voz" por idioma (preview de cada modelo descargado). */
internal fun voiceSampleText(language: SupportedLanguage): String = when (language) {
    SupportedLanguage.SPANISH -> "Hola, esta es mi voz."
    SupportedLanguage.ENGLISH -> "Hi, this is my voice."
    SupportedLanguage.PORTUGUESE -> "Olá, esta é a minha voz."
    SupportedLanguage.QUECHUA -> "Allinllachu, kayqa ñuqaq kunkaymi."
}

/**
 * Tarjeta de un modelo de voz (descarga/activación/borrado + preview). Reutilizada por
 * [VoiceModelsScreen]. El preview solo está disponible cuando el modelo ya está descargado
 * (Sherpa-ONNX no puede sintetizar sin el `.onnx`).
 *
 * @param voiceSpeed velocidad del perfil, aplicada también al audio de prueba.
 */
@Composable
internal fun ModelCard(
    info: TtsModelInfo,
    maxSizeBytes: Long,
    uiLanguage: String,
    voiceSpeed: Float,
    viewModel: TtsViewModel,
) {
    val context = LocalContext.current
    val state by viewModel.state(info).collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isActive = state is TtsModelState.Active

    val borderColor = if (isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Fila 1: género + nombre + preview (si está descargado) + chip de calidad.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = genderIcon(info.gender), fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = info.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (state.isReady) {
                    ModelPreviewButton(info, voiceSpeed, uiLanguage, viewModel)
                    Spacer(Modifier.width(4.dp))
                }
                QualityChip(info.quality, uiLanguage)
            }

            Spacer(Modifier.height(8.dp))

            // Fila 2: tamaño en MB + barra comparativa entre modelos del mismo idioma.
            val fraction = if (maxSizeBytes > 0) {
                (info.sizeBytes.toFloat() / maxSizeBytes).coerceIn(0.05f, 1f)
            } else 1f
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "%.1f MB".format(info.sizeMb),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Fila 3: acciones según el estado.
            ModelActions(
                info = info,
                state = state,
                uiLanguage = uiLanguage,
                isOnline = isOnline,
                onDownload = { viewModel.download(info) },
                onPause = { pct -> viewModel.pause(info, pct) },
                onResume = { viewModel.resume(info) },
                onCancel = { viewModel.cancel(info) },
                onActivate = { viewModel.activate(info) },
                onDelete = { viewModel.delete(info) },
            )

            if (info.note != null && state is TtsModelState.NotDownloaded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = info.note,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** Botón ▶/⏹ que reproduce "hola, esta es mi voz" con ESTE modelo (ya descargado). */
@Composable
internal fun ModelPreviewButton(
    info: TtsModelInfo,
    voiceSpeed: Float,
    uiLanguage: String,
    viewModel: TtsViewModel,
) {
    val context = LocalContext.current
    val playback by viewModel.playback.collectAsState()
    val isThis = playback.modelId == info.id && playback.isBusy
    val isSynthesizing = isThis &&
        (playback.phase == TtsPhase.PREPARING || playback.phase == TtsPhase.SYNTHESIZING)
    val isPlaying = isThis && playback.phase == TtsPhase.PLAYING

    when {
        isSynthesizing -> IconButton(onClick = { viewModel.stop() }, modifier = Modifier.size(36.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        isPlaying -> IconButton(onClick = { viewModel.stop() }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Stop,
                contentDescription = UiTranslations.getString(context, "audio_btn_stop", uiLanguage),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        else -> IconButton(
            onClick = { viewModel.previewModel(voiceSampleText(info.language), info, voiceSpeed) },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = UiTranslations.getString(context, "audio_btn_play", uiLanguage),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ModelActions(
    info: TtsModelInfo,
    state: TtsModelState,
    uiLanguage: String,
    isOnline: Boolean,
    onDownload: () -> Unit,
    onPause: (Int) -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    when (state) {
        is TtsModelState.NotDownloaded -> {
            // Sin internet no se puede descargar: botón deshabilitado + aviso (requisito).
            Button(
                onClick = onDownload,
                enabled = isOnline,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(UiTranslations.getString(context, "voice_btn_download", uiLanguage))
            }
            if (!isOnline) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = UiTranslations.getString(context, "voice_no_internet", uiLanguage),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        is TtsModelState.Downloading -> {
            val pct = (state.progress * 100).toInt().coerceIn(0, 100)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = UiTranslations.getString(context, "voice_downloading", uiLanguage, "$pct%"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(4.dp))
                // Pausar (guarda checkpoint) + Anular (borra parciales).
                IconButton(onClick = { onPause(pct) }) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = UiTranslations.getString(context, "voice_btn_pause", uiLanguage),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = UiTranslations.getString(context, "voice_btn_cancel", uiLanguage),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        is TtsModelState.Paused -> {
            val pct = (state.progress * 100).toInt().coerceIn(0, 100)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.waitingForNetwork) {
                            UiTranslations.getString(context, "voice_waiting_network", uiLanguage)
                        } else {
                            UiTranslations.getString(context, "voice_paused", uiLanguage, "$pct%")
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    )
                }
                Spacer(Modifier.width(4.dp))
                // Pausa manual → Reanudar (solo con internet). Esperando red → solo Anular.
                if (!state.waitingForNetwork) {
                    IconButton(onClick = onResume, enabled = isOnline) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = UiTranslations.getString(context, "voice_btn_resume", uiLanguage),
                            tint = if (isOnline) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    }
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = UiTranslations.getString(context, "voice_btn_cancel", uiLanguage),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        is TtsModelState.Downloaded -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onActivate,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(UiTranslations.getString(context, "voice_btn_activate", uiLanguage))
                }
                Spacer(Modifier.width(8.dp))
                DeleteButton(uiLanguage, onDelete)
            }
        }

        is TtsModelState.Active -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InUseBadge(uiLanguage, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                DeleteButton(uiLanguage, onDelete)
            }
        }

        is TtsModelState.Error -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = UiTranslations.getString(context, "voice_error", uiLanguage),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(text = state.message, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onDownload, enabled = isOnline, shape = RoundedCornerShape(10.dp)) {
                    Text(UiTranslations.getString(context, "voice_btn_retry", uiLanguage))
                }
            }
        }
    }
}

@Composable
private fun DeleteButton(uiLanguage: String, onDelete: () -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = onDelete,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
    ) {
        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(UiTranslations.getString(context, "voice_btn_delete", uiLanguage))
    }
}

@Composable
private fun InUseBadge(uiLanguage: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = UiTranslations.getString(context, "voice_in_use", uiLanguage),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun QualityChip(quality: ModelQuality, uiLanguage: String) {
    val context = LocalContext.current
    val (color, key) = when (quality) {
        ModelQuality.LIGHT -> LightGreen to "voice_quality_light"
        ModelQuality.HIGH -> HighBlue to "voice_quality_high"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = UiTranslations.getString(context, key, uiLanguage),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

/** Ícono de género (texto): masculino / femenino / neutral. */
private fun genderIcon(gender: VoiceGender): String = when (gender) {
    VoiceGender.MALE -> "👤♂"
    VoiceGender.FEMALE -> "👤♀"
    VoiceGender.NEUTRAL -> "◯"
}
