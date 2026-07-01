package yupay.turismo.ui.features.info

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import yupay.turismo.utils.UiTranslations

@Composable
fun PrivacyPolicyScreen(
    language: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    InfoTextScreen(
        title = UiTranslations.getString(context, "profile_privacy", language),
        content = UiTranslations.getString(context, "privacy_content", language),
        language = language,
        onBack = onBack
    )
}
