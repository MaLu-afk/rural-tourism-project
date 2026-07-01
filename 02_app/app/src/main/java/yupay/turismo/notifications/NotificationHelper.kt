package yupay.turismo.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import yupay.turismo.MainActivity
import yupay.turismo.R

/**
 * Punto único para crear canales y mostrar/cancelar las notificaciones del sistema.
 *
 * Dos canales:
 *  - [CHANNEL_EVENTS] (IMPORTANCE_HIGH, con sonido): visitas/productos/tips/mapa.
 *  - [CHANNEL_ONGOING] (IMPORTANCE_LOW, silencioso): la notificación "ongoing" obligatoria del
 *    Foreground Service ([yupay.turismo.service.SyncForegroundService]).
 *
 * Las notificaciones de eventos llevan un deep link ([buildDeepLinkIntent]) que abre [MainActivity]
 * en la sección correspondiente; se cancelan al volver la app a primer plano ([AppForegroundObserver]).
 */
object NotificationHelper {

    const val CHANNEL_EVENTS = "yupay_events"
    const val CHANNEL_ONGOING = "yupay_sync"

    /** Extra del Intent que indica a qué ruta navegar al tocar la notificación. */
    const val EXTRA_NAV_TARGET = "yupay.turismo.NAV_TARGET"

    private const val ONGOING_NOTIFICATION_ID = 42

    /** Crea los canales (idempotente). Llamar desde [yupay.turismo.YupayApp.onCreate]. */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val events = NotificationChannel(
            CHANNEL_EVENTS,
            "Novedades de sincronización",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos de nuevas visitas, cambios en productos, tips y mapa."
            enableVibration(true)
        }

        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            "Sincronización en segundo plano",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene la app sincronizada con la nube y otros dispositivos."
            setShowBadge(false)
        }

        nm.createNotificationChannel(events)
        nm.createNotificationChannel(ongoing)
    }

    /** Notificación persistente "ongoing" que exige Android mientras corre el Foreground Service. */
    fun buildOngoingNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("YUPAY")
            .setContentText("Sincronizando en segundo plano")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(buildDeepLinkIntent(context, navTarget = null, requestCode = 0))
            .build()

    fun ongoingNotificationId(): Int = ONGOING_NOTIFICATION_ID

    /**
     * Muestra (o reemplaza) la notificación de un evento de sincronización, agrupando las de la
     * misma categoría: en vez de apilar muchas notificaciones iguales, la categoría mantiene UNA sola
     * notificación cuyo contador se va acumulando (1.ª visita → "Se añadió 1 visita"; 2.ª visita →
     * "Se añadieron 2 visitas").
     *
     * El total previo se lee de la propia notificación activa ([Notification.number]) en vez de un
     * estado en memoria: así sobrevive a reinicios del proceso y se reinicia solo cuando la
     * notificación deja de estar activa (el usuario la descarta, o vuelve la app a primer plano y
     * [cancelEventNotifications] la cancela).
     */
    fun notify(context: Context, event: SyncEvent, language: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val id = event.notificationId
        val increment = incrementOf(event)
        val total = if (increment > 0) previousCount(context, id) + increment else 0
        val text = textFor(event, language, total)
        val builder = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(buildDeepLinkIntent(context, event.navTarget, requestCode = id))
        // setNumber persiste el contador en la notificación activa para poder acumular en el próximo
        // evento de la misma categoría (y muestra el badge en lanzadores que lo soportan).
        if (total > 0) builder.setNumber(total)
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS no concedido (Android 13+); se ignora silenciosamente.
        }
    }

    /** Contador mostrado actualmente por la notificación [id] si sigue activa; 0 si no hay ninguna. */
    private fun previousCount(context: Context, id: Int): Int {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return 0
        return nm.activeNotifications.firstOrNull { it.id == id }?.notification?.number ?: 0
    }

    /** Cuánto suma este evento al contador de su categoría. Tips y mapa no llevan número (→ 0). */
    private fun incrementOf(event: SyncEvent): Int = when (event) {
        is SyncEvent.NewVisits -> event.count.coerceAtLeast(1)
        is SyncEvent.ProductsChanged -> event.count.coerceAtLeast(1)
        SyncEvent.TipsChanged, SyncEvent.MapChanged -> 0
    }

    /** Cancela todas las notificaciones de EVENTOS (no toca la "ongoing" del servicio). */
    fun cancelEventNotifications(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        for (id in SyncEvent.ALL_EVENT_IDS) nm.cancel(id)
    }

    private fun buildDeepLinkIntent(
        context: Context,
        navTarget: String?,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (navTarget != null) putExtra(EXTRA_NAV_TARGET, navTarget)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    private data class NotifText(val title: String, val body: String)

    /**
     * Título (con emoji y propio de cada categoría) y cuerpo de la notificación, en los tres idiomas
     * que maneja la app. Para visitas y productos el cuerpo incluye [count] (singular/plural); tips y
     * mapa no llevan número. El nombre "YUPAY" ya aparece en la cabecera de la notificación, así que el
     * título se reserva para describir la categoría.
     */
    private fun textFor(event: SyncEvent, language: String, count: Int): NotifText {
        val en = language == "Inglés"
        val pt = language == "Portugués"
        return when (event) {
            is SyncEvent.NewVisits -> {
                val title = when {
                    en -> "🏡 New visits"
                    pt -> "🏡 Novas visitas"
                    else -> "🏡 Nuevas visitas"
                }
                val body = when {
                    en -> if (count == 1) "1 new visit was added" else "$count new visits were added"
                    pt -> if (count == 1) "1 nova visita foi adicionada" else "$count novas visitas foram adicionadas"
                    else -> if (count == 1) "Se añadió 1 nueva visita" else "Se añadieron $count nuevas visitas"
                }
                NotifText(title, body)
            }
            is SyncEvent.ProductsChanged -> {
                val title = when {
                    en -> "🛍️ Products updated"
                    pt -> "🛍️ Produtos atualizados"
                    else -> "🛍️ Productos actualizados"
                }
                val body = when {
                    en -> if (count == 1) "1 product was updated" else "$count products were updated"
                    pt -> if (count == 1) "1 produto foi atualizado" else "$count produtos foram atualizados"
                    else -> if (count == 1) "Se actualizó 1 producto" else "Se actualizaron $count productos"
                }
                NotifText(title, body)
            }
            SyncEvent.TipsChanged -> {
                val title = when {
                    en -> "💡 New tips"
                    pt -> "💡 Novas dicas"
                    else -> "💡 Nuevos tips"
                }
                val body = when {
                    en -> "New tips for entrepreneurs were added"
                    pt -> "Foram adicionadas novas dicas para empreendedores"
                    else -> "Se añadieron nuevos tips para emprendedores"
                }
                NotifText(title, body)
            }
            SyncEvent.MapChanged -> {
                val title = when {
                    en -> "🗺️ Map updated"
                    pt -> "🗺️ Mapa atualizado"
                    else -> "🗺️ Mapa actualizado"
                }
                val body = when {
                    en -> "The map summary was updated"
                    pt -> "O resumo do mapa foi atualizado"
                    else -> "Se actualizó el resumen del mapa"
                }
                NotifText(title, body)
            }
        }
    }
}
