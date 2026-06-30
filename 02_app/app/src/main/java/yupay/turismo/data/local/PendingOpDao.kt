package yupay.turismo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOpDao {
    @Query("SELECT * FROM pending_ops ORDER BY id ASC")
    suspend fun getAll(): List<PendingOp>

    @Query("SELECT COUNT(*) FROM pending_ops")
    fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_ops")
    suspend fun count(): Int

    /** ¿Hay alguna operación pendiente (no enviada) para este tipo de entidad? (p.ej. PROFILE). */
    @Query("SELECT EXISTS(SELECT 1 FROM pending_ops WHERE entityType = :entityType)")
    suspend fun hasPending(entityType: String): Boolean

    @Insert
    suspend fun insert(op: PendingOp): Long

    @Delete
    suspend fun delete(op: PendingOp)

    @Query("DELETE FROM pending_ops WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Coalescencia: descarta operaciones previas de la MISMA entidad local antes de encolar
     * la nueva, de modo que sólo se conserve el estado más reciente (CREATE→UPDATE→… colapsa
     * a una sola op; el motor decide POST/PUT según exista o no `remoteId`).
     */
    @Query("DELETE FROM pending_ops WHERE entityType = :entityType AND localId = :localId")
    suspend fun deleteForEntity(entityType: String, localId: Int)

    @Query("DELETE FROM pending_ops")
    suspend fun clear()
}
