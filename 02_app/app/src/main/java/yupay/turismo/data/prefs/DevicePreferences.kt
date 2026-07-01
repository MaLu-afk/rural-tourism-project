package yupay.turismo.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.deviceDataStore by preferencesDataStore(name = "device_prefs")

/**
 * Preferencias **por-dispositivo** (no por-cuenta), guardadas en su propio DataStore.
 *
 * Deliberadamente fuera de [yupay.turismo.data.local.AppSettings]/Room porque:
 *  - `AppSettings` se sincroniza a la nube y por P2P; activar notificaciones en un teléfono no debe
 *    activarlas en el otro dispositivo emparejado.
 *  - La base Room usa `fallbackToDestructiveMigration`: añadir una columna borraría los datos locales.
 *
 * Sigue el mismo patrón que [yupay.turismo.data.session.SessionManager].
 */
class DevicePreferences(appContext: Context) {

    private val ds = appContext.applicationContext.deviceDataStore

    private object Keys {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LAST_SEEN_WATERMARK = longPreferencesKey("last_seen_watermark")

        /** Descargas de modelos de voz pausadas por el usuario: JSON `{modelId: pct}` (0–100). */
        val TTS_PAUSED_DOWNLOADS = stringPreferencesKey("tts_paused_downloads")
    }

    /** ¿El usuario activó las notificaciones? Por defecto false (opt-in explícito). */
    val notificationsEnabledFlow: Flow<Boolean> =
        ds.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: false }

    suspend fun isNotificationsEnabled(): Boolean =
        ds.data.first()[Keys.NOTIFICATIONS_ENABLED] ?: false

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        ds.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    /** Marca de agua (epoch ms) de lo último que el usuario ya vio al abrir la app. */
    suspend fun lastSeenWatermark(): Long =
        ds.data.first()[Keys.LAST_SEEN_WATERMARK] ?: 0L

    suspend fun setLastSeenWatermark(value: Long) {
        ds.edit { it[Keys.LAST_SEEN_WATERMARK] = value }
    }

    // ───────── Descargas de modelos de voz pausadas (checkpoint) ─────────
    // Mapa modelId → progreso (pct 0–100) al pausar. Se usa para distinguir "pausada por el
    // usuario" de "esperando red", y para mostrar el avance guardado. Vive aquí (por-dispositivo)
    // porque una descarga no debe propagarse a otros equipos.

    /** Mapa observable de descargas pausadas (modelId → pct). Vacío si no hay ninguna. */
    val pausedDownloadsFlow: Flow<Map<String, Int>> =
        ds.data.map { decodePaused(it[Keys.TTS_PAUSED_DOWNLOADS]) }

    /** Marca [modelId] como pausado con su progreso actual [pct] (0–100). */
    suspend fun setPausedDownload(modelId: String, pct: Int) {
        ds.edit { prefs ->
            val current = decodePaused(prefs[Keys.TTS_PAUSED_DOWNLOADS]).toMutableMap()
            current[modelId] = pct.coerceIn(0, 100)
            prefs[Keys.TTS_PAUSED_DOWNLOADS] = Json.encodeToString(current.toMap())
        }
    }

    /** Quita [modelId] de la lista de pausados (al reanudar, anular o borrar). */
    suspend fun clearPausedDownload(modelId: String) {
        ds.edit { prefs ->
            val current = decodePaused(prefs[Keys.TTS_PAUSED_DOWNLOADS]).toMutableMap()
            if (current.remove(modelId) != null) {
                prefs[Keys.TTS_PAUSED_DOWNLOADS] = Json.encodeToString(current.toMap())
            }
        }
    }

    /** Limpia todos los marcadores de pausa (logout / reset de fábrica). */
    suspend fun clearAllPausedDownloads() {
        ds.edit { it.remove(Keys.TTS_PAUSED_DOWNLOADS) }
    }

    private fun decodePaused(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
    }
}
