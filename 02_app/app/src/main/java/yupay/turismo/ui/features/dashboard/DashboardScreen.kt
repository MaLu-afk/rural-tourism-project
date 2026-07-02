package yupay.turismo.ui.features.dashboard

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
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
import yupay.turismo.data.local.Visit
import yupay.turismo.data.local.AppSettings
import yupay.turismo.tts.SupportedLanguage
import yupay.turismo.tts.audio.AudioPlaybackViewModel
import yupay.turismo.ui.features.dashboard.components.FilterSelector
import yupay.turismo.ui.features.dashboard.components.InsightMessageOverlay
import yupay.turismo.ui.features.dashboard.tabs.SalesTab
import yupay.turismo.ui.features.dashboard.tabs.SummaryTab
import yupay.turismo.ui.features.dashboard.tabs.TimesTab
import yupay.turismo.ui.features.dashboard.tabs.VisitorsTab
import yupay.turismo.utils.UiTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    visits: List<Visit>,
    settings: AppSettings?,
    language: String,
    onBack: () -> Unit
) {
    val dashboardViewModel: DashboardViewModel = viewModel()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600

    val totalVisitors by dashboardViewModel.totalVisitors.collectAsState()
    val totalItemsSold by dashboardViewModel.totalItemsSold.collectAsState()
    val totalRevenue by dashboardViewModel.totalRevenue.collectAsState()
    val topNationalities by dashboardViewModel.topNationalities.collectAsState()
    val serviceDistribution by dashboardViewModel.serviceDistribution.collectAsState()
    val revenueByService by dashboardViewModel.revenueByService.collectAsState()
    val leaderCountry by dashboardViewModel.leaderCountry.collectAsState()
    val starService by dashboardViewModel.starService.collectAsState()
    val currentFilter by dashboardViewModel.filter.collectAsState()
    val revenueEstimates by dashboardViewModel.revenueEstimates.collectAsState()
    val peakHours by dashboardViewModel.peakHours.collectAsState()
    val averageTicket by dashboardViewModel.averageTicket.collectAsState()
    val visitsByWeekday by dashboardViewModel.visitsByWeekday.collectAsState()
    val partOfDay by dashboardViewModel.partOfDayDistribution.collectAsState()
    val revenueSeries by dashboardViewModel.revenueSeries.collectAsState()
    val visitorsSeries by dashboardViewModel.visitorsSeries.collectAsState()

    var selectedTab by remember { mutableStateOf(DashboardTab.SUMMARY) }
    var insightMessage by remember { mutableStateOf<String?>(null) }

    // Reproductor de audio real, ligado a esta página (un solo botón).
    val ttsLanguage = SupportedLanguage.fromSettings(language)
    val voiceSpeed = settings?.voiceSpeed ?: 1.0f
    val owner = "dashboard"
    val audioVm: AudioPlaybackViewModel = viewModel()
    val audio by audioVm.state.collectAsState()
    val dashboardOwnsAudio = audio.ownerKey == owner
    val dashboardPreparing = dashboardOwnsAudio && (audio.isPreparing || audio.isSynthesizing)
    LaunchedEffect(voiceSpeed) { audioVm.setSpeed(voiceSpeed) }
    DisposableEffect(owner) { onDispose { audioVm.releaseIfOwner(owner) } }

    // Si tras intentar reproducir no hay audio (sin voz instalada o síntesis fallida), avisar.
    val audioUnavailable = dashboardOwnsAudio && !audio.isPreparing && !audio.isSynthesizing && !audio.ready
    LaunchedEffect(audioUnavailable, audio.hasVoice) {
        if (audioUnavailable) {
            val msg = if (!audio.hasVoice) {
                UiTranslations.getString(context, "audio_btn_no_voice", language)
            } else {
                UiTranslations.getString(context, "audio_status_no_audio", language)
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(visits, settings) {
        dashboardViewModel.updateVisits(visits)
        dashboardViewModel.updateSettings(settings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiTranslations.getString(context, "dashboard_title", language), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = UiTranslations.getString(context, "dashboard_cd_back", language))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            // El botón solo aparece cuando hay suficientes visitas que resumir.
            if (totalVisitors > 0) {
                FloatingActionButton(
                    onClick = {
                        // Resumen distinto según la pestaña en la que está el usuario al pulsar.
                        val summary = dashboardViewModel.getTabSummary(context, selectedTab, language)
                        insightMessage = summary
                        // Si este mismo contenido ya resultó "sin audio", da feedback inmediato
                        // (pulsar de nuevo no relanza la síntesis: prepare es idempotente).
                        val a = audio
                        if (a.ownerKey == owner && !a.isPreparing && !a.isSynthesizing && !a.ready) {
                            val msg = if (!a.hasVoice) {
                                UiTranslations.getString(context, "audio_btn_no_voice", language)
                            } else {
                                UiTranslations.getString(context, "audio_status_no_audio", language)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        audioVm.prepareAndPlay(owner, summary, ttsLanguage)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    if (dashboardPreparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Default.VolumeUp, contentDescription = UiTranslations.getString(context, "insights_tts_description", language))
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
            if (isLandscape) {
                // En horizontal: filtros de tiempo a la izquierda y pestañas a su derecha, en una fila.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        FilterSelector(currentFilter, language) { dashboardViewModel.setFilter(it) }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DashboardTabs(selectedTab, language) { selectedTab = it }
                    }
                }
            } else {
                // En vertical: filtros encima de las pestañas.
                FilterSelector(currentFilter, language) { dashboardViewModel.setFilter(it) }
                DashboardTabs(selectedTab, language) { selectedTab = it }
            }

            when (selectedTab) {
                DashboardTab.SUMMARY -> SummaryTab(
                    language = language,
                    total = totalVisitors,
                    leader = leaderCountry,
                    star = starService,
                    averageTicket = averageTicket,
                    totalItems = totalItemsSold,
                    totalRevenue = totalRevenue,
                    revenue = revenueEstimates,
                    revenueSeries = revenueSeries
                )
                DashboardTab.VISITORS -> VisitorsTab(
                    language = language,
                    topNationalities = topNationalities,
                    visitsByWeekday = visitsByWeekday,
                    visitorsSeries = visitorsSeries
                )
                DashboardTab.SALES -> SalesTab(
                    language = language,
                    revenueSeries = revenueSeries,
                    averageTicket = averageTicket,
                    serviceDistribution = serviceDistribution,
                    revenueByService = revenueByService
                )
                DashboardTab.TIMES -> TimesTab(
                    language = language,
                    peakHours = peakHours,
                    visitsByWeekday = visitsByWeekday,
                    partOfDay = partOfDay
                )
            }
            }

            // Mensaje de resumen animado (aparece como subtítulo sobre el contenido)
            InsightMessageOverlay(
                message = insightMessage,
                language = language,
                onDismiss = { insightMessage = null },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * Barra de pestañas temáticas (Resumen / Visitantes / Ventas / Tiempos). Extraída para poder colocarla
 * tanto debajo de los filtros (vertical) como a su derecha (horizontal).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTabs(
    selectedTab: DashboardTab,
    language: String,
    onTabSelected: (DashboardTab) -> Unit
) {
    val context = LocalContext.current
    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        DashboardTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        UiTranslations.getString(context, tab.labelKey, language),
                        fontSize = 13.sp,
                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}
