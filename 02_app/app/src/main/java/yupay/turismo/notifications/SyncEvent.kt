package yupay.turismo.notifications

import yupay.turismo.ui.navigation.Routes

/**
 * Evento de dominio que representa "llegaron datos nuevos" por cualquiera de los dos canales de
 * sincronización (nube o P2P). Lo emite [SyncEventBus] y lo consume el observador de notificaciones
 * en [yupay.turismo.YupayApp], que decide si mostrar una notificación del sistema según si la app
 * está en primer o segundo plano (ver [AppForegroundObserver]).
 *
 * [navTarget] es la ruta a la que debe llevar la notificación al tocarla (deep link).
 */
sealed interface SyncEvent {
    /** Destino de navegación al tocar la notificación de este evento. */
    val navTarget: String

    /** ID estable de notificación: misma categoría → reemplaza en vez de apilar. */
    val notificationId: Int

    data class NewVisits(val count: Int) : SyncEvent {
        override val navTarget = Routes.VISITS
        override val notificationId = ID_VISITS
    }

    data class ProductsChanged(val count: Int) : SyncEvent {
        override val navTarget = Routes.PRODUCT_CATALOG
        override val notificationId = ID_PRODUCTS
    }

    object TipsChanged : SyncEvent {
        override val navTarget = Routes.TIP_DETAIL
        override val notificationId = ID_TIPS
    }

    object MapChanged : SyncEvent {
        override val navTarget = Routes.MAP
        override val notificationId = ID_MAP
    }

    companion object {
        const val ID_SUMMARY = 1000
        const val ID_VISITS = 1001
        const val ID_PRODUCTS = 1002
        const val ID_TIPS = 1003
        const val ID_MAP = 1004

        /** Todos los IDs de notificaciones de eventos (para cancelarlos al volver a foreground). */
        val ALL_EVENT_IDS = intArrayOf(ID_SUMMARY, ID_VISITS, ID_PRODUCTS, ID_TIPS, ID_MAP)
    }
}
