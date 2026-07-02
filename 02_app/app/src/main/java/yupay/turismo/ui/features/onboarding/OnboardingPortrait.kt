package yupay.turismo.ui.features.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import yupay.turismo.utils.UiTranslations

@Composable
fun OnboardingPortraitContent(
    pagerState: PagerState,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    onOtherLanguagesClick: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Dynamic Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            userScrollEnabled = true
        ) { pageIndex ->
            OnboardingPortraitPage(pageIndex, selectedLanguage)
        }

        // Static Elements at Bottom
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            OnboardingPageIndicator(pagerState = pagerState)

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (pagerState.currentPage < 2) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onNext()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = UiTranslations.getString(context, "onboarding_btn_${pagerState.currentPage}", selectedLanguage),
                    fontSize = 18.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LanguageSelector(
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
                onOtherLanguagesClick = onOtherLanguagesClick
            )
        }
    }
}

@Composable
fun OnboardingPortraitPage(pageIndex: Int, language: String) {
    val context = LocalContext.current
    val title = UiTranslations.getString(context, "onboarding_title_$pageIndex", language)
    val description = UiTranslations.getString(context, "onboarding_desc_$pageIndex", language)
    val icon = when (pageIndex) {
        0 -> Icons.Default.WifiOff
        1 -> Icons.Default.AutoGraph
        else -> Icons.Default.Lightbulb
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OnboardingIcon(icon, modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f))
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = description,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
