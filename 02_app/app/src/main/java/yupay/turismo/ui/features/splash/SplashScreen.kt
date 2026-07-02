package yupay.turismo.ui.features.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import yupay.turismo.R

@Composable
fun SplashScreen(isReady: Boolean, onTimeout: () -> Unit) {
    var timerDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(2000)
        timerDone = true
    }

    LaunchedEffect(timerDone, isReady) {
        if (timerDone && isReady) {
            onTimeout()
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (isLandscape) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedLogo(size = 110.dp)

                Spacer(modifier = Modifier.width(32.dp))

                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Yupay Turismo",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedLogo(size = 140.dp)

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Yupay Turismo",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(100.dp))

                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

/**
 * Logo de la marca (vector `R.drawable.logo`) animado: entra con fade + escala con un pequeño
 * rebote y luego late suavemente. Se tiñe con el color primario del tema, así que adopta los
 * colores representativos según el modo del dispositivo (marrón en claro, azul en oscuro).
 */
@Composable
private fun AnimatedLogo(size: Dp) {
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, animationSpec = tween(durationMillis = 700)) }
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val infinite = rememberInfiniteTransition(label = "logoPulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Image(
        painter = painterResource(id = R.drawable.logo),
        contentDescription = "Yupay Turismo",
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val s = scale.value * pulse
                scaleX = s
                scaleY = s
                this.alpha = alpha.value
            },
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
    )
}
