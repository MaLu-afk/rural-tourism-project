 package yupay.turismo.ui.features.visits

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import yupay.turismo.data.local.Visit
import yupay.turismo.ui.MainViewModel
import yupay.turismo.utils.CurrencyUtils
import yupay.turismo.utils.UiTranslations
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun VisitsScreen(
    viewModel: MainViewModel,
    onNavigateToAdd: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    onNavigate: (String) -> Unit
) {
    val visits by viewModel.allVisits.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val language = settings?.language ?: "Español"
    val context = LocalContext.current
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600

    val groupedVisits = remember(visits, language) {
        groupVisits(visits, language, context)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = UiTranslations.getString(context, "visits_add", language))
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = if (isLandscape) 48.dp else 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = UiTranslations.getString(context, "visits_title", language),
                    fontSize = if (isLandscape) 32.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 36.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = UiTranslations.getString(context, "total_records", language, visits.size),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (visits.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = UiTranslations.getString(context, "no_records_yet", language),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxWidth(if (isLandscape) 0.8f else 1f).align(Alignment.CenterHorizontally)
                ) {
                    groupedVisits.forEach { entry ->
                        val category = entry.key
                        val visitsInCategory = entry.value
                        
                        item {
                            Text(
                                text = category,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(visitsInCategory) { visit ->
                            VisitItem(
                                visit = visit,
                                preferredCurrency = settings?.preferredCurrency ?: "S/",
                                usdRate = settings?.usdExchangeRate ?: 3.8,
                                eurRate = settings?.eurExchangeRate ?: 4.1,
                                language = language,
                                onClick = { onNavigateToDetail(visit.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun groupVisits(visits: List<Visit>, language: String, context: Context): Map<String, List<Visit>> {
    val grouped = mutableMapOf<String, MutableList<Visit>>()
    val now = Calendar.getInstance()
    
    val sortedVisits = visits.sortedByDescending { it.registrationDate }
    
    sortedVisits.forEach { visit ->
        val visitDate = Calendar.getInstance().apply { timeInMillis = visit.registrationDate }
        
        val categoryKey = when {
            isSameDay(now, visitDate) -> "cat_today"
            isYesterday(now, visitDate) -> "cat_yesterday"
            isSameWeek(now, visitDate) -> "cat_this_week"
            isSameMonth(now, visitDate) -> "cat_this_month"
            isSameYear(now, visitDate) -> "cat_this_year"
            else -> "cat_older"
        }
        val category = UiTranslations.getString(context, categoryKey, language)
        
        grouped.getOrPut(category) { mutableListOf() }.add(visit)
    }
    
    val result = LinkedHashMap<String, List<Visit>>()
    listOf("cat_today", "cat_yesterday", "cat_this_week", "cat_this_month", "cat_this_year", "cat_older").forEach { key ->
        val cat = UiTranslations.getString(context, key, language)
        grouped[cat]?.let { result[cat] = it }
    }
    return result
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(today: Calendar, date: Calendar): Boolean {
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = today.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return isSameDay(yesterday, date)
}

private fun isSameWeek(today: Calendar, date: Calendar): Boolean {
    return today.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
           today.get(Calendar.WEEK_OF_YEAR) == date.get(Calendar.WEEK_OF_YEAR)
}

private fun isSameMonth(today: Calendar, date: Calendar): Boolean {
    return today.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
           today.get(Calendar.MONTH) == date.get(Calendar.MONTH)
}

private fun isSameYear(today: Calendar, date: Calendar): Boolean {
    return today.get(Calendar.YEAR) == date.get(Calendar.YEAR)
}

@Composable
fun VisitItem(visit: Visit, preferredCurrency: String, usdRate: Double, eurRate: Double, language: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val convertedTotal = CurrencyUtils.convert(visit.totalAmount, visit.currency, preferredCurrency, usdRate, eurRate)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(visit.nationalityFlag, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${UiTranslations.getString(context, "tourist_label", language)} - ${visit.nationality}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$preferredCurrency ${String.format("%.2f", convertedTotal)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (visit.remoteId != null) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = UiTranslations.getString(context, "visits_cd_sent", language),
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = UiTranslations.getString(context, "visits_cd_pending", language),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

fun getTimeAgo(timestamp: Long, language: String, context: Context): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        minutes < 1 -> UiTranslations.getString(context, "time_now", language)
        minutes < 60 -> UiTranslations.getString(context, "time_min_ago", language, minutes.toInt())
        hours < 24 -> if (hours == 1L) UiTranslations.getString(context, "time_hour_ago", language) else UiTranslations.getString(context, "time_hours_ago", language, hours.toInt())
        days == 1L -> UiTranslations.getString(context, "time_yesterday", language)
        days < 7 -> UiTranslations.getString(context, "time_days_ago", language, days.toInt())
        else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
