package yupay.turismo.ui.features.dashboard.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yupay.turismo.ui.components.VicoColumnChart
import yupay.turismo.ui.components.VicoLineChart
import yupay.turismo.ui.features.dashboard.RevenueSeries
import yupay.turismo.ui.features.dashboard.WEEKDAY_KEYS
import yupay.turismo.ui.features.dashboard.components.ChartSection
import yupay.turismo.ui.features.dashboard.components.DashboardSection
import yupay.turismo.ui.features.dashboard.components.DashboardSectionsLayout
import yupay.turismo.ui.features.dashboard.seriesLabel
import yupay.turismo.utils.UiTranslations

@Composable
internal fun VisitorsTab(
    language: String,
    topNationalities: List<Pair<Pair<String, String>, Int>>,
    visitsByWeekday: List<Int>,
    visitorsSeries: RevenueSeries
) {
    val context = LocalContext.current
    var showAll by remember { mutableStateOf(false) }
    val shown = if (showAll) topNationalities.take(10) else topNationalities.take(5)

    val sections = listOf(
        DashboardSection {
            ChartSection(UiTranslations.getString(context, "insights_top_countries", language)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showAll,
                        onClick = { showAll = false },
                        label = { Text(UiTranslations.getString(context, "insights_top_5", language), fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = showAll,
                        onClick = { showAll = true },
                        label = { Text(UiTranslations.getString(context, "insights_top_10", language), fontSize = 12.sp) }
                    )
                }
                VicoColumnChart(
                    values = shown.map { it.second.toFloat() },
                    labels = shown.map { it.first.second }, // bandera (emoji) como etiqueta
                    emptyText = UiTranslations.getString(context, "insights_no_data", language)
                )
            }
        },
        DashboardSection {
            ChartSection(UiTranslations.getString(context, "insights_visits_weekday", language)) {
                VicoColumnChart(
                    values = visitsByWeekday.map { it.toFloat() },
                    labels = WEEKDAY_KEYS.map { UiTranslations.getString(context, it, language) },
                    emptyText = UiTranslations.getString(context, "insights_no_data", language)
                )
            }
        },
        DashboardSection {
            ChartSection(UiTranslations.getString(context, "insights_visitors_over_time", language)) {
                VicoLineChart(
                    values = visitorsSeries.points.map { it.second.toFloat() },
                    labels = visitorsSeries.points.map {
                        seriesLabel(context, language, visitorsSeries.granularity, it.first)
                    },
                    emptyText = UiTranslations.getString(context, "insights_no_data", language)
                )
            }
        }
    )

    DashboardSectionsLayout(sections)
}
