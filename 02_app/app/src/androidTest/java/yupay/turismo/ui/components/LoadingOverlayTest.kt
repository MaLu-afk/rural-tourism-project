package yupay.turismo.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import yupay.turismo.ui.theme.Final_projectTheme

/**
 * Pruebas de interfaz (Compose) para [LoadingOverlay].
 *
 * Verifican que el componente muestra el mensaje recibido por parámetro, tanto el
 * texto por defecto como uno personalizado. Al ser un composable sin dependencias de
 * ViewModel ni de Context, se prueba de forma aislada con [createComposeRule].
 *
 * Ubicación: app/src/androidTest/java/yupay/turismo/ui/components/LoadingOverlayTest.kt
 */
@RunWith(AndroidJUnit4::class)
class LoadingOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mensajePorDefecto_seMuestraEnPantalla() {
        composeTestRule.setContent {
            Final_projectTheme {
                LoadingOverlay()
            }
        }

        composeTestRule
            .onNodeWithText("Sincronizando datos...")
            .assertExists()
    }

    @Test
    fun mensajePersonalizado_seMuestraEnPantalla() {
        composeTestRule.setContent {
            Final_projectTheme {
                LoadingOverlay(message = "Cargando visitas...")
            }
        }

        composeTestRule
            .onNodeWithText("Cargando visitas...")
            .assertExists()
    }
}
