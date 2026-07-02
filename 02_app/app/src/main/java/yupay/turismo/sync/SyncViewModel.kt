package yupay.turismo.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import yupay.turismo.data.local.DiscountType
import yupay.turismo.data.local.SelectedProduct
import yupay.turismo.di.ServiceLocator

/**
 * Fachada delgada sobre [P2pSyncController]. La lógica P2P vive ahora en un singleton de proceso
 * ([ServiceLocator.p2pController]) para seguir sincronizando con la app cerrada; este ViewModel sólo
 * re-expone sus flows y delega los métodos, de modo que la UI (ProfileScreen, SyncStatusScreen,
 * LinkedDevicesScreen, ShowQrScreen, NavGraph) no cambia.
 *
 * Importante: NO se libera nada en `onCleared()` — el motor P2P debe sobrevivir a la destrucción de
 * la Activity.
 */
class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val controller: P2pSyncController

    init {
        // Idempotente: garantiza los singletons aunque YupayApp no se haya ejecutado aún.
        ServiceLocator.init(application)
        controller = ServiceLocator.p2pController
        // Asegura que el motor esté arrancado (idempotente).
        controller.start()
    }

    // ───────── Flows re-expuestos (misma firma que antes) ─────────
    val isConnected get() = controller.isConnected
    val role get() = controller.role
    val remoteDeviceName get() = controller.remoteDeviceName
    val logs get() = controller.logs
    val ticks get() = controller.ticks
    val syncCompleted get() = controller.syncCompleted
    val resetEvent get() = controller.resetEvent
    val latency get() = controller.latency
    val acks get() = controller.acks

    // ───────── Métodos delegados ─────────
    fun addVisit(
        nationality: String,
        flag: String,
        selectedProducts: List<SelectedProduct>,
        subtotal: Double,
        discountValue: Double,
        discountType: DiscountType,
        totalAmount: Double
    ) = controller.addVisit(nationality, flag, selectedProducts, subtotal, discountValue, discountType, totalAmount)

    fun saveProfile(name: String, category: String) = controller.saveProfile(name, category)
    fun markOnboardingCompleted() = controller.markOnboardingCompleted()
    fun startServer(port: Int, token: String? = null) = controller.startServer(port, token)
    fun cancelServerMode() = controller.cancelServerMode()
    fun connectToServer(ip: String, port: Int, token: String? = null) = controller.connectToServer(ip, port, token)
    fun logout(onComplete: () -> Unit = {}) = controller.logout(onComplete)
    fun requestRemoteLogout(onComplete: () -> Unit = {}) = controller.requestRemoteLogout(onComplete)
    fun disconnectAllRemotes(onComplete: () -> Unit = {}) = controller.disconnectAllRemotes(onComplete)
    fun sendPing() = controller.sendPing()
    fun getLocalIpAddress(): String = controller.getLocalIpAddress()
}
