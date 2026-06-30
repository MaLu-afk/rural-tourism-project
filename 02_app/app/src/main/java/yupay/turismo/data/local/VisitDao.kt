package yupay.turismo.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits ORDER BY registrationDate DESC")
    fun getAllVisits(): Flow<List<Visit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisit(visit: Visit): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisits(visits: List<Visit>)

    @Transaction
    suspend fun replaceVisits(visits: List<Visit>) {
        deleteAllVisits()
        insertVisits(visits)
    }

    @Update
    suspend fun updateVisit(visit: Visit)

    @Delete
    suspend fun deleteVisit(visit: Visit)

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun getVisitById(id: Int): Visit?

    @Query("SELECT * FROM visits WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: Long): Visit?

    @Query("SELECT * FROM visits WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): Visit?

    @Query("SELECT * FROM visits WHERE registrationDate = :registrationDate LIMIT 1")
    suspend fun getByRegistrationDate(registrationDate: Long): Visit?

    @Query("SELECT * FROM visits ORDER BY registrationDate DESC")
    suspend fun getAllOnce(): List<Visit>

    /** Visitas aún no subidas a la nube (sin id de servidor). */
    @Query("SELECT * FROM visits WHERE remoteId IS NULL ORDER BY registrationDate ASC")
    suspend fun getUnsynced(): List<Visit>

    @Query("SELECT COUNT(*) FROM visits WHERE remoteId IS NULL")
    fun countUnsyncedFlow(): Flow<Int>

    @Query("DELETE FROM visits")
    suspend fun deleteAllVisits()
}
