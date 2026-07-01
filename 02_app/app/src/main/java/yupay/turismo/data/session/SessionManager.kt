package yupay.turismo.data.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Sesión autenticada del usuario (tokens de Supabase emitidos por la API). */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** epoch en SEGUNDOS (formato de Supabase `expires_at`). 0 si desconocido. */
    val expiresAt: Long,
    val userId: String,
    val email: String
)

private val Context.authDataStore by preferencesDataStore(name = "yupay_session")

/**
 * Almacén seguro de la sesión en DataStore (fuera de Room). Guarda únicamente los tokens
 * y metadatos del usuario; **nunca** la contraseña. Sustituye al `accountPassword` en claro.
 *
 * Nota: DataStore Preferences no cifra en disco. Para endurecer, se puede envolver con
 * EncryptedSharedPreferences/Tink (ver PLAN_INTEGRACION_NUBE.md → "Endurecimiento").
 */
class SessionManager(private val appContext: Context) {

    private val ds = appContext.applicationContext.authDataStore

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES = longPreferencesKey("expires_at")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
    }

    val sessionFlow: Flow<Session?> = ds.data.map(::toSession)

    val isLoggedInFlow: Flow<Boolean> = ds.data.map { it[Keys.ACCESS]?.isNotBlank() == true }

    suspend fun current(): Session? = toSession(ds.data.first())

    suspend fun accessTokenOnce(): String? = ds.data.first()[Keys.ACCESS]

    suspend fun refreshTokenOnce(): String? = ds.data.first()[Keys.REFRESH]

    suspend fun isLoggedIn(): Boolean = !accessTokenOnce().isNullOrBlank()

    /** Guarda una sesión completa (login/registro/google/verify-reset). */
    suspend fun save(
        accessToken: String,
        refreshToken: String,
        expiresAt: Long,
        userId: String,
        email: String
    ) {
        ds.edit { p ->
            p[Keys.ACCESS] = accessToken
            p[Keys.REFRESH] = refreshToken
            p[Keys.EXPIRES] = expiresAt
            p[Keys.USER_ID] = userId
            p[Keys.EMAIL] = email
        }
    }

    /** Actualiza sólo los tokens tras un refresh, preservando userId/email. */
    suspend fun updateTokens(accessToken: String, refreshToken: String, expiresAt: Long) {
        ds.edit { p ->
            p[Keys.ACCESS] = accessToken
            if (refreshToken.isNotBlank()) p[Keys.REFRESH] = refreshToken
            p[Keys.EXPIRES] = expiresAt
        }
    }

    suspend fun clear() {
        ds.edit { it.clear() }
    }

    private fun toSession(p: Preferences): Session? {
        val access = p[Keys.ACCESS]
        if (access.isNullOrBlank()) return null
        return Session(
            accessToken = access,
            refreshToken = p[Keys.REFRESH] ?: "",
            expiresAt = p[Keys.EXPIRES] ?: 0L,
            userId = p[Keys.USER_ID] ?: "",
            email = p[Keys.EMAIL] ?: ""
        )
    }
}
