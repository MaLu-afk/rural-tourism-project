package yupay.turismo.ui.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import yupay.turismo.data.local.Visit
import yupay.turismo.ui.components.VicoColumnChart
import yupay.turismo.ui.theme.Final_projectTheme
import yupay.turismo.ui.navigation.Routes
import yupay.turismo.utils.CurrencyUtils
import yupay.turismo.utils.UiTranslations
import java.util.Calendar

enum class TimeView { DAY, WEEK, MONTH, YEAR }

@Composable
fun HomeScreen(
    businessName: String,
    selectedService: String,
    entrepreneurTips: String,
    profilePicture: ByteArray?,
    visits: List<Visit>,
    preferredCurrency: String,
    usdRate: Double,
    eurRate: Double,
    language: String,
    onNavigateToTip: () -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var selectedView by remember { mutableStateOf(TimeView.MONTH) }
    val context = LocalContext.current

    val chartData = remember(visits, selectedView, language) {
        processChartData(visits, selectedView, context, language)
    }

    val maxCount = chartData.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = UiTranslations.getString(context, "visits_add", language)
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        if (isLandscape) {
            LandscapeHomeContent(
                paddingValues, businessName, selectedService, entrepreneurTips,
                profilePicture, visits, preferredCurrency, usdRate, eurRate,
                selectedView, chartData, maxCount, language,
                onViewChange = { selectedView = it },
                onNavigateToTip = onNavigateToTip,
                onNavigateToDashboard = onNavigateToDashboard,
                onNavigate = onNavigate
            )
        } else {
            PortraitHomeContent(
                paddingValues, businessName, selectedService, entrepreneurTips,
                profilePicture, visits, preferredCurrency, usdRate, eurRate,
                selectedView, chartData, maxCount, language,
                onViewChange = { selectedView = it },
                onNavigateToTip = onNavigateToTip,
                onNavigateToDashboard = onNavigateToDashboard,
                onNavigate = onNavigate
            )
        }
    }
}

@Composable
fun PortraitHomeContent(
    paddingValues: PaddingValues,
    businessName: String,
    selectedService: String,
    entrepreneurTips: String,
    profilePicture: ByteArray?,
    visits: List<Visit>,
    preferredCurrency: String,
    usdRate: Double,
    eurRate: Double,
    selectedView: TimeView,
    chartData: List<Pair<String, Int>>,
    maxCount: Int,
    language: String,
    onViewChange: (TimeView) -> Unit,
    onNavigateToTip: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        HomeHeader(businessName, selectedService, profilePicture, language)
        Spacer(modifier = Modifier.height(24.dp))
        TimeViewSelector(selectedView, onViewChange, language)
        ChartCard(selectedView, chartData, maxCount, language, onNavigateToDashboard)
        Spacer(modifier = Modifier.height(24.dp))
        TipCard(entrepreneurTips, language, onNavigateToTip)
        Spacer(modifier = Modifier.height(24.dp))
        RecentVisitsCard(visits, preferredCurrency, usdRate, eurRate, language, onNavigate)
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun LandscapeHomeContent(
    paddingValues: PaddingValues,
    businessName: String,
    selectedService: String,
    entrepreneurTips: String,
    profilePicture: ByteArray?,
    visits: List<Visit>,
    preferredCurrency: String,
    usdRate: Double,
    eurRate: Double,
    selectedView: TimeView,
    chartData: List<Pair<String, Int>>,
    maxCount: Int,
    language: String,
    onViewChange: (TimeView) -> Unit,
    onNavigateToTip: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            HomeHeader(businessName, selectedService, profilePicture, language)
            Spacer(modifier = Modifier.height(24.dp))
            TimeViewSelector(selectedView, onViewChange, language)
            ChartCard(selectedView, chartData, maxCount, language, onNavigateToDashboard)
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        Spacer(modifier = Modifier.width(24.dp))
        
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            TipCard(entrepreneurTips, language, onNavigateToTip)
            Spacer(modifier = Modifier.height(24.dp))
            RecentVisitsCard(visits, preferredCurrency, usdRate, eurRate, language, onNavigate)
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun HomeHeader(businessName: String, selectedService: String, profilePicture: ByteArray?, language: String) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (profilePicture != null) {
                AsyncImage(
                    model = profilePicture,
                    contentDescription = UiTranslations.getString(context, "home_cd_profile_photo", language),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = UiTranslations.getString(context, "home_greeting", language, businessName),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = UiTranslations.getString(context, "home_category", language, UiTranslations.translateService(selectedService, language, context)),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun TimeViewSelector(selectedView: TimeView, onViewChange: (TimeView) -> Unit, language: String) {
    val context = LocalContext.current
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        TimeView.entries.forEachIndexed { index, view ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = TimeView.entries.size),
                onClick = { onViewChange(view) },
                selected = selectedView == view,
                label = { 
                    Text(
                        when(view) {
                            TimeView.DAY -> UiTranslations.getString(context, "time_day", language)
                            TimeView.WEEK -> UiTranslations.getString(context, "time_week", language)
                            TimeView.MONTH -> UiTranslations.getString(context, "time_month", language)
                            TimeView.YEAR -> UiTranslations.getString(context, "time_year", language)
                        },
                        fontSize = 12.sp
                    ) 
                }
            )
        }
    }
}

@Composable
fun ChartCard(selectedView: TimeView, chartData: List<Pair<String, Int>>, maxCount: Int, language: String, onExpand: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when(selectedView) {
                        TimeView.DAY -> UiTranslations.getString(context, "chart_today", language)
                        TimeView.WEEK -> UiTranslations.getString(context, "chart_week", language)
                        TimeView.MONTH -> UiTranslations.getString(context, "chart_month", language)
                        TimeView.YEAR -> UiTranslations.getString(context, "chart_year", language)
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onExpand) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = UiTranslations.getString(context, "map_expand", language),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            VicoColumnChart(
                values = chartData.map { it.second.toFloat() },
                labels = chartData.map { it.first },
                height = 160.dp,
                emptyText = UiTranslations.getString(context, "home_no_records", language)
            )
        }
    }
}

@Composable
fun TipCard(entrepreneurTips: String, language: String, onNavigateToTip: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = UiTranslations.getString(context, "tip_detail_title", language),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = entrepreneurTips.ifEmpty { UiTranslations.getString(context, "home_tip_fallback", language) },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onNavigateToTip,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = UiTranslations.getString(context, "home_cd_play", language),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun RecentVisitsCard(visits: List<Visit>, preferredCurrency: String, usdRate: Double, eurRate: Double, language: String, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = UiTranslations.getString(context, "home_recent_visits", language),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = UiTranslations.getString(context, "home_see_all", language),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onNavigate(Routes.VISITS) }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(UiTranslations.getString(context, "home_tourist_country", language), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(UiTranslations.getString(context, "total_label", language), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                
                if (visits.isEmpty()) {
                    Text(
                        text = UiTranslations.getString(context, "home_no_records", language),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    visits.take(3).forEach { visit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(Routes.visitDetail(visit.id)) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(text = visit.nationalityFlag, fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = visit.nationality,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = visit.selectedProducts.take(2).joinToString(", ") { it.name },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            val convertedTotal = CurrencyUtils.convert(visit.totalAmount, visit.currency, preferredCurrency, usdRate, eurRate)
                            Text(
                                text = "$preferredCurrency ${String.format("%.2f", convertedTotal)}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            )
                        }
                        if (visit != visits.take(3).last()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }
    }
}

private fun processChartData(visits: List<Visit>, view: TimeView, context: android.content.Context, language: String): List<Pair<String, Int>> {
    val calendar = Calendar.getInstance()
    val now = calendar.timeInMillis
    
    return when (view) {
        TimeView.DAY -> {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val isDay = hour in 8..19
            val startHour = if (isDay) 8 else 20
            (0..11).map { i ->
                val h = (startHour + i) % 24
                val label = "${h}h"
                val count = visits.count { v ->
                    calendar.timeInMillis = v.registrationDate
                    calendar.get(Calendar.HOUR_OF_DAY) == h && 
                    isSameDay(v.registrationDate, now)
                }
                label to count
            }
        }
        TimeView.WEEK -> {
            val dayKeys = listOf("day_mon", "day_tue", "day_wed", "day_thu", "day_fri", "day_sat", "day_sun")
            dayKeys.mapIndexed { index, key ->
                val name = UiTranslations.getString(context, key, language)
                val count = visits.count { v ->
                    calendar.timeInMillis = v.registrationDate
                    val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // L=0, D=6
                    dayOfWeek == index && isSameWeek(v.registrationDate, now)
                }
                name to count
            }
        }
        TimeView.MONTH -> {
            (1..4).map { week ->
                val label = UiTranslations.getString(context, "chart_week_label", language, week)
                val count = visits.count { v ->
                    calendar.timeInMillis = v.registrationDate
                    val weekOfMonth = ((calendar.get(Calendar.DAY_OF_MONTH) - 1) / 7) + 1
                    weekOfMonth == week && isSameMonth(v.registrationDate, now)
                }
                label to count
            }
        }
        TimeView.YEAR -> {
            val monthKeys = listOf(
                "month_jan", "month_feb", "month_mar", "month_apr", "month_may", "month_jun",
                "month_jul", "month_aug", "month_sep", "month_oct", "month_nov", "month_dec"
            )
            monthKeys.mapIndexed { index, key ->
                val name = UiTranslations.getString(context, key, language)
                val count = visits.count { v ->
                    calendar.timeInMillis = v.registrationDate
                    calendar.get(Calendar.MONTH) == index && isSameYear(v.registrationDate, now)
                }
                name to count
            }
        }
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

private fun isSameWeek(t1: Long, t2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.WEEK_OF_YEAR) == c2.get(Calendar.WEEK_OF_YEAR)
}

private fun isSameMonth(t1: Long, t2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)
}

private fun isSameYear(t1: Long, t2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
}

@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
fun HomeLandscapePreview() {
    Final_projectTheme {
        HomeScreen(
            businessName = "Tienda Tablet",
            selectedService = "Varios",
            entrepreneurTips = "Usa el modo horizontal para ver más datos a la vez.",
            profilePicture = null,
            visits = emptyList(),
            preferredCurrency = "S/",
            usdRate = 3.8,
            eurRate = 4.1,
            language = "Español",
            onNavigateToTip = {},
            onNavigateToAdd = {},
            onNavigateToDashboard = {},
            onNavigate = {}
        )
    }
}
