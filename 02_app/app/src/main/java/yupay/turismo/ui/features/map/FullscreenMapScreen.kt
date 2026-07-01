package yupay.turismo.ui.features.map

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.lifecycle.viewmodel.compose.viewModel
import yupay.turismo.tts.SupportedLanguage
import yupay.turismo.tts.audio.AudioPlaybackViewModel
import yupay.turismo.ui.MainViewModel
import yupay.turismo.ui.components.AudioPlayerUI
import yupay.turismo.ui.components.MapSubtitles
import yupay.turismo.utils.UiTranslations

@Composable
fun FullscreenMapScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val visits by viewModel.allVisits.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val currentSummary = settings?.let { it.mapSummary[it.language] ?: it.mapSummary["Español"] ?: "" } ?: ""
    val context = LocalContext.current
    val language = settings?.language ?: "Español"

    // Reproductor de audio real. Owner "map" compartido con MapScreen (misma página).
    var viewMode by remember { mutableStateOf(MapViewMode.POINTS) }
    val ttsLanguage = SupportedLanguage.fromSettings(settings?.language)
    val voiceSpeed = settings?.voiceSpeed ?: 1.0f
    val owner = "map"
    val audioVm: AudioPlaybackViewModel = viewModel()
    val audio by audioVm.state.collectAsState()
    val isOwner = audio.ownerKey == owner
    val currentTime = if (isOwner) audio.positionMs else 0L
    val totalDuration = if (isOwner) audio.durationMs else 0L
    val isPlaying = isOwner && audio.isPlaying
    val isPreparing = !isOwner || audio.isPreparing
    val audioReady = isOwner && audio.ready
    val hasVoice = !isOwner || audio.hasVoice
    val isSynthesizing = isOwner && audio.isSynthesizing
    val hasText = !isOwner || audio.hasText

    LaunchedEffect(currentSummary, language) { audioVm.prepare(owner, currentSummary, ttsLanguage) }
    LaunchedEffect(voiceSpeed) { audioVm.setSpeed(voiceSpeed) }
    DisposableEffect(owner) { onDispose { audioVm.releaseIfOwner(owner) } }

    // Orden y colores de productos compartidos con el dibujo del mapa (puntos/burbujas) e idénticos
    // a la leyenda. Incluye TODOS los productos (la leyenda muestra ~4 con scroll).
    val legend = rememberProductLegend(visits)

    // Handle system back button
    BackHandler {
        val activity = context.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onBack()
    }
    
    // Force Landscape orientation
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // OSM Map View
        OsmMapView(
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel,
            visits = visits,
            isInteractive = true,
            zoomLevel = 4.0,
            showLabels = true,
            viewMode = viewMode,
            productColors = legend.colorsArgb
        )

        // Subtítulos flotantes
        if (isPlaying) {
            MapSubtitles(
                text = currentSummary,
                currentTime = currentTime,
                durationMs = totalDuration,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
            )
        }

        // Top Controls: Back Button and Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            IconButton(
                onClick = {
                    val activity = context.findActivity()
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    onBack()
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = UiTranslations.getString(context, "map_cd_back", language), tint = MaterialTheme.colorScheme.primary)
            }

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Mode Toggle
                IconButton(
                    onClick = { viewMode = if (viewMode == MapViewMode.POINTS) MapViewMode.BUBBLES else MapViewMode.POINTS },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (viewMode == MapViewMode.POINTS) Icons.Default.BubbleChart else Icons.Default.LocationOn,
                        contentDescription = UiTranslations.getString(context, "map_cd_mode", language),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Legend in fullscreen
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(180.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            UiTranslations.getString(context, "map_legend", language),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        // Scroll vertical: hasta ~4 visibles; se desplaza para ver todos los productos.
                        Column(
                            modifier = Modifier
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            legend.ordered.forEachIndexed { index, (name, count) ->
                                LegendItemFullScreen(name, legend.colorAt(index), count)
                            }
                        }
                    }
                }
            }
        }

        // Bottom Control: Audio Player
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.width(300.dp)
            ) {
                AudioPlayerUI(
                    currentTime = currentTime,
                    totalDuration = totalDuration,
                    isPlaying = isPlaying,
                    onPlayPauseClick = { audioVm.togglePlayPause() },
                    onSeek = { fraction -> audioVm.seekToFraction(fraction) },
                    onFastForward = { audioVm.forward10() },
                    onRewind = { audioVm.rewind10() },
                    compact = true,
                    modifier = Modifier.padding(8.dp),
                    language = language,
                    isPreparing = isPreparing,
                    isSynthesizing = isSynthesizing,
                    ready = audioReady,
                    hasVoice = hasVoice,
                    hasText = hasText
                )
            }
        }
    }
}

@Composable
fun LegendItemFullScreen(label: String, color: Color, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = count.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
