package yupay.turismo.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import yupay.turismo.data.remote.RealtimeClient
import yupay.turismo.data.remote.YupayApiService
import yupay.turismo.data.repository.CloudSyncRepository
import yupay.turismo.data.repository.SyncOutcome
import yupay.turismo.data.session.SessionManager
import yupay.turismo.utils.NetworkMonitor

/**
 * Coordina la sincronización con la nube: serializa las operaciones (un solo sync a la vez)
 * y dispara [syncNow] automáticamente cuando vuelve la conexión o cuando aparecen cambios
 * locales pendientes en el outbox. La sincronización P2P por LAN (paquete `sync/`) es
 * independiente y no se ve afectada.
 */
class CloudSyncEngine(
    private val repo: CloudSyncRepository,
    private val session: SessionManager,
    private val networkMonitor: NetworkMonitor,
    api: YupayApiService
) {
    private val mutex = Mutex()

    /** Realtime: al notificar un cambio remoto, dispara un pull incremental. */
    private val realtime = RealtimeClient(api, session, networkMonitor) { syncNow() }

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    val pendingCountFlow = repo.pendingCountFlow

    sealed interface SyncState {
        object Idle : SyncState
        object Syncing : SyncState
        data class Success(val at: Long) : SyncState
        data class Error(val message: String) : SyncState
    }

    /** Primer enlace tras login/registro/Google: sube lo local o adopta lo del servidor. */
    suspend fun firstLink(): SyncOutcome = guarded(wait = true) { repo.firstLinkSmart() }

    /** Sync incremental: drena el outbox y baja cambios desde la última marca. */
    suspend fun syncNow(): SyncOutcome = guarded(wait = false) { repo.syncNow() }

    /**
     * Sube a la nube los cambios que entraron por P2P/LAN (no pasan por el outbox). Reconcilia
     * el estado local completo contra el servidor. Lo dispara la capa P2P ([SyncViewModel]).
     */
    suspend fun reconcileFromP2p(): SyncOutcome = guarded(wait = false) { repo.reconcileLocalToCloud() }

    /**
     * Arranca los observadores que disparan la sync sola:
     *  - al estar online + con sesión (reconexión / arranque),
     *  - al crecer la cola de cambios pendientes mientras hay red.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(networkMonitor.isOnline, session.isLoggedInFlow) { online, logged -> online && logged }
                .distinctUntilChanged()
                .collect { ready ->
                    if (ready) {
                        delay(1000) // pequeño respiro tras (re)conectar
                        syncNow()
                    }
                }
        }
        scope.launch {
            repo.pendingCountFlow
                .distinctUntilChanged()
                .collect { count ->
                    if (count > 0 && networkMonitor.isOnline.value && session.isLoggedIn()) {
                        syncNow()
                    }
                }
        }
        scope.launch {
            repo.unsyncedVisitsCountFlow
                .distinctUntilChanged()
                .collect { count ->
                    if (count > 0 && networkMonitor.isOnline.value && session.isLoggedIn()) {
                        syncNow()
                    }
                }
        }
        // Sync en tiempo real: Realtime es la vía principal; el poll de respaldo de abajo cubre
        // cortes del WebSocket o eventos perdidos.
        realtime.start(scope)
        // Poll de respaldo (red de seguridad): un pull incremental periódico mientras hay red+sesión.
        // Barato y protegido por el Mutex (no se solapa con otros syncs).
        scope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (networkMonitor.isOnline.value && session.isLoggedIn()) {
                    syncNow()
                }
            }
        }
    }

    private companion object {
        /** Periodo del poll de respaldo (ms). Realtime entrega los cambios casi al instante. */
        const val POLL_INTERVAL_MS = 60_000L
    }

    private suspend fun guarded(wait: Boolean, block: suspend () -> SyncOutcome): SyncOutcome {
        if (wait) {
            mutex.lock()
        } else if (!mutex.tryLock()) {
            return SyncOutcome.Error("Sincronización ya en curso.")
        }
        try {
            _state.value = SyncState.Syncing
            val outcome = runCatching { block() }
                .getOrElse { SyncOutcome.Error(it.message ?: "Error inesperado de sincronización.") }
            _state.value = toState(outcome)
            return outcome
        } finally {
            mutex.unlock()
        }
    }

    private fun toState(outcome: SyncOutcome): SyncState = when (outcome) {
        SyncOutcome.Success -> SyncState.Success(System.currentTimeMillis())
        SyncOutcome.NotLoggedIn -> SyncState.Idle
        is SyncOutcome.Error -> SyncState.Error(outcome.message)
    }
}
