package yupay.turismo.ui.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import yupay.turismo.ui.components.ServiceOption
import yupay.turismo.ui.components.ServiceSelectorGrid
import yupay.turismo.utils.UiTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    viewModel: yupay.turismo.ui.MainViewModel,
    selectedLanguage: String,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.appSettings.collectAsState()
    
    // Key en el VALOR del campo, no en el objeto `settings`: una sync re-emite AppSettings (avanza
    // lastSyncAt/lastModified) y con key=`settings` remember borraría la edición en curso. Ver EditProfileScreen.
    var businessName by remember(settings?.businessName) { mutableStateOf(settings?.businessName ?: "") }
    var selectedService by remember(settings?.businessCategory) { mutableStateOf(settings?.businessCategory ?: "") }

    val services = listOf(
        ServiceOption(UiTranslations.translateService("Hospedaje", selectedLanguage, context), Icons.Default.Bed),
        ServiceOption(UiTranslations.translateService("Alimentación", selectedLanguage, context), Icons.Default.Restaurant),
        ServiceOption(UiTranslations.translateService("Artesanía", selectedLanguage, context), Icons.Default.Checkroom),
        ServiceOption(UiTranslations.translateService("Varios", selectedLanguage, context), Icons.Default.Storefront)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        UiTranslations.getString(context, "setup_title", selectedLanguage),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = UiTranslations.getString(context, "btn_back", selectedLanguage),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left Column: Business Name Input
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = UiTranslations.getString(context, "setup_business_name_label", selectedLanguage),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { 
                            if (!it.contains("\n")) businessName = it 
                        },
                        placeholder = { Text(UiTranslations.getString(context, "setup_business_name_hint", selectedLanguage)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        maxLines = 1,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            focusedContainerColor = if (isSystemInDarkTheme()) Color.Transparent else MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = if (isSystemInDarkTheme()) Color.Transparent else MaterialTheme.colorScheme.surface
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { onSave(businessName, selectedService) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = businessName.isNotBlank() && selectedService.isNotBlank()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                UiTranslations.getString(context, "setup_save_btn", selectedLanguage),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                // Right Column: Service Selection
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = UiTranslations.getString(context, "setup_service_question", selectedLanguage),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Service Grid
                    ServiceSelectorGrid(
                        services = services,
                        selectedServices = if (selectedService.isEmpty()) emptySet() else setOf(selectedService.let { 
                            UiTranslations.translateService(it, selectedLanguage, context)
                        }),
                        onServiceToggle = { serviceNameTranslated ->
                            val originalName = when(serviceNameTranslated) {
                                UiTranslations.translateService("Hospedaje", selectedLanguage, context) -> "Hospedaje"
                                UiTranslations.translateService("Alimentación", selectedLanguage, context) -> "Alimentación"
                                UiTranslations.translateService("Artesanía", selectedLanguage, context) -> "Artesanía"
                                UiTranslations.translateService("Varios", selectedLanguage, context) -> "Varios"
                                else -> serviceNameTranslated
                            }
                            selectedService = originalName
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                // Business Name Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = UiTranslations.getString(context, "setup_business_name_label", selectedLanguage),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { 
                            if (!it.contains("\n")) businessName = it 
                        },
                        placeholder = { Text(UiTranslations.getString(context, "setup_business_name_hint", selectedLanguage)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        maxLines = 1,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            focusedContainerColor = if (isSystemInDarkTheme()) Color.Transparent else MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = if (isSystemInDarkTheme()) Color.Transparent else MaterialTheme.colorScheme.surface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = UiTranslations.getString(context, "setup_service_question", selectedLanguage),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Service Grid
                ServiceSelectorGrid(
                    services = services,
                    selectedServices = if (selectedService.isEmpty()) emptySet() else setOf(selectedService.let { 
                        UiTranslations.translateService(it, selectedLanguage, context)
                    }),
                    onServiceToggle = { serviceNameTranslated ->
                        val originalName = when(serviceNameTranslated) {
                            UiTranslations.translateService("Hospedaje", selectedLanguage, context) -> "Hospedaje"
                            UiTranslations.translateService("Alimentación", selectedLanguage, context) -> "Alimentación"
                            UiTranslations.translateService("Artesanía", selectedLanguage, context) -> "Artesanía"
                            UiTranslations.translateService("Varios", selectedLanguage, context) -> "Varios"
                            else -> serviceNameTranslated
                        }
                        selectedService = originalName
                    }
                )

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = { onSave(businessName, selectedService) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = businessName.isNotBlank() && selectedService.isNotBlank()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            UiTranslations.getString(context, "setup_save_btn", selectedLanguage),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
