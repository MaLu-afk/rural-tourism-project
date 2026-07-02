package yupay.turismo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import yupay.turismo.ui.theme.Final_projectTheme

@Preview(showBackground = true)
@Composable
fun AudioPlayerPreview() {
    Final_projectTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AudioPlayerUI(
                currentTime = 5000L,
                totalDuration = 30000L,
                isPlaying = true,
                onPlayPauseClick = {},
                onSeek = {},
                onFastForward = {},
                onRewind = {}
            )
            Spacer(modifier = Modifier.height(32.dp))
            AudioPlayerUI(
                currentTime = 15000L,
                totalDuration = 60000L,
                isPlaying = false,
                onPlayPauseClick = {},
                onSeek = {},
                onFastForward = {},
                onRewind = {},
                compact = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CinemaTextPreview() {
    Final_projectTheme {
        CinemaEffectText(
            text = "Esta es la primera línea.\nEsta es la segunda línea en negrita.\nY esta es la tercera línea que sube.",
            currentTime = 3500L, // En la segunda línea
            totalDuration = 9000L
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MapSubtitlesPreview() {
    Final_projectTheme {
        MapSubtitles(
            text = "Resumen del mapa: Mostrando visitas en Cusco.",
            currentTime = 1000L,
            readingTimePerLine = 3000L
        )
    }
}
