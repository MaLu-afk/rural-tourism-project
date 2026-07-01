package yupay.turismo.ui.features.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yupay.turismo.ui.AuthEvent
import yupay.turismo.ui.MainViewModel
import yupay.turismo.ui.components.CountdownConfirmationDialog
import yupay.turismo.utils.UiTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountInfoScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val settings by viewModel.appSettings.collectAsState()
    val language = settings?.language ?: "Español"
    val context = LocalContext.current
    val session by viewModel.cloudSession.collectAsState()
    val email = session?.email?.ifBlank { null } ?: settings?.accountEmail ?: UiTranslations.getString(context, "account_email_unavailable", language)

    val authState by viewModel.authState.collectAsState()
    var showDelete by remember { mutableStateOf(false) }

    // Al completarse el borrado en la API + reset de fábrica, volver al onboarding.
    LaunchedEffect(authState.event) {
        if (authState.event is AuthEvent.AccountDeleted) {
            viewModel.consumeAuthEvent()
            onAccountDeleted()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(UiTranslations.getString(context, "account_title", language), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = UiTranslations.getString(context, "btn_back", language))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CloudDone,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                UiTranslations.getString(context, "account_linked_synced", language),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(UiTranslations.getString(context, "auth_email_label", language), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text(email, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Establecer / cambiar contraseña (también para cuentas creadas con Google).
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChangePassword() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(UiTranslations.getString(context, "account_set_change_password", language), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            UiTranslations.getString(context, "account_set_change_password_desc", language),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                UiTranslations.getString(context, "account_data_saved_info", language),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Borrar cuenta (acción destructiva): borra la cuenta y todos sus datos en la API y
            // resetea el dispositivo. Tras un modal con cuenta atrás de 10 s, vuelve al onboarding.
            OutlinedButton(
                onClick = { showDelete = true },
                enabled = !authState.loading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                if (authState.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(UiTranslations.getString(context, "delete_account_button", language))
                }
            }

            authState.error?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showDelete) {
        CountdownConfirmationDialog(
            language = language,
            titleKey = "delete_account_title",
            descKey = "delete_account_desc",
            confirmKey = "btn_delete_account",
            seconds = 10,
            onConfirm = {
                showDelete = false
                viewModel.deleteAccount()
            },
            onDismiss = { showDelete = false }
        )
    }
}
