package yupay.turismo.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pruebas unitarias locales (host JVM, sin emulador) para los conversores de fecha
 * usados en la sincronización con la nube: [parseIsoToMillis] y [millisToIso].
 *
 * El servidor maneja fechas en formato ISO 8601 (timestamptz de Postgres) y Room/Android
 * las almacena como epoch en milisegundos. Estas pruebas verifican la conversión en
 * ambos sentidos y el manejo de entradas inválidas o nulas.
 *
 * Ubicación: app/src/test/java/yupay/turismo/data/remote/DateMappersTest.kt
 */
class DateMappersTest {

    @Test
    fun parseIso_conZulu_devuelveEpochMillis() {
        // 1970-01-01T00:00:01Z = 1000 ms desde epoch
        assertEquals(1000L, parseIsoToMillis("1970-01-01T00:00:01Z"))
    }

    @Test
    fun parseIso_conOffsetExplicito_devuelveEpochMillis() {
        // 1970-01-01T01:00:00+01:00 equivale a 1970-01-01T00:00:00Z = 0 ms
        assertEquals(0L, parseIsoToMillis("1970-01-01T01:00:00+01:00"))
    }

    @Test
    fun parseIso_sinOffset_seInterpretaComoUtc() {
        // LocalDateTime sin zona: el código lo interpreta como UTC.
        assertEquals(0L, parseIsoToMillis("1970-01-01T00:00:00"))
    }

    @Test
    fun parseIso_null_devuelveNull() {
        assertNull(parseIsoToMillis(null))
    }

    @Test
    fun parseIso_vacio_devuelveNull() {
        assertNull(parseIsoToMillis("   "))
    }

    @Test
    fun parseIso_textoInvalido_devuelveNull() {
        assertNull(parseIsoToMillis("no-es-una-fecha"))
    }

    @Test
    fun millisToIso_idaYVuelta_conservaElValor() {
        val millis = 1_700_000_000_000L
        val iso = millisToIso(millis)
        assertEquals(millis, parseIsoToMillis(iso))
    }
}
