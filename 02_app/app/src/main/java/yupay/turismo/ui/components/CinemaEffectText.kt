package yupay.turismo.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun CinemaEffectText(
    text: String,
    currentTime: Long,
    totalDuration: Long,
    readingTimePerLine: Long = 3000L, // fallback cuando no hay audio (durationMs == 0)
    durationMs: Long = 0L, // duración REAL del audio: reparte las líneas proporcionalmente
    modifier: Modifier = Modifier
) {
    val lines = remember(text) { text.split("\n").filter { it.isNotBlank() } }
    // Sin timestamps por palabra: repartimos las líneas de forma uniforme sobre la duración real.
    val perLine = if (lines.isNotEmpty() && durationMs > 0) durationMs / lines.size else readingTimePerLine
    val currentLineIndex = if (lines.isEmpty()) 0
        else (currentTime / perLine.coerceAtLeast(1)).toInt().coerceIn(0, lines.size - 1)
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Determinamos cuántas líneas mostrar antes de que dejen de desaparecer
    val maxVisibleLines = 5
    val scrollThreshold = (lines.size - maxVisibleLines).coerceAtLeast(0)

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex <= scrollThreshold) {
            coroutineScope.launch {
                listState.animateScrollToItem(currentLineIndex)
            }
        }
    }

    Box(modifier = modifier.height(200.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false // El scroll es automático
        ) {
            itemsIndexed(lines) { index, line ->
                val isCurrent = index == currentLineIndex
                val isPast = index < currentLineIndex
                
                // Efecto de desvanecimiento para las líneas pasadas si aún estamos en modo scroll
                val alpha by animateFloatAsState(
                    targetValue = if (isPast && currentLineIndex <= scrollThreshold) 0f else 1f,
                    label = "alpha"
                )

                Text(
                    text = line,
                    fontSize = 18.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(alpha = alpha)
                )
            }
            // Espacio al final para que la última línea pueda quedar arriba si es necesario
            item {
                Spacer(modifier = Modifier.height(150.dp))
            }
        }
    }
}

@Composable
fun MapSubtitles(
    text: String,
    currentTime: Long,
    readingTimePerLine: Long = 3000L,
    durationMs: Long = 0L, // duración REAL del audio (0 = usar readingTimePerLine)
    modifier: Modifier = Modifier
) {
    val lines = remember(text) { text.split("\n", ". ").filter { it.isNotBlank() } }
    val perLine = if (lines.isNotEmpty() && durationMs > 0) durationMs / lines.size else readingTimePerLine
    val currentLineIndex = if (lines.isNotEmpty()) {
        (currentTime / perLine.coerceAtLeast(1)).toInt().coerceIn(0, lines.size - 1)
    } else 0
    
    if (lines.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = lines[currentLineIndex],
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
