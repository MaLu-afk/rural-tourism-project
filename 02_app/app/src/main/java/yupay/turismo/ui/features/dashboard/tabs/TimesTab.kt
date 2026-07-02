package yupay.turismo.ui.features.dashboard.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import yupay.turismo.ui.components.VicoColumnChart
import yupay.turismo.ui.features.dashboard.WEEKDAY_KEYS
import yupay.turismo.ui.features.dashboard.components.ChartSection
import yupay.turismo.ui.features.dashboard.components.DashboardSection
import yupay.turismo.ui.features.dashboard.components.DashboardSectionsLayout
import yupay.turismo.ui.features.dashboard.components.KpiData
import yupay.turismo.ui.features.dashboard.components.KpiGrid
import yupay.turismo.utils.UiTranslations

@Composable
internal fun TimesTab(
    language: String,
    peakHours: List<Pair<Int, Int>>,
    visitsByWeekday: List<Int>,
    partOfDay: List<Int>
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val visibleHours = peakHours.filter { it.first in 7..21 }

    // KPIs derivados de los datos disponibles.
    val busiestHour = peakHours.maxByOrNull { it.second }?.takeIf { it.second > 0 }
    val busiestDayIdx = visitsByWeekday.indices.maxByOrNull { visitsByWeekday[it] }
        ?.takeIf { visitsByWeekday[it] > 0 }

    val busiestHourValue = busiestHour?.let { "${it.first}h" } ?: "-"
    val busiestDayValue = busiestDayIdx?.let {
        UiTranslations.getString(context, WEEKDAY_KEYS[it], language)
    } ?: "-"

    val kpis = listOf(
        KpiData(
            UiTranslations.getString(context, "insights_busiest_hour", language),
            busiestHourValue,
            scheme.primaryContainer, scheme.onPrimaryContainer
        ),
        KpiData(
            UiTranslations.getString(context, "insights_busiest_day", language),
            busiestDayValue,
            scheme.secondaryContainer, scheme.onSecondaryContainer
        )
    )

    val partOfDayLabels = listOf(
        UiTranslations.getString(context, "insights_morning", language),
        UiTranslations.getString(context, "insights_afternoon", language),
        UiTranslations.getString(context, "insights_evening", language)
    )

    val sections = listOf(
        DashboardSection(fullWidth = true) { KpiGrid(kpis) },
        DashboardSection {
            ChartSection(UiTranslations.getString(context, "insights_peak_hours", language)) {
                VicoColumnChart(
                    values = visibleHours.map { it.second.toFloat() },
                    labels = visibleHours.map { "${it.first}h" },
                    height = 200.dp,
                    emptyText = UiTranslations.getString(context, "insights_no_data", language)
                )
            }
        },
        DashboardSection {
            ChartSection(UiTranslations.getString(context, "insights_part_of_day", language)) {
                VicoColumnChart(
                    values = partOfDay.map { it.toFloat() },
                    labels = partOfDayLabels,
                    emptyText = UiTranslations.getString(context, "insights_no_data", language)
                )
            }
        }
    )

    DashboardSectionsLayout(sections)
}
