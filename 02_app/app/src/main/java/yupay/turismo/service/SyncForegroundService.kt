package yupay.turismo.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import yupay.turismo.di.ServiceLocator
import yupay.turismo.notifications.NotificationHelper

/**
 * Foreground Service que mantiene VIVO el proceso para que la sincronización siga funcionando con
 * la app cerrada, por sus dos canales:
 *  - **Nube**: el WebSocket de Supabase Realtime + el poll de [yupay.turismo.data.sync.CloudSyncEngine]
 *    (ya arrancados en el `appScope` de [ServiceLocator]); el proceso vivo evita que el sistema los mate.
 *  - **P2P**: [yupay.turismo.sync.P2pSyncController], sacado del ciclo de vida de la Activity.
 *
 * Tipo de FGS: `dataSync` (cubre tanto la nube como el P2P por LAN, ambos transferencia de datos).
 *
 * Android obliga a mostrar una notificación "ongoing" (no descartable) mientras corre. Se usa el
 * canal silencioso [NotificationHelper.CHANNEL_ONGOING] para que sea discreta.
 *
 * Se enciende/apaga desde el toggle del perfil ([yupay.turismo.ui.MainViewModel.setNotificationsEnabled])
 * y se reanuda en [yupay.turismo.YupayApp] si el usuario ya lo tenía activado.
 */
class SyncForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        // Garantiza que los motores de sync (nube + P2P) estén instanciados y arrancados.
        ServiceLocator.init(this)
        ServiceLocator.startBackgroundSync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        // START_STICKY: si el sistema mata el servicio por memoria, lo recrea (re-llama aquí con
        // intent nulo) y volvemos a foreground.
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = NotificationHelper.buildOngoingNotification(this)
        // dataSync cubre AMBOS canales (nube y P2P por LAN son transferencia de datos). Sólo se
        // pasa el tipo en API 29+, donde existe y el manifest lo declara.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NotificationHelper.ongoingNotificationId(),
            notification,
            type
        )
    }

    /**
     * Android 15+ limita los FGS de tipo `dataSync` (~6 h/día). Al agotarse el tiempo el sistema
     * llama aquí y hay que salir del estado foreground para no recibir una excepción. La sync se
     * reanudará al reabrir la app (o cuando el usuario reactive el toggle); además, en primer plano
     * el poll y Realtime de la nube siguen bajando los cambios con normalidad.
     */
    override fun onTimeout(startId: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SyncForegroundService::class.java)
                )
            } catch (_: Exception) {
                // p.ej. ForegroundServiceStartNotAllowedException si se intenta desde segundo plano
                // (Android 12+). El servicio se reanudará la próxima vez que la app pase a foreground.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncForegroundService::class.java))
        }
    }
}
