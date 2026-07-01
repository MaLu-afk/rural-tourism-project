package yupay.turismo.ui.features.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import yupay.turismo.sync.SyncViewModel
import yupay.turismo.utils.UiTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedDevicesScreen(navController: NavController, syncViewModel: SyncViewModel, language: String) {
    val isConnected by syncViewModel.isConnected.collectAsState()
    val remoteDeviceName by syncViewModel.remoteDeviceName.collectAsState()
    val context = LocalContext.current
    
    var showIndividualDisconnectDialog by remember { mutableStateOf(false) }
    var showMassDisconnectDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiTranslations.getString(context, "linked_devices_title", language)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = UiTranslations.getString(context, "btn_back", language))
                    }
                }
            )
        }
    ) { padding ->
        if (remoteDeviceName == null) {
            EmptyState(language)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        DeviceItem(
                            name = remoteDeviceName ?: UiTranslations.getString(context, "linked_devices_unknown", language),
                            isActive = isConnected,
                            language = language,
                            // Permitir quitar el vínculo también offline: limpia la información de
                            // conexión obsoleta en este dispositivo aunque el peer no esté accesible.
                            onDisconnect = { showIndividualDisconnectDialog = true }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    }
                }

                Button(
                    onClick = { showMassDisconnectDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(UiTranslations.getString(context, "linked_devices_disconnect_all", language), fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    if (showIndividualDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showIndividualDisconnectDialog = false },
            title = { Text(UiTranslations.getString(context, "linked_devices_disconnect_confirm_title", language)) },
            text = { Text(UiTranslations.getString(context, "linked_devices_disconnect_confirm_desc", language, remoteDeviceName ?: "")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showIndividualDisconnectDialog = false
                        syncViewModel.requestRemoteLogout {
                            navController.popBackStack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text(UiTranslations.getString(context, "linked_devices_disconnect_btn", language))
                }
            },
            dismissButton = {
                TextButton(onClick = { showIndividualDisconnectDialog = false }) {
                    Text(UiTranslations.getString(context, "btn_cancel", language))
                }
            }
        )
    }

    if (showMassDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showMassDisconnectDialog = false },
            title = { Text(UiTranslations.getString(context, "linked_devices_mass_disconnect_title", language)) },
            text = { Text(UiTranslations.getString(context, "linked_devices_mass_disconnect_desc", language)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMassDisconnectDialog = false
                        syncViewModel.disconnectAllRemotes {
                            navController.popBackStack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text(UiTranslations.getString(context, "linked_devices_mass_disconnect_btn", language))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMassDisconnectDialog = false }) {
                    Text(UiTranslations.getString(context, "btn_cancel", language))
                }
            }
        )
    }

}

@Composable
fun DeviceItem(name: String, isActive: Boolean, language: String, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else Color.Gray.copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                if (isActive) UiTranslations.getString(context, "linked_devices_connected_now", language) else UiTranslations.getString(context, "linked_devices_disconnected", language),
                fontSize = 12.sp,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
            )
        }

        // Siempre disponible: permite también quitar un vínculo obsoleto cuando el peer está offline.
        IconButton(onClick = onDisconnect) {
            Icon(
                Icons.Default.ArrowBack, // Usamos ArrowBack como placeholder para "sacar" o desconectar
                contentDescription = UiTranslations.getString(context, "linked_devices_disconnect_action", language),
                tint = Color.Red,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun EmptyState(language: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Devices,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = Color.Gray.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(UiTranslations.getString(context, "linked_devices_empty", language), fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(
            UiTranslations.getString(context, "linked_devices_empty_hint", language),
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
}

