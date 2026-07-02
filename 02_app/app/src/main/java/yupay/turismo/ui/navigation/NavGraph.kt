package yupay.turismo.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import yupay.turismo.sync.SyncViewModel
import yupay.turismo.ui.MainViewModel
import yupay.turismo.ui.components.*
import yupay.turismo.ui.features.home.*
import yupay.turismo.ui.features.onboarding.*
import yupay.turismo.ui.features.profile.*
import yupay.turismo.ui.features.visits.*
import yupay.turismo.ui.features.map.*
import yupay.turismo.ui.features.sync.*
import yupay.turismo.ui.features.info.*
import yupay.turismo.ui.features.splash.*
import yupay.turismo.ui.features.dashboard.*
import yupay.turismo.ui.features.auth.*
import yupay.turismo.utils.UiTranslations

@Composable
fun MainNavigation(
    viewModel: MainViewModel,
    syncViewModel: SyncViewModel,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val appSettings by viewModel.appSettings.collectAsState()
    val context = LocalContext.current

    var selectedLanguage by remember { mutableStateOf("Español") }
    var businessName by remember { mutableStateOf("") }
    var selectedService by remember { mutableStateOf("") }
    var entrepreneurTips by remember { mutableStateOf("") }
    var profilePicture by remember { mutableStateOf<ByteArray?>(null) }
    var mapSummary by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val mainPagerState = rememberPagerState(pageCount = { 4 })
    
    // Track if navigation is via bottom bar to skip horizontal animation in pager
    var isJumpNavigation by remember { mutableStateOf(false) }

    // Global Back Handler
    BackHandler {
        if (currentRoute == Routes.MAIN_PAGER) {
            if (mainPagerState.currentPage != 0) {
                isJumpNavigation = true
                coroutineScope.launch { 
                    mainPagerState.scrollToPage(0) 
                    isJumpNavigation = false
                }
            } else {
                (context as? Activity)?.finish()
            }
        } else if (currentRoute == Routes.HOME || currentRoute == Routes.VISITS || currentRoute == Routes.MAP || currentRoute == Routes.PROFILE) {
            navController.navigate(Routes.MAIN_PAGER) {
                popUpTo(0) { inclusive = true }
            }
        } else if (currentRoute == Routes.SPLASH || currentRoute == Routes.ONBOARDING) {
             (context as? Activity)?.finish()
        } else {
            if (isSecondaryRoute(currentRoute)) {
                navController.popBackStack()
            } else {
                navController.navigate(Routes.MAIN_PAGER) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    LaunchedEffect(appSettings) {
        if (appSettings == null && currentRoute == Routes.MAIN_PAGER) {
            navController.navigate(Routes.ONBOARDING) {
                popUpTo(0) { inclusive = true }
            }
        }

        appSettings?.let {
            selectedLanguage = it.language
            businessName = it.businessName
            selectedService = it.businessCategory
            entrepreneurTips = it.entrepreneurTips[it.language] ?: it.entrepreneurTips["Español"] ?: ""
            mapSummary = it.mapSummary[it.language] ?: it.mapSummary["Español"] ?: ""
            profilePicture = it.profilePicture
        }
    }

    // Reset total del dispositivo SERVIDOR al cancelar el P2P (lo dispare el servidor o el
    // cliente): vuelve al onboarding con el estado de fábrica.
    LaunchedEffect(Unit) {
        syncViewModel.resetEvent.collect {
            navController.navigate(Routes.ONBOARDING) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Sync online "en vivo": baja periódicamente los cambios hechos en OTRO dispositivo de la
    // misma cuenta. Sin esto, el pull sólo ocurría al cambiar algo localmente o al reconectar, así
    // que el otro equipo nunca veía los cambios. Sólo con sesión y en primer plano (RESUMED), para
    // no gastar batería/datos en segundo plano. El push/outbox existente no se toca.
    val cloudSession by viewModel.cloudSession.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(cloudSession != null, lifecycleOwner) {
        if (cloudSession == null) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.syncNow()
                delay(15_000)
            }
        }
    }

    val mainRoutes = listOf(Routes.HOME, Routes.VISITS, Routes.MAP, Routes.PROFILE)
    val isMainRoute = currentRoute == Routes.MAIN_PAGER || currentRoute in mainRoutes
    val showBottomBar = isMainRoute

    // Deep link de notificaciones: al tocar una notificación, navega a la sección correspondiente.
    // VISITS/MAP son páginas del pager (scroll); PRODUCT_CATALOG/TIP_DETAIL son rutas (navigate).
    // Se espera a que la app pase del splash/onboarding (appSettings cargado y ruta "real") para
    // no saltar antes de tiempo en un arranque en frío desde la notificación.
    val navTarget by viewModel.navTarget.collectAsState()
    LaunchedEffect(navTarget, currentRoute, appSettings) {
        val target = navTarget ?: return@LaunchedEffect
        if (appSettings == null) return@LaunchedEffect
        if (currentRoute == null || currentRoute == Routes.SPLASH ||
            currentRoute == Routes.ONBOARDING || currentRoute == Routes.PROFILE_SETUP
        ) return@LaunchedEffect

        when (target) {
            Routes.VISITS, Routes.MAP -> {
                val index = mainRoutes.indexOf(target).takeIf { it != -1 } ?: 0
                if (currentRoute != Routes.MAIN_PAGER) {
                    navController.navigate(Routes.MAIN_PAGER) { popUpTo(0) { inclusive = true } }
                }
                isJumpNavigation = true
                runCatching { mainPagerState.scrollToPage(index) }
                isJumpNavigation = false
            }
            Routes.PRODUCT_CATALOG, Routes.TIP_DETAIL -> {
                if (currentRoute != target) navController.navigate(target)
            }
        }
        viewModel.consumeNavTarget()
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600

    Scaffold(
        bottomBar = {
            if (showBottomBar && !isLandscape) {
                val currentPagerIndex = if (currentRoute == Routes.MAIN_PAGER) mainPagerState.currentPage else {
                    mainRoutes.indexOf(currentRoute).takeIf { it != -1 } ?: 0
                }
                BottomNavigationBar(
                    currentRoute = mainRoutes[currentPagerIndex],
                    language = appSettings?.language ?: "Español",
                    onNavigate = { route ->
                        val targetIndex = mainRoutes.indexOf(route)
                        if (currentRoute == Routes.MAIN_PAGER) {
                            coroutineScope.launch {
                                isJumpNavigation = true
                                mainPagerState.scrollToPage(targetIndex)
                                isJumpNavigation = false
                            }
                        } else {
                            navController.navigate(Routes.MAIN_PAGER) {
                                popUpTo(Routes.MAIN_PAGER) { inclusive = true }
                                launchSingleTop = true
                            }
                            coroutineScope.launch { 
                                isJumpNavigation = true
                                mainPagerState.scrollToPage(targetIndex)
                                isJumpNavigation = false
                            }
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showBottomBar && isLandscape) {
                val currentPagerIndex = if (currentRoute == Routes.MAIN_PAGER) mainPagerState.currentPage else {
                    mainRoutes.indexOf(currentRoute).takeIf { it != -1 } ?: 0
                }
                MainNavigationRail(
                    currentRoute = mainRoutes[currentPagerIndex],
                    onNavigate = { route ->
                        val targetIndex = mainRoutes.indexOf(route)
                        if (currentRoute == Routes.MAIN_PAGER) {
                            coroutineScope.launch {
                                isJumpNavigation = true
                                mainPagerState.scrollToPage(targetIndex)
                                isJumpNavigation = false
                            }
                        } else {
                            navController.navigate(Routes.MAIN_PAGER) {
                                popUpTo(Routes.MAIN_PAGER) { inclusive = true }
                                launchSingleTop = true
                            }
                            coroutineScope.launch { 
                                isJumpNavigation = true
                                mainPagerState.scrollToPage(targetIndex)
                                isJumpNavigation = false
                            }
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.SPLASH,
                    modifier = Modifier.padding(
                        top = 0.dp,
                        bottom = if (showBottomBar && !isLandscape) innerPadding.calculateBottomPadding() else 0.dp,
                        start = if (showBottomBar && isLandscape) 0.dp else 0.dp
                    ),
                    enterTransition = {
                        val target = targetState.destination.route
                        val animSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                        slideInHorizontally(initialOffsetX = { it }, animationSpec = animSpec) + fadeIn()
                    },
                    exitTransition = {
                        val animSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                        slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = animSpec) + fadeOut()
                    },
                    popEnterTransition = {
                        val animSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                        slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = animSpec) + fadeIn()
                    },
                    popExitTransition = {
                        val animSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                        slideOutHorizontally(targetOffsetX = { it }, animationSpec = animSpec) + fadeOut()
                    }
                ) {
                    composable(Routes.SPLASH) {
                        SplashScreen(isReady = appSettings != null) {
                            if (appSettings?.isOnboardingCompleted == true) {
                                navController.navigate(Routes.MAIN_PAGER) {
                                    popUpTo(Routes.SPLASH) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Routes.ONBOARDING) {
                                    popUpTo(Routes.SPLASH) { inclusive = true }
                                }
                            }
                        }
                    }
                    composable(Routes.ONBOARDING) {
                        val onboardingPagerState = rememberPagerState(pageCount = { 3 })
                        OnboardingContainer(
                            navController = navController,
                            pagerState = onboardingPagerState,
                            selectedLanguage = selectedLanguage,
                            onLanguageChange = {
                                selectedLanguage = it
                                viewModel.saveLanguage(it)
                            },
                            onNext = {
                                navController.navigate(Routes.PROFILE_SETUP)
                            }
                        )
                    }
                    composable(Routes.PROFILE_SETUP) {
                        ProfileSetupScreen(
                            viewModel = viewModel,
                            selectedLanguage = selectedLanguage,
                            onBack = { navController.popBackStack() },
                            onSave = { name, service ->
                                viewModel.saveProfile(name, service)
                                viewModel.preloadProducts(service)
                                navController.navigate(Routes.PRODUCT_CATALOG_SETUP)
                            }
                        )
                    }
                    composable(Routes.PRODUCT_CATALOG_SETUP) {
                        ProductCatalogScreen(
                            viewModel = viewModel,
                            language = selectedLanguage,
                            isSetupFlow = true,
                            onBack = { navController.popBackStack() },
                            onFinish = {
                                navController.navigate(Routes.MAIN_PAGER) {
                                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                                }
                            },
                            onNavigateToEditor = { id -> navController.navigate(Routes.productEditor(id)) }
                        )
                    }
                    composable(Routes.PRODUCT_CATALOG) {
                        ProductCatalogScreen(
                            viewModel = viewModel,
                            language = selectedLanguage,
                            isSetupFlow = false,
                            onBack = { navController.popBackStack() },
                            onFinish = { navController.popBackStack() },
                            onNavigateToEditor = { id -> navController.navigate(Routes.productEditor(id)) }
                        )
                    }
                    composable(
                        Routes.PRODUCT_EDITOR,
                        arguments = listOf(navArgument("productId") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { backStackEntry ->
                        val productId = backStackEntry.arguments?.getString("productId")?.toIntOrNull()
                        ProductEditorScreen(
                            productId = productId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.CURRENCY_SELECTION) {
                        CurrencySelectionScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.MAIN_PAGER) {
                        if (appSettings == null) {
                            GlobalLoadingScreen(UiTranslations.getString(context, "loading_settings", appSettings?.language ?: "Español"))
                        } else {
                            MainPagerScreen(
                                pagerState = mainPagerState,
                                viewModel = viewModel,
                                syncViewModel = syncViewModel,
                                navController = navController,
                                innerPadding = PaddingValues(0.dp),
                                businessName = businessName,
                                selectedService = selectedService,
                                entrepreneurTips = entrepreneurTips,
                                profilePicture = profilePicture,
                                preferredCurrency = appSettings?.preferredCurrency ?: "S/",
                                usdRate = appSettings?.usdExchangeRate ?: 3.8,
                                eurRate = appSettings?.eurExchangeRate ?: 4.1,
                                userScrollEnabled = !isLandscape
                            )
                        }
                    }
                    composable(Routes.VISIT_DETAIL) { backStackEntry ->
                        val visitId = backStackEntry.arguments?.getString("visitId")?.toIntOrNull() ?: 0
                        VisitDetailScreen(
                            visitId = visitId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.FULLSCREEN_MAP) {
                        FullscreenMapScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(
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
                    composable(Routes.PROFILE_VOICE_MODELS) {
                        VoiceModelsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.PROFILE_EDIT) {
                        EditProfileScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.SHOW_QR) {
                        val settings by viewModel.appSettings.collectAsState()
                        val language = settings?.language ?: "Español"
                        ShowQrScreen(navController = navController, syncViewModel = syncViewModel, language = language)
                    }
                    composable(Routes.SCAN_QR) {
                        val settings by viewModel.appSettings.collectAsState()
                        val language = settings?.language ?: "Español"
                        ScanQrScreen(navController = navController, language = language)
                    }
                    composable(Routes.LINKED_DEVICES) {
                        val settings by viewModel.appSettings.collectAsState()
                        val language = settings?.language ?: "Español"
                        LinkedDevicesScreen(navController = navController, syncViewModel = syncViewModel, language = language)
                    }
                    composable(
                        Routes.SYNC_STATUS,
                        arguments = listOf(
                            navArgument("role") { defaultValue = "CLIENT" },
                            navArgument("deviceName") { defaultValue = "Dispositivo" },
                            navArgument("ip") { defaultValue = "" },
                            navArgument("port") { type = NavType.IntType; defaultValue = 0 },
                            navArgument("sessionId") { defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        val settings by viewModel.appSettings.collectAsState()
                        val language = settings?.language ?: "Español"
                        SyncStatusScreen(
                            navController = navController,
                            syncViewModel = syncViewModel,
                            role = backStackEntry.arguments?.getString("role") ?: "CLIENT",
                            deviceName = backStackEntry.arguments?.getString("deviceName") ?: "",
                            ip = backStackEntry.arguments?.getString("ip") ?: "",
                            port = backStackEntry.arguments?.getInt("port") ?: 0,
                            sessionId = backStackEntry.arguments?.getString("sessionId") ?: "",
                            language = language
                        )
                    }
                    composable(Routes.PROFILE_LANGUAGE) {
                        LanguageSelectionScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.PROFILE_HELP) {
                        HelpScreen(
                            language = selectedLanguage,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.PROFILE_PRIVACY) {
                        PrivacyPolicyScreen(
                            language = selectedLanguage,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.REGISTER) {
                        RegisterScreen(
                            viewModel = viewModel,
                            navController = navController,
                            language = selectedLanguage,
                            onBack = { navController.popBackStack() },
                            onSuccess = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.LOGIN) {
                        LoginScreen(
                            viewModel = viewModel,
                            navController = navController,
                            onBack = { navController.popBackStack() },
                            onLinkOffline = {
                                navController.navigate(Routes.SHOW_QR)
                            },
                            onSuccess = { 
                                navController.navigate(Routes.MAIN_PAGER) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Routes.FORGOT_PASSWORD) {
                        ForgotPasswordScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onCodeSent = { email ->
                                navController.navigate(Routes.verifyOtp(email, "reset"))
                            }
                        )
                    }
                    composable(
                        Routes.VERIFY_OTP,
                        arguments = listOf(
                            navArgument("email") { type = NavType.StringType },
                            navArgument("flow") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val email = backStackEntry.arguments?.getString("email") ?: ""
                        val flow = backStackEntry.arguments?.getString("flow") ?: "register"
                        VerifyOtpScreen(
                            viewModel = viewModel,
                            email = email,
                            flow = flow,
                            onBack = { navController.popBackStack() },
                            onSuccess = {
                                if (flow == "register") {
                                    navController.navigate(Routes.MAIN_PAGER) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                } else {
                                    navController.navigate(Routes.resetPassword("reset"))
                                }
                            }
                        )
                    }
                    composable(
                        Routes.RESET_PASSWORD,
                        arguments = listOf(
                            navArgument("source") { type = NavType.StringType; defaultValue = "reset" }
                        )
                    ) { backStackEntry ->
                        val source = backStackEntry.arguments?.getString("source") ?: "reset"
                        ResetPasswordScreen(
                            viewModel = viewModel,
                            source = source,
                            onBack = { navController.popBackStack() },
                            onSuccess = {
                                if (source == "account") {
                                    navController.popBackStack()
                                } else {
                                    navController.navigate(Routes.LOGIN) {
                                        popUpTo(Routes.LOGIN) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }
                    composable(Routes.ACCOUNT_INFO) {
                        AccountInfoScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onChangePassword = { navController.navigate(Routes.resetPassword("account")) },
                            onAccountDeleted = {
                                navController.navigate(Routes.ONBOARDING) {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(Routes.ADD_VISIT) {
                        val settings by viewModel.appSettings.collectAsState()
                        val language = settings?.language ?: "Español"
                        AddVisitScreen(
                            viewModel = viewModel,
                            language = language,
                            onBack = { navController.popBackStack() },
                            onSave = { nationality, flag, products, subtotal, discV, discT, total ->
                                syncViewModel.addVisit(nationality, flag, products, subtotal, discV, discT, total)
                                navController.popBackStack()
                            }
                        )
                    }
                    composable(Routes.DASHBOARD) {
                        val visits by viewModel.allVisits.collectAsState()
                        val settings by viewModel.appSettings.collectAsState()
                        val language = settings?.language ?: "Español"
                        DashboardScreen(
                            visits = visits,
                            settings = settings,
                            language = language,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.TIP_DETAIL) { TipDetailScreen(viewModel = viewModel) { navController.popBackStack() } }
                }

                // Show Global Loading if appSettings are being loaded or if we are in a transition state
                if (appSettings == null && currentRoute != Routes.SPLASH && currentRoute != Routes.ONBOARDING) {
                    GlobalLoadingScreen(message = UiTranslations.getString(context, "loading_resources", appSettings?.language ?: "Español"))
                }
            }
        }
    }
}

private fun isSecondaryRoute(route: String?): Boolean {
    val secondaryRoutes = listOf(
        Routes.PROFILE_EDIT, Routes.PROFILE_LANGUAGE, Routes.PROFILE_HELP, Routes.PROFILE_PRIVACY,
        Routes.ADD_VISIT, Routes.VISIT_DETAIL, Routes.PRODUCT_CATALOG, Routes.PRODUCT_EDITOR,
        Routes.CURRENCY_SELECTION, Routes.DASHBOARD, Routes.TIP_DETAIL, Routes.SYNC_STATUS,
        Routes.SHOW_QR, Routes.SCAN_QR, Routes.LINKED_DEVICES, Routes.FULLSCREEN_MAP,
        Routes.PROFILE_VOICE_MODELS
    )
    return route in secondaryRoutes
}
