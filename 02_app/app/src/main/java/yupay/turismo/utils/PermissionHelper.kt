package yupay.turismo.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import yupay.turismo.utils.UiTranslations

@Composable
fun PermissionRequester(
    permissions: List<String>,
    onAllGranted: () -> Unit,
    onDenied: () -> Unit,
    explanationTitle: String,
    explanationMessage: String,
    trigger: Boolean,
    onTriggerReset: () -> Unit,
    language: String = "Español"
) {
    val context = LocalContext.current
    var showExplanation by remember { mutableStateOf(false) }
    var showSettingsSnackbar by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            onAllGranted()
        } else {
            showSettingsSnackbar = true
            onDenied()
        }
        onTriggerReset()
    }

    LaunchedEffect(trigger) {
        if (trigger) {
            showExplanation = true
        }
    }

    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { 
                showExplanation = false
                onTriggerReset()
            },
            title = { Text(explanationTitle) },
            text = { Text(explanationMessage) },
            confirmButton = {
                Button(onClick = {
                    showExplanation = false
                    launcher.launch(permissions.toTypedArray())
                }) {
                    Text(UiTranslations.getString(context, "perm_grant", language))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showExplanation = false
                    onTriggerReset()
                }) {
                    Text(UiTranslations.getString(context, "btn_cancel", language))
                }
            }
        )
    }

    if (showSettingsSnackbar) {
        // En una implementación real usaríamos SnackbarHostState.
        // Para simplificar esta UI, usamos un AlertDialog informativo si se deniegan.
        AlertDialog(
            onDismissRequest = { showSettingsSnackbar = false },
            title = { Text(UiTranslations.getString(context, "perm_settings_title", language)) },
            text = { Text(UiTranslations.getString(context, "perm_settings_desc", language)) },
            confirmButton = {
                Button(onClick = {
                    showSettingsSnackbar = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text(UiTranslations.getString(context, "perm_open_settings", language))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsSnackbar = false }) {
                    Text(UiTranslations.getString(context, "btn_close", language))
                }
            }
        )
    }
}

fun getSyncPermissions(includeCamera: Boolean): List<String> {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.CHANGE_NETWORK_STATE
    )
    
    if (includeCamera) {
        permissions.add(Manifest.permission.CAMERA)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    
    return permissions
}
