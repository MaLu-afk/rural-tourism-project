package yupay.turismo.ui.features.map

import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import yupay.turismo.data.local.Visit
import yupay.turismo.ui.MainViewModel
import kotlin.math.ln
import kotlin.math.max

enum class MapViewMode {
    POINTS, BUBBLES
}

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    visits: List<Visit>,
    isInteractive: Boolean = true,
    zoomLevel: Double = 2.0,
    center: GeoPoint = GeoPoint(0.0, 0.0),
    showLabels: Boolean = false,
    viewMode: MapViewMode = MapViewMode.POINTS,
    productColors: Map<String, Int> = emptyMap()
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val configuration = LocalConfiguration.current
    
    val countryFeatures by viewModel.countryFeatures.collectAsState()

    val waterColor = if (isDark) 0xFF0A1120.toInt() else 0xFFE0F7FA.toInt()
    val countryFillColor = if (isDark) 0xFF1B253D.toInt() else 0xFFF0F0F0.toInt()
    val countryStrokeColor = if (isDark) 0xFF2A3A5A.toInt() else 0xFFE0E0E0.toInt()
    val textColor = if (isDark) 0xFF8BA4D0.toInt() else 0xFF000000.toInt()

    val colorPrimary = MaterialTheme.colorScheme.primary.toArgb()

    var baseZoom by remember { mutableDoubleStateOf(1.0) }
    var hasZoomedToFit by remember { mutableStateOf(false) }

    val countryOverlays = remember(countryFeatures, countryFillColor, countryStrokeColor) {
        countryFeatures.flatMap { feature ->
            feature.polygons.filter { it.isNotEmpty() }.map { points ->
                Polygon().apply {
                    this.points = points
                    fillPaint.color = countryFillColor
                    outlinePaint.color = countryStrokeColor
                    outlinePaint.strokeWidth = 1f
                }
            }
        }
    }

    val mapping = remember {
        mapOf(
            "Perú" to "Peru", "México" to "Mexico", "España" to "Spain",
            "Estados Unidos" to "United States", "Brasil" to "Brazil",
            "Francia" to "France", "Alemania" to "Germany", "Reino Unido" to "United Kingdom"
        )
    }

    // Mayor total agregado (país, producto): normaliza el radio de las burbujas para que la mayor
    // llegue al radio máximo.
    val bubbleMax = remember(visits) {
        var maxValue = 1
        visits.groupBy { mapping[it.nationality] ?: it.nationality }.forEach { (_, countryVisits) ->
            val perProduct = mutableMapOf<String, Int>()
            countryVisits.forEach { v -> v.selectedProducts.forEach { perProduct[it.name] = perProduct.getOrDefault(it.name, 0) + it.quantity } }
            perProduct.values.forEach { if (it > maxValue) maxValue = it }
        }
        maxValue.toFloat()
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setUseDataConnection(false)
                    setMultiTouchControls(isInteractive)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    isHorizontalMapRepetitionEnabled = true
                    overlayManager.tilesOverlay.isEnabled = false
                    controller.setZoom(zoomLevel)
                    controller.setCenter(center)
                }
            },
            update = { mapView ->
                mapView.setBackgroundColor(waterColor)
                
                if (mapView.height > 0 && !hasZoomedToFit) {
                    val targetPixelWidth = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) mapView.height.toDouble() else mapView.width.toDouble()
                    baseZoom = max(1.0, ln(targetPixelWidth / 256.0) / ln(2.0))
                    mapView.minZoomLevel = baseZoom
                    mapView.controller.setZoom(if (isInteractive) zoomLevel else baseZoom)
                    hasZoomedToFit = true
                }

                val currentOverlays = mapView.overlays
                if (currentOverlays.isEmpty()) {
                    currentOverlays.addAll(countryOverlays)
                }

                val dynamicOverlay = object : Overlay() {
                    private val pointPaint = Paint().apply { isAntiAlias = true }
                    private val textPaint = Paint().apply {
                        color = textColor
                        textSize = 24f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = Typeface.DEFAULT_BOLD
                    }

                    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
                        if (shadow) return
                        
                        if (viewMode == MapViewMode.POINTS) {
                            // Un punto por UNIDAD de producto consumida (no por visita): una visita con
                            // 3 de A y 2 de B aporta 5 puntos en ese país, coloreados por producto.
                            visits.forEach { visit ->
                                val jsonName = mapping[visit.nationality] ?: visit.nationality
                                val feat = countryFeatures.find { it.name == jsonName } ?: return@forEach
                                var unit = 0
                                visit.selectedProducts.forEach { product ->
                                    val color = productColors[product.name] ?: colorPrimary
                                    repeat(product.quantity) {
                                        val gp = stableUnitPoint(visit, feat, product.id, unit++)
                                        val point = mapView.projection.toPixels(gp, null)
                                        pointPaint.color = color
                                        pointPaint.alpha = 255
                                        pointPaint.style = Paint.Style.FILL
                                        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 8f, pointPaint)
                                    }
                                }
                            }
                        } else {
                            // Una burbuja por producto y país: tamaño según unidades (más consumido =
                            // más grande) y color por producto (igual que la leyenda).
                            visits.groupBy { mapping[it.nationality] ?: it.nationality }.forEach { (countryName, countryVisits) ->
                                val feature = countryFeatures.find { it.name == countryName } ?: return@forEach
                                val prodCounts = mutableMapOf<String, Int>()
                                countryVisits.forEach { v -> v.selectedProducts.forEach { prodCounts[it.name] = prodCounts.getOrDefault(it.name, 0) + it.quantity } }

                                val top = prodCounts.toList().sortedByDescending { it.second }.take(4)
                                top.forEachIndexed { index, (name, count) ->
                                    val gp = getStablePointForService(feature, index, top.size)
                                    val point = mapView.projection.toPixels(gp, null)
                                    val radius = 15f + (count.toFloat() / bubbleMax) * 30f
                                    pointPaint.color = productColors[name] ?: colorPrimary
                                    pointPaint.alpha = 160
                                    pointPaint.style = Paint.Style.FILL
                                    canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, pointPaint)
                                }
                            }
                        }

                        if (showLabels && mapView.zoomLevelDouble > baseZoom + 1) {
                            countryFeatures.forEach { feature ->
                                feature.centroid?.let { centroid ->
                                    val point = mapView.projection.toPixels(centroid, null)
                                    canvas.drawText(feature.name, point.x.toFloat(), point.y.toFloat(), textPaint)
                                }
                            }
                        }
                    }
                }
                
                if (currentOverlays.isNotEmpty() && currentOverlays.last() !is Polygon) {
                    currentOverlays.removeAt(currentOverlays.size - 1)
                }
                currentOverlays.add(dynamicOverlay)
                mapView.postInvalidate()
            }
        )
    }
}

private fun getStablePointForService(feature: yupay.turismo.data.model.CountryFeature, index: Int, total: Int): GeoPoint {
    val polygon = feature.polygons.maxByOrNull { it.size } ?: return feature.centroid ?: GeoPoint(0.0, 0.0)
    val step = max(1, polygon.size / max(1, total))
    val v = polygon[(index * step) % polygon.size]
    val c = feature.centroid ?: v
    return GeoPoint((v.latitude + c.latitude) / 2.0, (v.longitude + c.longitude) / 2.0)
}

/**
 * Posición estable y repartida para CADA unidad de producto: combina visita, producto y nº de unidad
 * en un hash determinista que elige un vértice del país y lo mezcla hacia el centroide con leve
 * variación, de modo que las unidades no se solapen exactamente ni salten entre redibujados.
 */
private fun stableUnitPoint(visit: Visit, feature: yupay.turismo.data.model.CountryFeature, productId: Int, unitIndex: Int): GeoPoint {
    val polygon = feature.polygons.maxByOrNull { it.size } ?: return feature.centroid ?: GeoPoint(0.0, 0.0)
    val seed = (visit.id * 73856093) xor (productId * 19349663) xor ((unitIndex + 1) * 83492791)
    val v = polygon[Math.floorMod(seed, polygon.size)]
    val c = feature.centroid ?: v
    val t = 0.55 + Math.floorMod(seed / 7, 30) / 100.0 // 0.55..0.84 hacia el centroide
    return GeoPoint(v.latitude * (1 - t) + c.latitude * t, v.longitude * (1 - t) + c.longitude * t)
}
