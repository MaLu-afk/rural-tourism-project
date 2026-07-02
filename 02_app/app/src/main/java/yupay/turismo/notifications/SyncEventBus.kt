package yupay.turismo.notifications

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bus de eventos de sincronización, singleton de proceso (creado en
 * [yupay.turismo.di.ServiceLocator]). Las capas de sync (nube en
 * [yupay.turismo.data.repository.CloudSyncRepository] y P2P en
 * [yupay.turismo.sync.P2pSyncController]) emiten aquí cuando insertan datos remotos nuevos; el
 * observador de notificaciones de [yupay.turismo.YupayApp] los colecta.
 *
 * Usa [tryEmit] no suspendido para poder emitir desde dentro de la sección sincronizada del motor
 * de sync sin riesgo de bloqueo. El buffer extra absorbe ráfagas; si se desbordara, descarta el
 * evento más antiguo (aceptable: como mucho se pierde una notificación de agrupación).
 */
class SyncEventBus {
    private val _events = MutableSharedFlow<SyncEvent>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    fun emit(event: SyncEvent) {
        _events.tryEmit(event)
    }
}
