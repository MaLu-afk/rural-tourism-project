package yupay.turismo.ui.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import yupay.turismo.ui.navigation.Routes
import yupay.turismo.utils.UiTranslations

@Composable
fun OnboardingContainer(
    navController: NavController,
    pagerState: PagerState,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Main Content
        if (isLandscape) {
            OnboardingLandscapeContent(
                pagerState = pagerState,
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
                onOtherLanguagesClick = { navController.navigate(Routes.PROFILE_LANGUAGE) },
                onNext = onNext
            )
        } else {
            OnboardingPortraitContent(
                pagerState = pagerState,
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
                onOtherLanguagesClick = { navController.navigate(Routes.PROFILE_LANGUAGE) },
                onNext = onNext
            )
        }

        // Top Options Menu (Overlay)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(modifier = Modifier.wrapContentSize()) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = UiTranslations.getString(context, "onboarding_cd_options", selectedLanguage),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text(UiTranslations.getString(context, "profile_help", selectedLanguage)) },
                        onClick = {
                            showMenu = false
                            navController.navigate(Routes.PROFILE_HELP)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(UiTranslations.getString(context, "onboarding_link_device", selectedLanguage)) },
                        onClick = {
                            showMenu = false
                            navController.navigate(Routes.LOGIN)
                        }
                    )
                }
            }
        }
    }
}
