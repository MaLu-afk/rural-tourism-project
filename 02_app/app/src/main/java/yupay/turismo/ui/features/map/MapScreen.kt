package yupay.turismo.ui.features.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
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
fun MapScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    isAudioActive: Boolean = true
) {
    val visits by viewModel.allVisits.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val language = settings?.language ?: "Español"
    val context = LocalContext.current

    val currentSummary = settings?.let { it.mapSummary[it.language] ?: it.mapSummary["Español"] ?: "" } ?: ""

    // Reproductor de audio real (TTS cacheado). Owner "map" compartido con FullscreenMapScreen
    // (misma página): el audio es continuo al expandir/contraer el mapa.
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
    // Alcance por página: al dejar de ser la página visible del pager, se pausa.
    LaunchedEffect(isAudioActive) { if (!isAudioActive) audioVm.pauseIfOwner(owner) }
    DisposableEffect(owner) { onDispose { audioVm.releaseIfOwner(owner) } }

    // Orden y colores de productos, compartidos con el dibujo del mapa (puntos/burbujas) para que la
    // leyenda y el mapa usen el mismo color por producto. Incluye TODOS los productos (la leyenda
    // muestra ~4 y permite scroll).
    val legend = rememberProductLegend(visits)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left side: Map
                Column(modifier = Modifier.weight(1.5f)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = UiTranslations.getString(context, "map_title", language),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 34.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            OsmMapView(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = viewModel,
                                visits = visits,
                                isInteractive = false,
                                showLabels = false,
                                viewMode = viewMode,
                                productColors = legend.colorsArgb
                            )

                            if (isPlaying) {
                                MapSubtitles(
                                    text = currentSummary,
                                    currentTime = currentTime,
                                    durationMs = totalDuration,
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                                )
                            }

                            IconButton(
                                onClick = { onNavigate("fullscreen_map") },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = UiTranslations.getString(context, "map_cd_expand", language),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Mode Toggle
                            IconButton(
                                onClick = { viewMode = if (viewMode == MapViewMode.POINTS) MapViewMode.BUBBLES else MapViewMode.POINTS },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (viewMode == MapViewMode.POINTS) Icons.Default.BubbleChart else Icons.Default.LocationOn,
                                    contentDescription = UiTranslations.getString(context, "map_cd_mode", language),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Right side: Legend and Audio
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(50.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = UiTranslations.getString(context, "map_legend", language),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            // Leyenda con scroll VERTICAL en horizontal: hasta ~4 visibles y se
                            // desplaza para ver todos los productos.
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                legend.ordered.forEachIndexed { index, (name, count) ->
                                    LegendItemRow(name, legend.colorAt(index), count)
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = UiTranslations.getString(context, "map_summary_title", language),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            if (currentSummary.isNotBlank()) {
                                AudioPlayerUI(
                                    currentTime = currentTime,
                                    totalDuration = totalDuration,
                                    isPlaying = isPlaying,
                                    onPlayPauseClick = { audioVm.togglePlayPause() },
                                    onSeek = { fraction -> audioVm.seekToFraction(fraction) },
                                    onFastForward = { audioVm.forward10() },
                                    onRewind = { audioVm.rewind10() },
                                    compact = true,
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
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = UiTranslations.getString(context, "map_title", language),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            OsmMapView(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = viewModel,
                                visits = visits,
                                isInteractive = false,
                                showLabels = false,
                                viewMode = viewMode,
                                productColors = legend.colorsArgb
                            )

                            if (isPlaying) {
                                MapSubtitles(
                                    text = currentSummary,
                                    currentTime = currentTime,
                                    durationMs = totalDuration,
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                                )
                            }

                            IconButton(
                                onClick = { onNavigate("fullscreen_map") },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = UiTranslations.getString(context, "map_cd_expand", language),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Mode Toggle
                            IconButton(
                                onClick = { viewMode = if (viewMode == MapViewMode.POINTS) MapViewMode.BUBBLES else MapViewMode.POINTS },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (viewMode == MapViewMode.POINTS) Icons.Default.BubbleChart else Icons.Default.LocationOn,
                                    contentDescription = UiTranslations.getString(context, "map_cd_mode", language),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = UiTranslations.getString(context, "map_legend", language),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Leyenda con scroll HORIZONTAL en vertical/portrait: hasta ~4 visibles y se
                        // desplaza para ver todos los productos.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            legend.ordered.forEachIndexed { index, (name, count) ->
                                LegendItem(name, legend.colorAt(index), count)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = UiTranslations.getString(context, "map_summary_title", language),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        if (currentSummary.isNotBlank()) {
                            AudioPlayerUI(
                                currentTime = currentTime,
                                totalDuration = totalDuration,
                                isPlaying = isPlaying,
                                onPlayPauseClick = { audioVm.togglePlayPause() },
                                onSeek = { fraction -> audioVm.seekToFraction(fraction) },
                                onFastForward = { audioVm.forward10() },
                                onRewind = { audioVm.rewind10() },
                                compact = false,
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
        }
    }
}

@Composable
fun LegendItemRow(label: String, color: Color, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
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

@Composable
fun LegendItem(label: String, color: Color, count: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp).width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = count.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
