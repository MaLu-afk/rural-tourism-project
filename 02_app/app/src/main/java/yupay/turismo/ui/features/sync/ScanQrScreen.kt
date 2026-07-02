package yupay.turismo.ui.features.sync

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import yupay.turismo.utils.UiTranslations
import yupay.turismo.ui.navigation.Routes

private data class QrData(
    val ip: String,
    val port: Int,
    val sessionId: String,
    val deviceName: String
)

@Composable
fun ScanQrScreen(navController: NavController, language: String = "Español") {
    val context = LocalContext.current
    val scanner = remember { GmsBarcodeScanning.getClient(context as Activity) }
    var scannedData by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var isScannerActive by remember { mutableStateOf(true) }

    // Timer logic
    LaunchedEffect(Unit) {
        delay(45000L) // 45 seconds
        if (scannedData == null) {
            isScannerActive = false
            showTimeoutDialog = true
        }
    }

    // Trigger scanner automatically when entering the screen
    LaunchedEffect(isScannerActive) {
        if (isScannerActive) {
            scanner.startScan()
                .addOnSuccessListener { barcode: Barcode ->
                    scannedData = barcode.rawValue
                }
                .addOnFailureListener {
                    showError = true
                }
                .addOnCanceledListener {
                    // This handles when the user cancels the Google scanner overlay
                    navController.popBackStack()
                }
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (scannedData == null && !showError && !showTimeoutDialog) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(UiTranslations.getString(context, "scanner_opening", language))
                }
            }
            
            if (showError) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(UiTranslations.getString(context, "scanner_error", language))
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navController.popBackStack() }) {
                        Text(UiTranslations.getString(context, "btn_back", language))
                    }
                }
            }
        }
    }

    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { 
                showTimeoutDialog = false
                navController.popBackStack()
            },
            title = { Text(UiTranslations.getString(context, "scanner_timeout_title", language)) },
            text = { Text(UiTranslations.getString(context, "scanner_timeout_desc", language)) },
            confirmButton = {
                Button(onClick = {
                    showTimeoutDialog = false
                    isScannerActive = true
                }) {
                    Text(UiTranslations.getString(context, "btn_retry", language))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTimeoutDialog = false
                    navController.popBackStack()
                }) {
                    Text(UiTranslations.getString(context, "btn_cancel", language))
                }
            }
        )
    }

    if (scannedData != null) {
        val qrData = remember(scannedData) {
            try {
                val json = Json.parseToJsonElement(scannedData!!).jsonObject
                val ip = json["ip"]?.jsonPrimitive?.content ?: ""
                val port = json["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val sessionId = json["sessionId"]?.jsonPrimitive?.content ?: ""
                val deviceName = json["deviceName"]?.jsonPrimitive?.content ?: ""

                if (ip.isNotEmpty() && port != 0) {
                    QrData(ip, port, sessionId, deviceName)
                } else null
            } catch (e: Exception) {
                null
            }
        }

        if (qrData != null) {
            AlertDialog(
                onDismissRequest = {
                    scannedData = null
                    navController.popBackStack()
                },
                title = { Text(UiTranslations.getString(context, "scan_device_found_title", language)) },
                text = {
                    Column {
                        Text(UiTranslations.getString(context, "scan_label_name", language, qrData.deviceName))
                        Text(UiTranslations.getString(context, "scan_label_ip", language, qrData.ip, qrData.port))
                        Text(UiTranslations.getString(context, "scan_label_session", language, qrData.sessionId.take(8)))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        navController.navigate("sync_status?role=CLIENT&deviceName=${qrData.deviceName}&ip=${qrData.ip}&port=${qrData.port}&sessionId=${qrData.sessionId}")
                    }) {
                        Text(UiTranslations.getString(context, "scan_connect_btn", language))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        scannedData = null
                        navController.popBackStack()
                    }) {
                        Text(UiTranslations.getString(context, "btn_cancel", language))
                    }
                }
            )
        } else if (scannedData != null) {
            LaunchedEffect(scannedData) {
                scannedData = null
                showError = true
            }
        }
    }
}

