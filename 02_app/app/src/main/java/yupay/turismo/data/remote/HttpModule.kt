package yupay.turismo.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import yupay.turismo.data.session.SessionManager
import java.util.concurrent.TimeUnit

/** Construye el cliente OkHttp (con auth + refresh) y el [YupayApiService]. */
object HttpModule {

    fun create(session: SessionManager): YupayApiService {
        // BASIC: registra sólo líneas de petición/respuesta (no headers ni body → no expone tokens).
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        // Cliente desnudo para el refresh: sin AuthInterceptor ni Authenticator (evita recursión).
        val refreshClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .addInterceptor(logging)
            .authenticator(TokenAuthenticator(session, refreshClient))
            // Render free tiene cold start; toleramos esperas largas en la primera petición.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return YupayApiService(client)
    }
}
