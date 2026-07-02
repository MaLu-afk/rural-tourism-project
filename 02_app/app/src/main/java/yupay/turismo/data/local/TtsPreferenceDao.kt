package yupay.turismo.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TtsPreferenceDao {

    /** Todas las preferencias (una por idioma). Útil para observar el estado global. */
    @Query("SELECT * FROM tts_preferences")
    fun getAll(): Flow<List<TtsPreference>>

    /** Modelo activo de un idioma como Flow (null si no hay voz configurada). */
    @Query("SELECT * FROM tts_preferences WHERE language = :language LIMIT 1")
    fun observe(language: String): Flow<TtsPreference?>

    @Query("SELECT * FROM tts_preferences WHERE language = :language LIMIT 1")
    suspend fun getOnce(language: String): TtsPreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: TtsPreference)

    /** Desactiva la voz de un idioma (p.ej. al eliminar el modelo activo). */
    @Query("DELETE FROM tts_preferences WHERE language = :language")
    suspend fun clear(language: String)

    /** Quita la preferencia si apunta a un modelo concreto (al eliminar ese modelo). */
    @Query("DELETE FROM tts_preferences WHERE language = :language AND activeModelId = :modelId")
    suspend fun clearIfActive(language: String, modelId: String)

    @Query("DELETE FROM tts_preferences")
    suspend fun clearAll()
}
