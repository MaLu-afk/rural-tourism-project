package yupay.turismo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cola de operaciones pendientes de subir a la nube (patrón "outbox").
 *
 * La app es offline-first: cada cambio local de productos/visitas/perfil se escribe
 * primero en Room y, además, se encola aquí. Cuando vuelve internet, [yupay.turismo.data.sync.CloudSyncEngine]
 * drena esta cola en orden (FIFO) llamando a los endpoints REST correspondientes.
 *
 * - CREATE/UPDATE: el motor relee la fila local actual por [localId] y decide POST (si la
 *   entidad aún no tiene `remoteId`) o PUT (si ya existe en el servidor).
 * - DELETE: la fila local ya no existe, así que el `remoteId` necesario para DELETE va
 *   serializado en [payloadJson] al momento de encolar.
 */
@Entity(tableName = "pending_ops")
data class PendingOp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val opType: String,
    /** id local (Room) de la entidad afectada. Para PROFILE se usa [PROFILE_LOCAL_ID]. */
    val localId: Int,
    /** Snapshot serializado: para DELETE contiene `{ "remoteId": <Long?> }`. */
    val payloadJson: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ENTITY_PRODUCT = "PRODUCT"
        const val ENTITY_VISIT = "VISIT"
        const val ENTITY_PROFILE = "PROFILE"

        const val OP_CREATE = "CREATE"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"

        /** Sólo hay un perfil por dispositivo; su localId es constante. */
        const val PROFILE_LOCAL_ID = 1
    }
}
