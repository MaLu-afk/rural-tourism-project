package yupay.turismo.ui.features.dashboard.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import yupay.turismo.ui.components.VicoLineChart
import yupay.turismo.ui.features.dashboard.RevenueSeries
import yupay.turismo.ui.features.dashboard.components.ChartSection
import yupay.turismo.ui.features.dashboard.components.DashboardSection
import yupay.turismo.ui.features.dashboard.components.DashboardSectionsLayout
import yupay.turismo.ui.features.dashboard.components.KpiData
import yupay.turismo.ui.features.dashboard.components.KpiGrid
import yupay.turismo.ui.features.dashboard.components.RevenueEstimateCard
import yupay.turismo.ui.features.dashboard.seriesLabel
import yupay.turismo.utils.UiTranslations

@Composable
internal fun SummaryTab(
    language: String,
    total: Int,
    leader: String,
    star: String,
    averageTicket: Double,
    totalItems: Int,
    totalRevenue: Double,
    revenue: Map<String, Double>,
    revenueSeries: RevenueSeries
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    val kpis = listOf(
        KpiData(
            UiTranslations.getString(context, "insights_total_visitors", language),
            total.toString(),
            scheme.primaryContainer, scheme.onPrimaryContainer
        ),
        KpiData(
            UiTranslations.getString(context, "insights_leader_country", language),
            leader,
            scheme.secondaryContainer, scheme.onSecondaryContainer
        ),
        KpiData(
            UiTranslations.getString(context, "insights_star_service", language),
            star,
            scheme.tertiaryContainer, scheme.onTertiaryContainer
        ),
        KpiData(
            UiTranslations.getString(context, "insights_avg_ticket", language),
            "S/ ${String.format("%.2f", averageTicket)}",
            scheme.surfaceVariant, scheme.onSurfaceVariant
        ),
        KpiData(
            UiTranslations.getString(context, "insights_total_items", language),
            totalItems.toString(),
            scheme.primaryContainer, scheme.onPrimaryContainer
        ),
        KpiData(
            UiTranslations.getString(context, "insights_total_revenue", language),
            "S/ ${String.format("%.2f", totalRevenue)}",
            scheme.secondaryContainer, scheme.onSecondaryContainer
        )
    )

    val sections = buildList {
        add(DashboardSection(fullWidth = true) { KpiGrid(kpis) })
        if (revenue.isNotEmpty()) {
            add(DashboardSection { RevenueEstimateCard(revenue, language) })
        }
        add(
            DashboardSection {
                ChartSection(UiTranslations.getString(context, "insights_revenue_over_time", language)) {
                    VicoLineChart(
                        values = revenueSeries.points.map { it.second.toFloat() },
                        labels = revenueSeries.points.map {
                            seriesLabel(context, language, revenueSeries.granularity, it.first)
                        },
                        height = 180.dp,
                        emptyText = UiTranslations.getString(context, "insights_no_data", language)
                    )
                }
            }
        )
    }

    DashboardSectionsLayout(sections)
}
