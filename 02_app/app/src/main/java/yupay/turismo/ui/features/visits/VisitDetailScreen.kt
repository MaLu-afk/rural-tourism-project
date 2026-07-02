package yupay.turismo.ui.features.visits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yupay.turismo.data.local.DiscountType
import yupay.turismo.data.local.Visit
import yupay.turismo.ui.MainViewModel
import yupay.turismo.utils.CurrencyUtils
import yupay.turismo.utils.UiTranslations
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitDetailScreen(
    visitId: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    // Observa la lista (reactivo): cuando la visita se sube y obtiene remoteId, el estado
    // de abajo pasa de "Pendiente de envío" a "Enviado" sin reabrir la pantalla.
    val allVisits by viewModel.allVisits.collectAsState()
    val visit = allVisits.find { it.id == visitId }
    val settings by viewModel.appSettings.collectAsState()
    val language = settings?.language ?: "Español"
    val prefCurrency = settings?.preferredCurrency ?: "S/"
    val usdRate = settings?.usdExchangeRate ?: 3.8
    val eurRate = settings?.eurExchangeRate ?: 4.1
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiTranslations.getString(context, "visits_detail_title", language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = UiTranslations.getString(context, "btn_back", language))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        visit?.let { v ->
            // Bloques de contenido reutilizados en vertical (apilados) y horizontal (dos columnas).
            val header: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(v.nationalityFlag, fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(
                            text = v.nationality,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = formatDate(v.registrationDate),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val productsSection: @Composable () -> Unit = {
                Column {
                    Text(
                        text = UiTranslations.getString(context, "product_label", language),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            v.selectedProducts.forEach { item ->
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text(
                                            "${item.quantity} x ${item.name}",
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium
                                        )
                                        val itemPriceConverted = CurrencyUtils.convert(item.priceAtSale, item.currency, prefCurrency, usdRate, eurRate)
                                        Text(
                                            "$prefCurrency ${String.format("%.2f", itemPriceConverted * item.quantity)}",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (item.hasDiscount) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.LocalOffer, null, modifier = Modifier.size(10.dp), tint = Color(0xFFE53935))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            val originalPriceConverted = CurrencyUtils.convert(item.originalPrice, item.currency, prefCurrency, usdRate, eurRate)
                                            Text(
                                                text = "$prefCurrency ${String.format("%.2f", originalPriceConverted * item.quantity)}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                fontSize = 11.sp,
                                                textDecoration = TextDecoration.LineThrough
                                            )
                                        }
                                    }
                                }
                                if (item != v.selectedProducts.last()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }

            val totalsSection: @Composable () -> Unit = {
                val subtotalConverted = CurrencyUtils.convert(v.subtotal, v.currency, prefCurrency, usdRate, eurRate)
                val totalAmountConverted = CurrencyUtils.convert(v.totalAmount, v.currency, prefCurrency, usdRate, eurRate)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(UiTranslations.getString(context, "subtotal_label", language), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$prefCurrency ${String.format("%.2f", subtotalConverted)}", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        val discSuffix = if (v.discountType == DiscountType.PERCENTAGE) " (${v.discountValue}%)" else ""
                        Text(
                            "${UiTranslations.getString(context, "discount_label", language)}$discSuffix",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val discAmountConverted = if (v.discountType == DiscountType.PERCENTAGE)
                            (subtotalConverted * v.discountValue / 100.0)
                        else
                            CurrencyUtils.convert(v.discountValue, v.currency, prefCurrency, usdRate, eurRate)

                        Text("- $prefCurrency ${String.format("%.2f", discAmountConverted)}", color = Color(0xFFD32F2F))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            UiTranslations.getString(context, "total_label", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "$prefCurrency ${String.format("%.2f", totalAmountConverted)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            val statusSection: @Composable () -> Unit = {
                // "Enviado" si la visita ya está en la nube (tiene id de servidor).
                val isSent = v.remoteId != null
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = (if (isSent) Color(0xFF4CAF50) else Color(0xFFFF9800)).copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, (if (isSent) Color(0xFF4CAF50) else Color(0xFFFF9800)).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSent) Icons.Default.CheckCircle else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isSent) Color(0xFF2E7D32) else Color(0xFFE65100),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isSent) UiTranslations.getString(context, "sent_record", language) else UiTranslations.getString(context, "pending_record", language),
                            color = if (isSent) Color(0xFF2E7D32) else Color(0xFFE65100),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (isLandscape) {
                // Horizontal: izquierda cliente + productos, derecha totales + estado.
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        header()
                        Spacer(modifier = Modifier.height(24.dp))
                        productsSection()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        totalsSection()
                        Spacer(modifier = Modifier.height(32.dp))
                        statusSection()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    header()
                    Spacer(modifier = Modifier.height(32.dp))
                    productsSection()
                    Spacer(modifier = Modifier.height(24.dp))
                    totalsSection()
                    Spacer(modifier = Modifier.height(48.dp))
                    statusSection()
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
