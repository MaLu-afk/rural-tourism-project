package yupay.turismo.notifications

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Observa el ciclo de vida del PROCESO (no de una Activity) vía [ProcessLifecycleOwner] para saber
 * si la app está en primer o segundo plano. Da el comportamiento "tipo WhatsApp":
 *
 *  - Al pasar a primer plano ([onStart]): cancela las notificaciones de eventos ya mostradas
 *    (el usuario ya está viendo los datos en vivo) y marca [isAppInForeground] = true, de modo que
 *    el consumidor del [SyncEventBus] deje de generar notificaciones.
 *  - Al pasar a segundo plano ([onStop]): marca [isAppInForeground] = false, reactivando las
 *    notificaciones para los cambios que lleguen mientras la app no está visible.
 *
 * Se registra una sola vez desde [yupay.turismo.YupayApp.onCreate].
 */
object AppForegroundObserver : DefaultLifecycleObserver {

    @Volatile
    var isAppInForeground: Boolean = false
        private set

    private lateinit var appContext: Context

    fun register(context: Context) {
        appContext = context.applicationContext
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
        NotificationHelper.cancelEventNotifications(appContext)
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
    }
}
