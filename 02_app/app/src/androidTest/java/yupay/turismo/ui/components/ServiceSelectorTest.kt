package yupay.turismo.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import yupay.turismo.ui.theme.Final_projectTheme

/**
 * Pruebas de interfaz (Compose) para [ServiceCard], la tarjeta seleccionable que se usa en
 * el formulario de registro de visita (servicios consumidos: Hospedaje, Alimentación,
 * Artesanía).
 *
 * Verifican que la tarjeta muestra el nombre del servicio recibido y que el callback
 * [ServiceCard] `onClick` se dispara al tocarla. No se prueba el color/borde de selección
 * (detalle visual), solo el comportamiento observable desde el árbol semántico.
 *
 * Ubicación: app/src/androidTest/java/yupay/turismo/ui/components/ServiceSelectorTest.kt
 */
@RunWith(AndroidJUnit4::class)
class ServiceSelectorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tarjetaDeServicio_muestraSuNombre() {
        val servicio = ServiceOption(name = "Hospedaje", icon = Icons.Default.Home)

        composeTestRule.setContent {
            Final_projectTheme {
                ServiceCard(service = servicio, isSelected = false, onClick = {})
            }
        }

        composeTestRule
            .onNodeWithText("Hospedaje")
            .assertExists()
    }

    @Test
    fun alTocarLaTarjeta_seInvocaOnClick() {
        val servicio = ServiceOption(name = "Alimentación", icon = Icons.Default.Home)
        var clicked = false

        composeTestRule.setContent {
            Final_projectTheme {
                ServiceCard(service = servicio, isSelected = false, onClick = { clicked = true })
            }
        }

        composeTestRule
            .onNodeWithText("Alimentación")
            .performClick()

        assert(clicked) { "Se esperaba que onClick se invocara al tocar la tarjeta." }
    }
}
