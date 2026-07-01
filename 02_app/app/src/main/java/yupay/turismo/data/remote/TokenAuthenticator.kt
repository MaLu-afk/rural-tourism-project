package yupay.turismo.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import yupay.turismo.data.remote.dto.RefreshRequest
import yupay.turismo.data.remote.dto.RefreshResponse
import yupay.turismo.data.session.SessionManager

/**
 * Ante un 401, intenta refrescar la sesión con `POST /auth/refresh` (usando un cliente
 * SIN authenticator para evitar recursión) y reintenta la petición original con el nuevo
 * accessToken. Si el refresh falla, limpia la sesión y se rinde (la app pedirá re-login).
 */
class TokenAuthenticator(
    private val session: SessionManager,
    private val refreshClient: OkHttpClient,
    private val baseUrl: String = ApiConfig.BASE_URL
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // En endpoints públicos de auth, un 401 NO significa token caduco (p.ej. contraseña
        // incorrecta en /auth/login). No intentar refrescar ahí.
        if (response.request.url.encodedPath in PUBLIC_AUTH_PATHS) return null

        // Evita bucles: si ya se reintentó (cadena de priorResponse), rendirse.
        if (responseCount(response) >= 2) return null

        val refreshToken = runBlocking { session.refreshTokenOnce() }
        if (refreshToken.isNullOrBlank()) return null

        val newAccess = refreshSession(refreshToken)
        if (newAccess == null) {
            // refresh inválido/expirado → sesión muerta.
            runBlocking { session.clear() }
            return null
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .build()
    }

    private fun refreshSession(refreshToken: String): String? {
        return try {
            val payload = yupayJson.encodeToString(RefreshRequest(refreshToken))
            val req = Request.Builder()
                .url("$baseUrl/auth/refresh")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()
            refreshClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || text.isBlank()) return null
                val env = yupayJson.decodeFromString<ApiEnvelope<RefreshResponse>>(text)
                val sess = env.data?.session ?: return null
                val access = sess.accessToken ?: return null
                runBlocking {
                    session.updateTokens(
                        accessToken = access,
                        refreshToken = sess.refreshToken ?: refreshToken,
                        expiresAt = sess.expiresAt ?: 0L
                    )
                }
                access
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var prior = response.priorResponse
        var count = 1
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        // Endpoints públicos donde un 401 no debe disparar refresh.
        val PUBLIC_AUTH_PATHS = setOf(
            "/auth/login", "/auth/register", "/auth/refresh", "/auth/google",
            "/auth/google/idtoken", "/auth/check-email", "/auth/resend-verification",
            "/auth/forgot-password", "/auth/verify-reset-code", "/auth/reset-password",
            "/auth/config"
        )
    }
}
