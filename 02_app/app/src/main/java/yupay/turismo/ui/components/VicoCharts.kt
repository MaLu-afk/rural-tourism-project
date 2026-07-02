package yupay.turismo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.common.data.ExtraStore

private val LABEL_LIST_KEY = ExtraStore.Key<List<String>>()

@Composable
fun VicoColumnChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    emptyText: String = "—"
) {
    if (values.isEmpty() || values.all { it <= 0f }) {
        EmptyChart(emptyText, height, modifier)
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values, labels) {
        modelProducer.runTransaction {
            columnSeries { series(values.map { it.toDouble() }) }
            extras { it[LABEL_LIST_KEY] = labels }
        }
    }

    val valueFormatter = remember {
        CartesianValueFormatter { context, value, _ ->
            context.model.extraStore.getOrNull(LABEL_LIST_KEY)?.getOrNull(value.toInt()) ?: ""
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = valueFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(height)
    )
}

@Composable
fun VicoLineChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    emptyText: String = "—"
) {
    if (values.isEmpty() || values.all { it <= 0f }) {
        EmptyChart(emptyText, height, modifier)
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values, labels) {
        modelProducer.runTransaction {
            lineSeries { series(values.map { it.toDouble() }) }
            extras { it[LABEL_LIST_KEY] = labels }
        }
    }

    val valueFormatter = remember {
        CartesianValueFormatter { context, value, _ ->
            context.model.extraStore.getOrNull(LABEL_LIST_KEY)?.getOrNull(value.toInt()) ?: ""
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = valueFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(height)
    )
}

private fun rememberLabelFormatter(labels: List<String>): CartesianValueFormatter =
    CartesianValueFormatter { _, value, _ -> labels.getOrNull(value.toInt()) ?: "" }

@Composable
private fun EmptyChart(text: String, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
