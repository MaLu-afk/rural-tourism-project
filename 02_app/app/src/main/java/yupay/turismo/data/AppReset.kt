package yupay.turismo.data

import android.content.Context
import android.provider.Settings
import yupay.turismo.data.local.AppDatabase
import yupay.turismo.data.local.AppSettings
import yupay.turismo.di.ServiceLocator
import yupay.turismo.tts.audio.AudioCache

/**
 * Reset TOTAL de la app a "estado de fábrica" (como recién descargada). Es el comportamiento
 * único y bien definido que comparten:
 *  - el "Cerrar sesión (Borrar todo)" de la cuenta online ([yupay.turismo.ui.MainViewModel.clearAllAppData]), y
 *  - el reset del dispositivo SERVIDOR al cancelar la vinculación P2P ([yupay.turismo.sync.SyncViewModel.logout]).
 *
 * Limpia: sesión de nube (tokens) + outbox de cambios pendientes + todas las tablas de Room +
 * el audio TTS generado (no los modelos descargados); y deja los ajustes por defecto SIN tips ni
 * resúmenes de mapa (vuelven al registrar/iniciar sesión).
 */
object AppReset {
    suspend fun factoryReset(context: Context) {
        val appContext = context.applicationContext

        // Sesión de nube + cola de cambios pendientes (idempotente).
        ServiceLocator.init(appContext)
        runCatching { ServiceLocator.sessionManager.clear() }
        runCatching { ServiceLocator.cloudSyncRepository.clearOutbox() }

        // Audio TTS generado: detener reproducción y borrar la caché de WAV (los modelos de voz
        // descargados se conservan; son caros de re-descargar).
        runCatching { ServiceLocator.audioPlaybackController.release() }
        runCatching { AudioCache.deleteAll(appContext) }
        // Marcadores de descargas pausadas (checkpoints manuales): se limpian al cerrar la cuenta.
        runCatching { ServiceLocator.devicePrefs.clearAllPausedDownloads() }

        // Datos locales + re-siembra de ajustes por defecto (sin tips/mapas).
        val db = AppDatabase.getDatabase(appContext)
        val repo = DataRepository(db.appSettingsDao(), db.visitDao(), db.productDao())
        repo.clearAllData()

        val androidId = Settings.Secure.getString(
            appContext.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        repo.saveSettings(
            AppSettings(
                deviceId = androidId,
                hardwareDeviceId = androidId
            )
        )
    }
}
