package yupay.turismo.ui.features.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yupay.turismo.ui.MainViewModel
import yupay.turismo.ui.components.UnsavedChangesDialog
import yupay.turismo.utils.UiTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.appSettings.collectAsState()
    val languages = listOf("Español", "Quechua", "Inglés", "Portugués")

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600
    // Key en settings?.language, no en el objeto `settings`: una sync re-emite AppSettings y con
    // key=`settings` remember reiniciaría la selección en curso. Ver EditProfileScreen.
    var selectedLanguage by remember(settings?.language) { mutableStateOf(settings?.language ?: "Español") }
    val currentLanguage = settings?.language ?: "Español"

    var showExitDialog by remember { mutableStateOf(false) }

    val hasChanges = selectedLanguage != currentLanguage

    BackHandler(enabled = hasChanges) {
        showExitDialog = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(UiTranslations.getString(context, "profile_language", currentLanguage), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges) showExitDialog = true else onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = UiTranslations.getString(context, "btn_back", currentLanguage))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        // Fila/celda de idioma reutilizada en vertical (lista) y horizontal (rejilla 2 columnas).
        val languageOption: @Composable (String, Modifier) -> Unit = { lang, mod ->
            Row(
                modifier = mod
                    .clickable { selectedLanguage = lang }
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = lang,
                    fontSize = 18.sp,
                    fontWeight = if (selectedLanguage == lang) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedLanguage == lang) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
                if (selectedLanguage == lang) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLandscape) {
                // Horizontal: 4 idiomas en rejilla 2x2.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 88.dp)
                ) {
                    languages.chunked(2).forEach { rowLangs ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowLangs.forEach { lang ->
                                Box(modifier = Modifier.weight(1f)) {
                                    languageOption(lang, Modifier.fillMaxWidth())
                                }
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 80.dp)
                ) {
                    languages.forEach { lang ->
                        languageOption(lang, Modifier.fillMaxWidth())
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    }
                }
            }

            if (hasChanges) {
                Button(
                    onClick = {
                        viewModel.saveLanguage(selectedLanguage)
                        onBack()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .then(if (isLandscape) Modifier.widthIn(max = 420.dp).fillMaxWidth() else Modifier.fillMaxWidth())
                        .height(56.dp)
                ) {
                    Text(UiTranslations.getString(context, "profile_save_changes", currentLanguage), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showExitDialog) {
            UnsavedChangesDialog(
                language = currentLanguage,
                onContinueEditing = { showExitDialog = false },
                onExitWithoutSaving = onBack,
                onDismiss = { showExitDialog = false }
            )
        }
    }
}
