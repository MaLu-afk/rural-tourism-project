package yupay.turismo.ui.features.sync

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import yupay.turismo.sync.SyncViewModel
import yupay.turismo.utils.UiTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowQrScreen(navController: NavController, syncViewModel: SyncViewModel, language: String = "Español") {
    val context = LocalContext.current
    val isConnected by syncViewModel.isConnected.collectAsState()
    val localIp = syncViewModel.getLocalIpAddress()
    val port = 51234
    // Token de emparejamiento ESTABLE para esta sesión de vinculación. Identifica el vínculo y se
    // valida en el handshake: al iniciar un emparejamiento nuevo se genera uno nuevo, por lo que un
    // cliente que quedó vinculado a un servidor anterior (ya reseteado) es rechazado y debe re-escanear.
    val sessionId = rememberSaveable { java.util.UUID.randomUUID().toString() }

    LaunchedEffect(Unit) {
        syncViewModel.startServer(port, sessionId)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!syncViewModel.isConnected.value) {
                syncViewModel.cancelServerMode()
            }
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            navController.navigate("sync_status?role=SERVER&deviceName=Cliente&ip=$localIp&port=$port&sessionId=$sessionId")
        }
    }

    val jsonString = """
        {
          "ip": "$localIp",
          "port": $port,
          "sessionId": "$sessionId",
          "deviceName": "${android.os.Build.MODEL}"
        }
    """.trimIndent()

    val bitmap: Bitmap = try {
        BarcodeEncoder().encodeBitmap(jsonString, BarcodeFormat.QR_CODE, 600, 600)
    } catch (e: Exception) {
        Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiTranslations.getString(context, "qr_link_device_title", language)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = UiTranslations.getString(context, "qr_scan_instruction", language),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Card(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(16.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = UiTranslations.getString(context, "qr_cd_code", language),
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(UiTranslations.getString(context, "qr_waiting_connection", language), fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            SuggestionChip(
                onClick = { },
                label = { Text(UiTranslations.getString(context, "qr_label_ip", language, localIp)) }
            )
            SuggestionChip(
                onClick = { },
                label = { Text(UiTranslations.getString(context, "qr_label_session", language, sessionId.take(8))) }
            )
        }
    }
}

