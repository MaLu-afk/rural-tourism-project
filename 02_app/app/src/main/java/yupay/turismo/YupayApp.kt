package yupay.turismo

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import yupay.turismo.data.local.AppDatabase
import yupay.turismo.di.ServiceLocator
import yupay.turismo.notifications.AppForegroundObserver
import yupay.turismo.notifications.NotificationHelper
import yupay.turismo.service.SyncForegroundService

/**
 * Application: inicializa el localizador de servicios de la nube y arranca la sincronización
 * automática (reconexión + outbox). Además prepara las notificaciones del sistema:
 *  - crea los canales de notificación,
 *  - registra el observador de primer/segundo plano ([AppForegroundObserver]),
 *  - traduce los eventos de sincronización en notificaciones (sólo en segundo plano y con el toggle
 *    activado),
 *  - reanuda el Foreground Service si el usuario ya tenía las notificaciones activadas.
 */
class YupayApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ServiceLocator.startBackgroundSync()

        NotificationHelper.createChannels(this)
        AppForegroundObserver.register(this)
        observeSyncEventsForNotifications()
        startSyncServiceIfEnabled()
    }

    /**
     * Convierte los eventos del [yupay.turismo.notifications.SyncEventBus] en notificaciones del
     * sistema, pero SÓLO si el usuario activó las notificaciones y la app está en segundo plano
     * (en primer plano los datos ya se ven en vivo, así que se omite).
     */
    private fun observeSyncEventsForNotifications() {
        appScope.launch {
            ServiceLocator.syncEventBus.events.collect { event ->
                if (AppForegroundObserver.isAppInForeground) return@collect
                if (!ServiceLocator.devicePrefs.isNotificationsEnabled()) return@collect
                val language = AppDatabase.getDatabase(this@YupayApp)
                    .appSettingsDao().getSettingsOnce()?.language ?: "Español"
                NotificationHelper.notify(this@YupayApp, event, language)
            }
        }
    }

    /** Reanuda el servicio de segundo plano si las notificaciones ya estaban activadas. */
    private fun startSyncServiceIfEnabled() {
        appScope.launch {
            if (ServiceLocator.devicePrefs.isNotificationsEnabled()) {
                SyncForegroundService.start(this@YupayApp)
            }
        }
    }
}
