package yupay.turismo.ui.features.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import yupay.turismo.ui.MainViewModel
import yupay.turismo.sync.SyncViewModel
import yupay.turismo.ui.features.map.MapScreen
import yupay.turismo.ui.features.profile.ProfileScreen
import yupay.turismo.ui.features.visits.VisitsScreen
import yupay.turismo.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun MainPagerScreen(
    pagerState: PagerState,
    viewModel: MainViewModel,
    syncViewModel: SyncViewModel,
    navController: NavController,
    innerPadding: PaddingValues,
    businessName: String,
    selectedService: String,
    entrepreneurTips: String,
    profilePicture: ByteArray?,
    preferredCurrency: String,
    usdRate: Double,
    eurRate: Double,
    userScrollEnabled: Boolean = true
) {
    val visits by viewModel.allVisits.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val language = settings?.language ?: "Español"
    val coroutineScope = rememberCoroutineScope()

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        beyondViewportPageCount = 1,
        userScrollEnabled = userScrollEnabled
    ) { page ->
        when (page) {
            0 -> HomeScreen(
                businessName = businessName,
                selectedService = selectedService,
                entrepreneurTips = entrepreneurTips,
                profilePicture = profilePicture,
                visits = visits,
                preferredCurrency = preferredCurrency,
                usdRate = usdRate,
                eurRate = eurRate,
                language = language,
                onNavigateToTip = { navController.navigate(Routes.TIP_DETAIL) },
                onNavigateToAdd = { navController.navigate(Routes.ADD_VISIT) },
                onNavigateToDashboard = { navController.navigate(Routes.DASHBOARD) },
                onNavigate = { route ->
                    if (route == Routes.VISITS) {
                        // For the "See All" button in Home, we use scrollToPage for instant switch as requested for buttons
                        coroutineScope.launch { pagerState.scrollToPage(1) }
                    } else {
                        navController.navigate(route) {
                            popUpTo(Routes.MAIN_PAGER) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
            1 -> VisitsScreen(
                viewModel = viewModel,
                onNavigateToAdd = { navController.navigate(Routes.ADD_VISIT) },
                onNavigateToDetail = { id -> navController.navigate(Routes.visitDetail(id)) },
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.MAIN_PAGER) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
            2 -> MapScreen(
                viewModel = viewModel,
                isAudioActive = pagerState.settledPage == 2,
                onNavigate = { route ->
                    if (route == "fullscreen_map") {
                        navController.navigate(Routes.FULLSCREEN_MAP)
                    } else {
                        navController.navigate(route) {
                            popUpTo(Routes.MAIN_PAGER) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
            3 -> ProfileScreen(
                viewModel = viewModel,
                syncViewModel = syncViewModel,
                navController = navController,
                onNavigateToEdit = { navController.navigate(Routes.PROFILE_EDIT) },
                onNavigateToLanguage = { navController.navigate(Routes.PROFILE_LANGUAGE) },
                onNavigateToHelp = { navController.navigate(Routes.PROFILE_HELP) },
                onNavigateToPrivacy = { navController.navigate(Routes.PROFILE_PRIVACY) },
                onNavigateToCatalog = { navController.navigate(Routes.PRODUCT_CATALOG) },
                onNavigateToVoiceModels = { navController.navigate(Routes.PROFILE_VOICE_MODELS) }
            )
        }
    }
}
