package yupay.turismo.ui.features.sync

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import yupay.turismo.sync.SyncViewModel
import yupay.turismo.ui.components.ConnectionRequiredDialog
import yupay.turismo.utils.UiTranslations
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    navController: NavController,
    syncViewModel: SyncViewModel,
    role: String,
    deviceName: String,
    ip: String,
    port: Int,
    sessionId: String,
    language: String = "Español"
) {
    val context = LocalContext.current
    val isConnected by syncViewModel.isConnected.collectAsState()
    val logs by syncViewModel.logs.collectAsState()
    val counter by syncViewModel.ticks.collectAsState()
    val latency by syncViewModel.latency.collectAsState()
    val acks by syncViewModel.acks.collectAsState()
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showConnectionRequiredDialog by remember { mutableStateOf(false) }

    val successRate = if (acks.second > 0) (acks.first * 100 / acks.second) else 100

    LaunchedEffect(Unit) {
        syncViewModel.syncCompleted.collect {
            // delay(1500) // Eliminamos el retraso y la navegación forzada si queremos que el usuario decida
            // O podemos navegar, pero sabiendo que la conexión sigue viva en el ViewModel
            navController.navigate("main_pager") {
                popUpTo("main_pager") { inclusive = true }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (role == "CLIENT") {
            // Pasar el token de emparejamiento escaneado del QR para que el servidor lo valide.
            syncViewModel.connectToServer(ip, port, sessionId.ifBlank { null })
        } else {
            // Si es servidor, ya está iniciado desde ShowQrScreen, 
            // pero nos aseguramos de que el rol sea correcto en el ViewModel
            // (esto es útil si se recrea la pantalla)
            // syncViewModel.markAsServer() // Podríamos agregar esto si fuera necesario
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (counter > 0) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (role == "SERVER") UiTranslations.getString(context, "sync_server_mode", language) else UiTranslations.getString(context, "sync_syncing_title", language)) },
                navigationIcon = {
                    IconButton(onClick = { /* Deshabilitado durante sync */ }, enabled = false) {
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Badge
            Surface(
                color = if (isConnected) Color(0xFF4CAF50) else Color.Red,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isConnected) UiTranslations.getString(context, "sync_badge_connected", language) else UiTranslations.getString(context, "sync_badge_disconnected", language),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (counter > 0 || isConnected) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = UiTranslations.getString(context, "sync_syncing_data", language),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = UiTranslations.getString(context, "sync_waiting", language),
                            fontSize = 24.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Metrics
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem(UiTranslations.getString(context, "sync_metric_last_event", language), "#$counter")
                MetricItem(UiTranslations.getString(context, "sync_metric_latency", language), "${latency}ms")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem(UiTranslations.getString(context, "sync_metric_acks", language), "${acks.first} / ${acks.second}")
                MetricItem(UiTranslations.getString(context, "sync_metric_success_rate", language), "$successRate%")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { syncViewModel.sendPing() },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(UiTranslations.getString(context, "sync_send_ping", language))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                UiTranslations.getString(context, "sync_event_log", language),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall
            )
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(logs) { log ->
                    Text(
                        text = log,
                        color = if (log.contains("conectado", ignoreCase = true) || log.contains("recibidos", ignoreCase = true)) Color(0xFF4CAF50) else Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            Button(
                onClick = {
                    if (isConnected) {
                        showDisconnectDialog = true
                    } else {
                        showConnectionRequiredDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(UiTranslations.getString(context, "sync_disconnect_btn", language))
            }
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text(UiTranslations.getString(context, "sync_disconnect_confirm_title", language)) },
            confirmButton = {
                Button(onClick = {
                    showDisconnectDialog = false
                    syncViewModel.disconnectAllRemotes {
                        navController.navigate("main_pager") {
                            popUpTo("main_pager") { inclusive = true }
                        }
                    }
                }) {
                    Text(UiTranslations.getString(context, "sync_disconnect_confirm_btn", language))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text(UiTranslations.getString(context, "btn_cancel", language))
                }
            }
        )
    }

    if (showConnectionRequiredDialog) {
        ConnectionRequiredDialog(language = language, onDismiss = { showConnectionRequiredDialog = false })
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

