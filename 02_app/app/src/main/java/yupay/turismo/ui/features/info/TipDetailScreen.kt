package yupay.turismo.ui.features.info

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import yupay.turismo.tts.SupportedLanguage
import yupay.turismo.tts.audio.AudioPlaybackViewModel
import yupay.turismo.ui.MainViewModel
import yupay.turismo.ui.components.AudioPlayerUI
import yupay.turismo.ui.components.CinemaEffectText
import yupay.turismo.utils.UiTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipDetailScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val settings by viewModel.appSettings.collectAsState()
    val language = settings?.language ?: "Español"
    val currentTip = settings?.let { it.entrepreneurTips[it.language] ?: it.entrepreneurTips["Español"] ?: "" } ?: ""

    // Reproductor de audio real (TTS cacheado), ligado a esta página.
    val ttsLanguage = SupportedLanguage.fromSettings(settings?.language)
    val voiceSpeed = settings?.voiceSpeed ?: 1.0f
    val owner = "tip:$language"
    val audioVm: AudioPlaybackViewModel = viewModel()
    val audio by audioVm.state.collectAsState()
    val isOwner = audio.ownerKey == owner
    val currentTime = if (isOwner) audio.positionMs else 0L
    val totalDuration = if (isOwner) audio.durationMs else 0L
    val isPlaying = isOwner && audio.isPlaying
    // Indicadores: antes de que esta pantalla tome posesión (prepare en LaunchedEffect) se muestra
    // "convirtiendo…"; luego ready/hasVoice deciden controles vs "no hay audio"/"configura una voz".
    val isPreparing = !isOwner || audio.isPreparing
    val audioReady = isOwner && audio.ready
    val hasVoice = !isOwner || audio.hasVoice
    val isSynthesizing = isOwner && audio.isSynthesizing
    val hasText = !isOwner || audio.hasText

    LaunchedEffect(currentTip, language) { audioVm.prepare(owner, currentTip, ttsLanguage) }
    LaunchedEffect(voiceSpeed) { audioVm.setSpeed(voiceSpeed) }
    DisposableEffect(owner) { onDispose { audioVm.releaseIfOwner(owner) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiTranslations.getString(context, "tip_detail_title", language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = UiTranslations.getString(context, "btn_back", language))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = UiTranslations.getString(context, "tip_detail_subtitle", language),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (currentTip.isNotEmpty()) {
                            CinemaEffectText(
                                text = currentTip,
                                currentTime = currentTime,
                                totalDuration = totalDuration,
                                durationMs = totalDuration
                            )
                        } else {
                            Text(
                                text = UiTranslations.getString(context, "tip_detail_empty", language),
                                fontSize = 18.sp,
                                lineHeight = 28.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(32.dp))

                Column(
                    modifier = Modifier.weight(0.8f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AudioPlayerUI(
                        currentTime = currentTime,
                        totalDuration = totalDuration,
                        isPlaying = isPlaying,
                        onPlayPauseClick = { audioVm.togglePlayPause() },
                        onSeek = { fraction -> audioVm.seekToFraction(fraction) },
                        onFastForward = { audioVm.forward10() },
                        onRewind = { audioVm.rewind10() },
                        language = language,
                        isPreparing = isPreparing,
                        isSynthesizing = isSynthesizing,
                        ready = audioReady,
                        hasVoice = hasVoice,
                        hasText = hasText
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = UiTranslations.getString(context, "tip_detail_subtitle", language),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Área de texto con efecto cine
                Box(modifier = Modifier.weight(1f)) {
                    if (currentTip.isNotEmpty()) {
                        CinemaEffectText(
                            text = currentTip,
                            currentTime = currentTime,
                            totalDuration = totalDuration,
                            durationMs = totalDuration
                        )
                    } else {
                        Text(
                            text = UiTranslations.getString(context, "tip_detail_empty", language),
                            fontSize = 18.sp,
                            lineHeight = 28.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Reproductor de Audio
                AudioPlayerUI(
                    currentTime = currentTime,
                    totalDuration = totalDuration,
                    isPlaying = isPlaying,
                    onPlayPauseClick = { audioVm.togglePlayPause() },
                    onSeek = { fraction -> audioVm.seekToFraction(fraction) },
                    onFastForward = { audioVm.forward10() },
                    onRewind = { audioVm.rewind10() },
                    language = language,
                    isPreparing = isPreparing,
                    isSynthesizing = isSynthesizing,
                    ready = audioReady,
                    hasVoice = hasVoice,
                    hasText = hasText
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

