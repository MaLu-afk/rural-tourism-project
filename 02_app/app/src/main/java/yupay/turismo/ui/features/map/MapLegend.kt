package yupay.turismo.ui.features.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import yupay.turismo.data.local.Visit

/**
 * Fuente única del orden y los colores de los productos en el mapa: la usan por igual la leyenda
 * ([MapScreen]/[FullscreenMapScreen]) y el dibujo de puntos/burbujas ([OsmMapView]). Así el color de
 * cada producto coincide en los tres sitios.
 */
data class ProductLegend(
    /** Productos (nombre, unidades totales) ordenados de mayor a menor consumo. Sin recorte. */
    val ordered: List<Pair<String, Int>>,
    /** Paleta indexada para la leyenda (Compose Color). */
    val palette: List<Color>,
    /** Color por nombre de producto en formato ARGB, para pintar en el Canvas del mapa. */
    val colorsArgb: Map<String, Int>,
) {
    /** Color de la leyenda para el producto en la posición [index] (cicla si hay más que colores). */
    fun colorAt(index: Int): Color = palette[index % palette.size]
}

/**
 * Suma las unidades (`quantity`) de cada producto a lo largo de todas las visitas y las ordena de
 * mayor a menor. Devuelve TODOS los productos (la leyenda decide cuántos mostrar y permite scroll).
 */
fun orderedProductCounts(visits: List<Visit>): List<Pair<String, Int>> {
    val counts = LinkedHashMap<String, Int>()
    visits.forEach { visit ->
        visit.selectedProducts.forEach { item ->
            counts[item.name] = (counts[item.name] ?: 0) + item.quantity
        }
    }
    return counts.toList().sortedByDescending { it.second }
}

/**
 * Paleta de colores para los productos: arranca con los del tema (primary/secondary/tertiary) y añade
 * colores fijos bien diferenciados, visibles tanto en mapa claro como oscuro. Permite >3 productos
 * con color propio (la leyenda muestra hasta 4 con scroll).
 */
@Composable
fun rememberProductPalette(): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    return remember(primary, secondary, tertiary) {
        listOf(
            primary,
            secondary,
            tertiary,
            Color(0xFF00897B), // teal
            Color(0xFFF9A825), // ámbar
            Color(0xFF8E24AA), // púrpura
            Color(0xFFD81B60), // rosa
            Color(0xFF3949AB), // índigo
        )
    }
}

/**
 * Calcula el orden de productos y el mapa de colores listos para usar en una pantalla del mapa.
 * Reutilizar en [MapScreen] y [FullscreenMapScreen] para no duplicar la lógica ni que diverjan.
 */
@Composable
fun rememberProductLegend(visits: List<Visit>): ProductLegend {
    val palette = rememberProductPalette()
    return remember(visits, palette) {
        val ordered = orderedProductCounts(visits)
        val colorsArgb = ordered
            .mapIndexed { i, (name, _) -> name to palette[i % palette.size].toArgb() }
            .toMap()
        ProductLegend(ordered = ordered, palette = palette, colorsArgb = colorsArgb)
    }
}
