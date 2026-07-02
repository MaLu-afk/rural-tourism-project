package yupay.turismo.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas unitarias locales (host JVM, sin emulador) para [CurrencyUtils.convert].
 *
 * Verifican la conversión de montos entre las tres monedas soportadas (S/, $, €)
 * tomando el Sol como moneda base, así como el comportamiento defensivo ante
 * tipos de cambio inválidos (tasa = 0).
 *
 * Ubicación: app/src/test/java/yupay/turismo/utils/CurrencyUtilsTest.kt
 */
class CurrencyUtilsTest {

    private val usdRate = 3.8   // 1 $ = 3.8 S/
    private val eurRate = 4.1   // 1 € = 4.1 S/
    private val delta = 0.0001  // tolerancia para comparación de Double

    @Test
    fun mismaMoneda_devuelveElMismoMonto() {
        assertEquals(100.0, CurrencyUtils.convert(100.0, "S/", "S/", usdRate, eurRate), delta)
    }

    @Test
    fun solesADolares_divideEntreTipoDeCambio() {
        // 38 S/ ÷ 3.8 = 10 $
        assertEquals(10.0, CurrencyUtils.convert(38.0, "S/", "$", usdRate, eurRate), delta)
    }

    @Test
    fun dolaresASoles_multiplicaPorTipoDeCambio() {
        // 10 $ × 3.8 = 38 S/
        assertEquals(38.0, CurrencyUtils.convert(10.0, "$", "S/", usdRate, eurRate), delta)
    }

    @Test
    fun solesAEuros_divideEntreTipoDeCambioEuro() {
        // 41 S/ ÷ 4.1 = 10 €
        assertEquals(10.0, CurrencyUtils.convert(41.0, "S/", "€", usdRate, eurRate), delta)
    }

    @Test
    fun dolaresAEuros_seConvierteViaSoles() {
        // 10 $ → 38 S/ → 38 / 4.1 €
        assertEquals(38.0 / 4.1, CurrencyUtils.convert(10.0, "$", "€", usdRate, eurRate), delta)
    }

    @Test
    fun tipoDeCambioCero_noDivideEntreCero() {
        // Con usdRate = 0 la conversión S/ → $ debe devolver el monto en soles sin fallar.
        assertEquals(50.0, CurrencyUtils.convert(50.0, "S/", "$", 0.0, eurRate), delta)
    }
}
