package yupay.turismo.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import yupay.turismo.data.session.SessionManager

/**
 * Inyecta `Authorization: Bearer <accessToken>` en cada petición cuando hay sesión.
 * En los endpoints públicos de auth el header es inocuo (no se lee). El refresh ante un 401
 * lo gestiona [TokenAuthenticator].
 */
class AuthInterceptor(private val session: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Permite a una llamada concreta evitar el header (p.ej. el refresh) marcándola.
        if (request.header(HEADER_NO_AUTH) != null) {
            return chain.proceed(request.newBuilder().removeHeader(HEADER_NO_AUTH).build())
        }
        val token = runBlocking { session.accessTokenOnce() }
        val authed = if (!token.isNullOrBlank() && request.header("Authorization") == null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        return chain.proceed(authed)
    }

    companion object {
        const val HEADER_NO_AUTH = "X-Yupay-No-Auth"
    }
}
