package yupay.turismo.ui.features.dashboard

import android.content.Context
import yupay.turismo.utils.UiTranslations

internal val WEEKDAY_KEYS = listOf(
    "day_mon", "day_tue", "day_wed", "day_thu", "day_fri", "day_sat", "day_sun"
)

internal val MONTH_KEYS = listOf(
    "month_jan", "month_feb", "month_mar", "month_apr", "month_may", "month_jun",
    "month_jul", "month_aug", "month_sep", "month_oct", "month_nov", "month_dec"
)

enum class DashboardFilter { ALL, LAST_7_DAYS, THIS_MONTH, THIS_YEAR }

enum class DashboardTab(val labelKey: String) {
    SUMMARY("insights_tab_summary"),
    VISITORS("insights_tab_visitors"),
    SALES("insights_tab_sales"),
    TIMES("insights_tab_times")
}

enum class RevenueGranularity { DAY, WEEK_OF_MONTH, MONTH }

data class RevenueSeries(
    val granularity: RevenueGranularity,
    val points: List<Pair<Int, Double>>
)

internal fun seriesLabel(
    context: Context,
    language: String,
    granularity: RevenueGranularity,
    index: Int
): String = when (granularity) {
    RevenueGranularity.DAY ->
        UiTranslations.getString(context, WEEKDAY_KEYS.getOrElse(index) { "day_mon" }, language)
    RevenueGranularity.WEEK_OF_MONTH ->
        UiTranslations.getString(context, "chart_week_label", language, index)
    RevenueGranularity.MONTH ->
        UiTranslations.getString(context, MONTH_KEYS.getOrElse(index) { "month_jan" }, language)
}
