package yupay.turismo.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = HighContrastBlue,
    secondary = ColdGray,
    tertiary = ColdBlueTertiary,
    background = DeepBlackBackground,
    surface = DeepBlackSurface,
    onPrimary = DeepBlackBackground,
    onSecondary = DeepBlackBackground,
    onTertiary = DeepBlackBackground,
    onBackground = ColdBlueOnBackground,
    onSurface = ColdBlueOnSurface,
    primaryContainer = ColdBluePrimaryContainer,
    onPrimaryContainer = ColdBlueOnPrimaryContainer,
    surfaceVariant = DeepBlackSurfaceVariant,
    onSurfaceVariant = ColdGray
)

private val LightColorScheme = lightColorScheme(
    primary = BrownPrimary,
    secondary = DarkGreenText,
    tertiary = OrangeAccent,
    background = CreamBackground,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF2C1810), // Brownish black for text
    onSurface = Color(0xFF2C1810),
    primaryContainer = WarmPrimaryContainer,
    onPrimaryContainer = WarmOnPrimaryContainer,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = Color(0xFF5D4037) // Muted brown for secondary text
)

@Composable
fun Final_projectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled for brand consistency
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Preview(showBackground = true)
@Composable
fun DarkThemePreview() {
    Final_projectTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Modo Oscuro Deep Black", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text("Superficie de alto contraste", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LightThemePreview() {
    Final_projectTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Modo Claro Cálido", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text("Superficie cálida", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
