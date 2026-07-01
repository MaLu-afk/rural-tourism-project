package yupay.turismo.data.repository

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import yupay.turismo.data.local.AppSettings
import yupay.turismo.data.local.AppSettingsDao
import yupay.turismo.data.local.PendingOp
import yupay.turismo.data.local.PendingOpDao
import yupay.turismo.data.local.Product
import yupay.turismo.data.local.ProductDao
import yupay.turismo.data.local.Visit
import yupay.turismo.data.local.VisitDao
import yupay.turismo.data.remote.ApiResult
import yupay.turismo.data.remote.DeletePayload
import yupay.turismo.data.remote.YupayApiService
import yupay.turismo.data.remote.applyContentToSettings
import yupay.turismo.data.remote.dto.MigrateRequest
import yupay.turismo.data.remote.dto.PullResponse
import yupay.turismo.data.remote.fromBase64
import yupay.turismo.data.remote.millisToIso
import yupay.turismo.data.remote.parseIsoToMillis
import yupay.turismo.data.remote.toContentRequestDtos
import yupay.turismo.data.remote.toEntity
import yupay.turismo.data.remote.toProfileRequestDto
import yupay.turismo.data.remote.toRequestDto
import yupay.turismo.data.remote.yupayJson
import yupay.turismo.data.session.SessionManager
import yupay.turismo.notifications.SyncEvent
import yupay.turismo.notifications.SyncEventBus

/** Resultado de una operación de sincronización. */
sealed interface SyncOutcome {
    object Success : SyncOutcome
    object NotLoggedIn : SyncOutcome
    data class Error(val message: String, val offline: Boolean = false) : SyncOutcome
}

/**
 * Sincronización con la nube (offline-first).
 *
 * - **Encolado**: cada cambio local de producto/visita/perfil se registra en el outbox
 *   ([PendingOp]) mediante los métodos `enqueue*`.
 * - **Primer enlace** ([firstLinkSmart]): tras login/registro/Google, decide subir lo local
 *   (`/sync/migrate`) si el servidor está vacío, o adoptar lo del servidor (`/sync/pull`).
 * - **Sync incremental** ([syncNow]): drena el outbox y baja cambios desde `lastSyncAt`.
 */
class CloudSyncRepository(
    private val api: YupayApiService,
    private val session: SessionManager,
    private val appSettingsDao: AppSettingsDao,
    private val productDao: ProductDao,
    private val visitDao: VisitDao,
    private val pendingOpDao: PendingOpDao,
    private val syncEventBus: SyncEventBus
) {
    val pendingCountFlow = pendingOpDao.countFlow()

    /** Nº de visitas locales aún no subidas a la nube (para disparar la sync). */
    val unsyncedVisitsCountFlow = visitDao.countUnsyncedFlow()

    // ───────────────── Encolado (offline) ─────────────────
    suspend fun enqueueProductUpsert(localId: Int) =
        enqueue(PendingOp.ENTITY_PRODUCT, PendingOp.OP_UPDATE, localId, "")

    suspend fun enqueueProductDelete(product: Product) =
        enqueue(
            PendingOp.ENTITY_PRODUCT, PendingOp.OP_DELETE, product.id,
            yupayJson.encodeToString(DeletePayload(product.remoteId))
        )

    suspend fun enqueueVisitUpsert(localId: Int) =
        enqueue(PendingOp.ENTITY_VISIT, PendingOp.OP_UPDATE, localId, "")

    suspend fun enqueueVisitDelete(visit: Visit) =
        enqueue(
            PendingOp.ENTITY_VISIT, PendingOp.OP_DELETE, visit.id,
            yupayJson.encodeToString(DeletePayload(visit.remoteId))
        )

    suspend fun enqueueProfileUpdate() =
        enqueue(PendingOp.ENTITY_PROFILE, PendingOp.OP_UPDATE, PendingOp.PROFILE_LOCAL_ID, "")

    private suspend fun enqueue(entityType: String, opType: String, localId: Int, payload: String) {
        // Coalescencia: sólo la op más reciente por entidad.
        pendingOpDao.deleteForEntity(entityType, localId)
        pendingOpDao.insert(
            PendingOp(entityType = entityType, opType = opType, localId = localId, payloadJson = payload)
        )
    }

    /** Vacía el outbox (al cerrar sesión o borrar la cuenta). */
    suspend fun clearOutbox() = pendingOpDao.clear()

    // ───────────────── Primer enlace ─────────────────
    suspend fun firstLinkSmart(): SyncOutcome {
        if (!session.isLoggedIn()) return SyncOutcome.NotLoggedIn
        val startedAt = System.currentTimeMillis()

        val pull = when (val r = api.pull(null)) {
            is ApiResult.Ok -> r.data
            is ApiResult.Fail -> return r.toOutcome()
        }

        val settings = appSettingsDao.getSettingsOnce() ?: AppSettings()
        val localProducts = productDao.getAllOnce()
        val localVisits = visitDao.getAllOnce()
        // "Servidor vacío" = sin productos ni visitas. NO se mira businessName: el registro por
        // OTP (verify-signup-code) ya crea la fila de perfil, pero los datos del emprendedor
        // (productos/visitas) siguen siendo locales y hay que subirlos con migrate, no perderlos.
        val serverEmpty = pull.products.isEmpty() && pull.visits.isEmpty()
        val localHasData = localProducts.isNotEmpty() || localVisits.isNotEmpty() ||
            settings.businessName.isNotBlank()

        if (serverEmpty && localHasData) {
            // El servidor está vacío y la app tiene datos → subirlos y recuperar ids.
            val migrateRes = api.migrate(buildMigrateRequest(settings, localProducts, localVisits))
            if (migrateRes is ApiResult.Fail) return migrateRes.toOutcome()
            when (val r2 = api.pull(null)) {
                is ApiResult.Ok -> finishSync(applyPull(r2.data, replace = true), watermarkFrom(r2.data, startedAt))
                is ApiResult.Fail -> return r2.toOutcome()
            }
        } else {
            // El servidor manda (login a cuenta existente, o nada que subir).
            finishSync(applyPull(pull, replace = true), watermarkFrom(pull, startedAt))
        }
        pendingOpDao.clear()
        return SyncOutcome.Success
    }

    // ───────────────── Sync incremental ─────────────────
    suspend fun syncNow(): SyncOutcome {
        if (!session.isLoggedIn()) return SyncOutcome.NotLoggedIn
        val startedAt = System.currentTimeMillis()

        if (!drainOutbox()) {
            return SyncOutcome.Error("Cambios locales pendientes; se reintentará al reconectar.", offline = true)
        }

        if (!pushPendingVisits()) {
            return SyncOutcome.Error("Visitas pendientes de subir; se reintentará al reconectar.", offline = true)
        }

        val settings = appSettingsDao.getSettingsOnce() ?: AppSettings()
        val since = if (settings.lastSyncAt > 0) millisToIso(settings.lastSyncAt) else null
        val pull = when (val r = api.pull(since)) {
            is ApiResult.Ok -> r.data
            is ApiResult.Fail -> return r.toOutcome()
        }
        finishSync(applyPull(pull, replace = false), watermarkFrom(pull, startedAt))
        return SyncOutcome.Success
    }

    // ───────────────── Puente P2P → nube ─────────────────
    /**
     * Reconcilia el estado LOCAL completo hacia la nube. Pensado para los cambios que entran por
     * P2P/LAN (paquete `sync/`), que NO pasan por el outbox de la nube y por tanto nunca llegarían
     * a la API por sí solos. Hace que el servidor refleje exactamente lo que hay en Room:
     *  - sube el perfil / datos del usuario,
     *  - reconcilia el catálogo (crea los nuevos, actualiza los existentes y BORRA del servidor los
     *    que ya no están localmente),
     *  - sube las visitas nuevas (append-only).
     * Es idempotente y seguro de reintentar. Sólo escribe en Room cuando asigna un `remoteId`
     * nuevo (para no provocar reenvíos P2P innecesarios).
     */
    suspend fun reconcileLocalToCloud(): SyncOutcome {
        if (!session.isLoggedIn()) return SyncOutcome.NotLoggedIn

        // 1) Perfil / datos del usuario.
        val settings = appSettingsDao.getSettingsOnce() ?: AppSettings()
        (api.updateMe(settings.toProfileRequestDto()) as? ApiResult.Fail)?.let {
            if (it.isRetryable()) return it.toOutcome() // 4xx de cliente: continuar
        }

        // 2) Catálogo: el servidor debe quedar igual que lo local.
        val serverProducts = when (val r = api.listProducts()) {
            is ApiResult.Ok -> r.data
            is ApiResult.Fail -> return r.toOutcome()
        }
        val localProducts = productDao.getAllOnce()
        val keepRemoteIds = localProducts.mapNotNull { it.remoteId }.toSet()

        // Borrar del servidor los productos que ya no existen localmente.
        for (sp in serverProducts) {
            if (sp.id !in keepRemoteIds) {
                val r = api.deleteProduct(sp.id)
                if (r is ApiResult.Fail && r.code != 404 && r.isRetryable()) return r.toOutcome()
            }
        }

        // Subir los locales: crear los nuevos (sin remoteId) y actualizar los existentes.
        for (lp in localProducts) {
            if (lp.remoteId == null) {
                when (val r = api.createProduct(lp.toRequestDto())) {
                    is ApiResult.Ok -> productDao.updateProduct(lp.copy(remoteId = r.data.id))
                    is ApiResult.Fail -> if (r.isRetryable()) return r.toOutcome()
                }
            } else {
                when (val r = api.updateProduct(lp.remoteId, lp.toRequestDto())) {
                    is ApiResult.Ok -> Unit // sin escritura local: evita reenvíos P2P en bucle
                    is ApiResult.Fail -> when {
                        r.code == 404 -> when (val c = api.createProduct(lp.toRequestDto())) {
                            is ApiResult.Ok -> productDao.updateProduct(lp.copy(remoteId = c.data.id))
                            is ApiResult.Fail -> if (c.isRetryable()) return c.toOutcome()
                        }
                        r.isRetryable() -> return r.toOutcome()
                        else -> Unit
                    }
                }
            }
        }

        // 3) Visitas nuevas (append-only; reutiliza el mismo empuje que la sync normal).
        if (!pushPendingVisits()) {
            return SyncOutcome.Error("Visitas pendientes de subir; se reintentará.", offline = true)
        }

        return SyncOutcome.Success
    }

    /** Fallos por los que conviene reintentar luego (no son errores de cliente 4xx). */
    private fun ApiResult.Fail.isRetryable(): Boolean = offline || code in 500..599 || code == 401

    // ───────────────── Outbox ─────────────────
    /** @return true si se drenó por completo; false si hubo que parar (reintentar luego). */
    private suspend fun drainOutbox(): Boolean {
        for (op in pendingOpDao.getAll()) {
            when (handleOp(op)) {
                OpResult.SUCCESS, OpResult.DROP -> pendingOpDao.deleteById(op.id)
                OpResult.RETRY -> return false
            }
        }
        return true
    }

    /**
     * Sube las visitas locales que aún no están en la nube (remoteId == null), creadas por
     * cualquier vía (incluido el alta vía P2P/SyncViewModel, que no usa el outbox). Las visitas
     * son append-only y el id de servidor se guarda al instante en `remoteId`, lo que hace que
     * el detalle/lista pasen de "Pendiente de envío" a "Enviado".
     * @return false si hubo que parar por falta de red/servidor (se reintenta luego).
     */
    private suspend fun pushPendingVisits(): Boolean {
        for (v in visitDao.getUnsynced()) {
            val now = System.currentTimeMillis()
            val payload = v.copy(isSent = true, sentDate = now)
            when (val r = api.createVisit(payload.toRequestDto())) {
                is ApiResult.Ok ->
                    visitDao.updateVisit(v.copy(remoteId = r.data.id, isSent = true, sentDate = now))
                is ApiResult.Fail -> {
                    if (r.offline || r.code in 500..599 || r.code == 401) return false
                    // 4xx de cliente: saltar esta visita y seguir con las demás.
                }
            }
        }
        return true
    }

    private enum class OpResult { SUCCESS, RETRY, DROP }

    private suspend fun handleOp(op: PendingOp): OpResult = when (op.entityType) {
        PendingOp.ENTITY_PROFILE -> {
            val s = appSettingsDao.getSettingsOnce()
            if (s == null) OpResult.DROP else classify(api.updateMe(s.toProfileRequestDto()))
        }

        PendingOp.ENTITY_PRODUCT -> if (op.opType == PendingOp.OP_DELETE) {
            val remoteId = decodeRemoteId(op.payloadJson)
            if (remoteId == null) OpResult.SUCCESS else classifyDelete(api.deleteProduct(remoteId))
        } else {
            val p = productDao.getProductById(op.localId) ?: return OpResult.SUCCESS
            if (p.remoteId == null) {
                when (val r = api.createProduct(p.toRequestDto())) {
                    is ApiResult.Ok -> { productDao.updateProduct(p.copy(remoteId = r.data.id)); OpResult.SUCCESS }
                    is ApiResult.Fail -> classifyFail(r)
                }
            } else {
                when (val r = api.updateProduct(p.remoteId, p.toRequestDto())) {
                    is ApiResult.Ok -> OpResult.SUCCESS
                    is ApiResult.Fail -> if (r.code == 404) recreateProduct(p) else classifyFail(r)
                }
            }
        }

        PendingOp.ENTITY_VISIT -> if (op.opType == PendingOp.OP_DELETE) {
            val remoteId = decodeRemoteId(op.payloadJson)
            if (remoteId == null) OpResult.SUCCESS else classifyDelete(api.deleteVisit(remoteId))
        } else {
            val v = visitDao.getVisitById(op.localId) ?: return OpResult.SUCCESS
            if (v.remoteId == null) {
                when (val r = api.createVisit(v.toRequestDto())) {
                    is ApiResult.Ok -> { visitDao.updateVisit(v.copy(remoteId = r.data.id)); OpResult.SUCCESS }
                    is ApiResult.Fail -> classifyFail(r)
                }
            } else {
                when (val r = api.updateVisit(v.remoteId, v.toRequestDto())) {
                    is ApiResult.Ok -> OpResult.SUCCESS
                    is ApiResult.Fail -> if (r.code == 404) recreateVisit(v) else classifyFail(r)
                }
            }
        }

        else -> OpResult.DROP
    }

    private suspend fun recreateProduct(p: Product): OpResult =
        when (val c = api.createProduct(p.toRequestDto())) {
            is ApiResult.Ok -> { productDao.updateProduct(p.copy(remoteId = c.data.id)); OpResult.SUCCESS }
            is ApiResult.Fail -> classifyFail(c)
        }

    private suspend fun recreateVisit(v: Visit): OpResult =
        when (val c = api.createVisit(v.toRequestDto())) {
            is ApiResult.Ok -> { visitDao.updateVisit(v.copy(remoteId = c.data.id)); OpResult.SUCCESS }
            is ApiResult.Fail -> classifyFail(c)
        }

    private fun classify(res: ApiResult<*>): OpResult =
        if (res is ApiResult.Fail) classifyFail(res) else OpResult.SUCCESS

    private fun classifyDelete(res: ApiResult<*>): OpResult = when (res) {
        is ApiResult.Ok -> OpResult.SUCCESS
        is ApiResult.Fail -> if (res.code == 404) OpResult.SUCCESS else classifyFail(res)
    }

    private fun classifyFail(res: ApiResult.Fail): OpResult = when {
        res.offline -> OpResult.RETRY
        res.code in 500..599 -> OpResult.RETRY
        res.code == 401 -> OpResult.RETRY
        else -> OpResult.DROP // 4xx de cliente: descartar para no atascar la cola
    }

    private fun decodeRemoteId(payload: String): Long? =
        if (payload.isBlank()) null
        else runCatching { yupayJson.decodeFromString<DeletePayload>(payload).remoteId }.getOrNull()

    // ───────────────── Aplicar /sync/pull a Room ─────────────────
    /** Vuelca el pull a Room y devuelve el [AppSettings] fusionado (sin guardar todavía). */
    private suspend fun applyPull(pull: PullResponse, replace: Boolean): AppSettings {
        val settings = appSettingsDao.getSettingsOnce() ?: AppSettings()
        var merged = settings
        // No pisar el perfil local si hay una edición propia aún sin subir (PendingOp de PROFILE):
        // un pull en vuelo (sync cada 15 s / Realtime) podría traer la versión anterior y revertir
        // el cambio recién guardado. El guard solo aplica al sync incremental (replace=false); en el
        // primer enlace (replace=true) se adopta el servidor a propósito. Tras subir el cambio, el op
        // se elimina del outbox y el servidor vuelve a mandar (no afecta a la sync entre dispositivos).
        val skipProfile = !replace && pendingOpDao.hasPending(PendingOp.ENTITY_PROFILE)
        if (!skipProfile) {
            pull.user?.let { u ->
                merged = merged.copy(
                    businessName = u.businessName ?: merged.businessName,
                    businessCategory = u.businessCategory ?: merged.businessCategory,
                    profilePicture = u.profilePicture.fromBase64() ?: merged.profilePicture
                )
            }
        }
        merged = applyContentToSettings(merged, pull.content, settings.language)

        // Contadores de cambios remotos REALES, sólo para emitir notificaciones en sync incremental
        // (replace==false). En el primer enlace (replace==true) se reemplaza todo y no se notifica,
        // para no inundar al usuario con la carga inicial.
        var newVisits = 0
        var changedProducts = 0

        if (replace) {
            productDao.replaceProducts(pull.products.map { it.toEntity() })
        } else {
            pull.products.forEach { dto ->
                val existing = productDao.getByRemoteId(dto.id)
                if (existing != null) {
                    // Preservar el uuid local (identidad estable para el merge P2P): el servidor no lo
                    // maneja, así que toEntity generaría uno nuevo en cada pull.
                    val entity = dto.toEntity(localId = existing.id).copy(uuid = existing.uuid)
                    productDao.updateProduct(entity)
                    // Sólo cuenta si el servidor lo modificó después de lo que ya teníamos (el solape
                    // del watermark puede re-traer filas sin cambios: no deben re-notificar).
                    if (entity.lastModified > existing.lastModified) changedProducts++
                } else {
                    productDao.insertProduct(dto.toEntity())
                    changedProducts++
                }
            }
        }

        if (replace) {
            visitDao.replaceVisits(pull.visits.map { it.toEntity() })
        } else {
            pull.visits.forEach { dto ->
                val ms = parseIsoToMillis(dto.registrationDate)
                val existing = visitDao.getByRemoteId(dto.id) ?: ms?.let { visitDao.getByRegistrationDate(it) }
                if (existing != null) {
                    // Preservar el uuid local (identidad estable entre dispositivos).
                    visitDao.updateVisit(dto.toEntity(localId = existing.id).copy(uuid = existing.uuid))
                } else {
                    visitDao.insertVisit(dto.toEntity())
                    newVisits++
                }
            }
        }

        if (!replace) {
            if (newVisits > 0) syncEventBus.emit(SyncEvent.NewVisits(newVisits))
            if (changedProducts > 0) syncEventBus.emit(SyncEvent.ProductsChanged(changedProducts))
            if (merged.entrepreneurTips != settings.entrepreneurTips) syncEventBus.emit(SyncEvent.TipsChanged)
            if (merged.mapSummary != settings.mapSummary) syncEventBus.emit(SyncEvent.MapChanged)
        }
        return merged
    }

    private suspend fun finishSync(merged: AppSettings, newWatermark: Long) {
        appSettingsDao.saveSettings(
            merged.copy(lastSyncAt = newWatermark, lastModified = System.currentTimeMillis())
        )
    }

    /**
     * Watermark del próximo `?since=`: la hora del SERVIDOR (no la del dispositivo), menos un
     * margen de solape. Usar la hora del servidor evita que un reloj de dispositivo adelantado
     * deje el `since` "en el futuro" y se salte cambios para siempre (clock skew). El solape
     * reprocesa unas pocas filas, lo cual es inocuo porque [applyPull] es idempotente.
     */
    private fun watermarkFrom(pull: PullResponse, fallback: Long): Long {
        val serverMs = parseIsoToMillis(pull.serverTime)
        return (serverMs ?: fallback) - OVERLAP_MS
    }

    private companion object {
        /** Margen de solape (ms) del watermark para tolerar lag de replicación / sub-segundo. */
        const val OVERLAP_MS = 5_000L
    }

    private fun buildMigrateRequest(
        settings: AppSettings,
        products: List<Product>,
        visits: List<Visit>
    ): MigrateRequest = MigrateRequest(
        profile = settings.toProfileRequestDto(),
        products = products.map { it.toRequestDto() },
        visits = visits.map { it.toRequestDto() },
        content = settings.toContentRequestDtos()
    )

    private fun ApiResult.Fail.toOutcome(): SyncOutcome = SyncOutcome.Error(message, offline)
}
