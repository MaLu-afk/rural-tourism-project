package yupay.turismo.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import yupay.turismo.data.remote.dto.*
import java.io.IOException
import java.net.URLEncoder

/**
 * Servicio HTTP tipado contra la Yupay Turismo API. Cada método devuelve [ApiResult]
 * (Ok/Fail) sin lanzar excepciones; los fallos de red se marcan como `offline`.
 *
 * Implementado sobre OkHttp + kotlinx.serialization (sin Retrofit) para minimizar
 * dependencias y mantener el control del manejo de la envoltura `{ success, data, error }`.
 */
class YupayApiService(
    private val client: OkHttpClient,
    private val baseUrl: String = ApiConfig.BASE_URL
) {

    // ───────────────────────── AUTH ─────────────────────────
    suspend fun getConfig(): ApiResult<AuthConfigResponse> =
        parse(raw("GET", "/auth/config", null))

    suspend fun register(req: RegisterRequest): ApiResult<RegisterResponse> =
        parse(raw("POST", "/auth/register", yupayJson.encodeToString(req)))

    suspend fun login(req: LoginRequest): ApiResult<AuthResponse> =
        parse(raw("POST", "/auth/login", yupayJson.encodeToString(req)))

    suspend fun googleWeb(req: GoogleWebRequest): ApiResult<GoogleResponse> =
        parse(raw("POST", "/auth/google", yupayJson.encodeToString(req)))

    suspend fun googleIdToken(req: GoogleIdTokenRequest): ApiResult<GoogleResponse> =
        parse(raw("POST", "/auth/google/idtoken", yupayJson.encodeToString(req)))

    suspend fun refresh(req: RefreshRequest): ApiResult<RefreshResponse> =
        parse(raw("POST", "/auth/refresh", yupayJson.encodeToString(req)))

    suspend fun checkEmail(req: CheckEmailRequest): ApiResult<CheckEmailResponse> =
        parse(raw("POST", "/auth/check-email", yupayJson.encodeToString(req)))

    suspend fun resendVerification(req: EmailRequest): ApiResult<SentResponse> =
        parse(raw("POST", "/auth/resend-verification", yupayJson.encodeToString(req)))

    suspend fun verifySignupCode(req: VerifySignupCodeRequest): ApiResult<GoogleResponse> =
        parse(raw("POST", "/auth/verify-signup-code", yupayJson.encodeToString(req)))

    suspend fun forgotPassword(req: EmailRequest): ApiResult<ForgotPasswordResponse> =
        parse(raw("POST", "/auth/forgot-password", yupayJson.encodeToString(req)))

    suspend fun verifyResetCode(req: VerifyResetCodeRequest): ApiResult<VerifyResetCodeResponse> =
        parse(raw("POST", "/auth/verify-reset-code", yupayJson.encodeToString(req)))

    suspend fun resetPassword(req: ResetPasswordRequest): ApiResult<UpdatedResponse> =
        parse(raw("POST", "/auth/reset-password", yupayJson.encodeToString(req)))

    suspend fun logout(): ApiResult<LoggedOutResponse> =
        parse(raw("POST", "/auth/logout", null))

    suspend fun deleteAccount(): ApiResult<DeletedAccountResponse> =
        parse(raw("DELETE", "/auth/account", null))

    // ───────────────────────── USERS ─────────────────────────
    suspend fun getMe(): ApiResult<UserProfileResponseDto> =
        parse(raw("GET", "/users/me", null))

    suspend fun updateMe(req: ProfileRequestDto): ApiResult<UserProfileResponseDto> =
        parse(raw("PATCH", "/users/me", yupayJson.encodeToString(req)))

    // ───────────────────────── PRODUCTS ─────────────────────────
    suspend fun listProducts(): ApiResult<List<ProductResponseDto>> =
        parse(raw("GET", "/products", null))

    suspend fun createProduct(req: ProductRequestDto): ApiResult<ProductResponseDto> =
        parse(raw("POST", "/products", yupayJson.encodeToString(req)))

    suspend fun updateProduct(remoteId: Long, req: ProductRequestDto): ApiResult<ProductResponseDto> =
        parse(raw("PUT", "/products/$remoteId", yupayJson.encodeToString(req)))

    suspend fun deleteProduct(remoteId: Long): ApiResult<DeletedResponse> =
        parse(raw("DELETE", "/products/$remoteId", null))

    // ───────────────────────── VISITS ─────────────────────────
    suspend fun createVisit(req: VisitRequestDto): ApiResult<VisitResponseDto> =
        parse(raw("POST", "/visits", yupayJson.encodeToString(req)))

    suspend fun updateVisit(remoteId: Long, req: VisitRequestDto): ApiResult<VisitResponseDto> =
        parse(raw("PUT", "/visits/$remoteId", yupayJson.encodeToString(req)))

    suspend fun deleteVisit(remoteId: Long): ApiResult<DeletedResponse> =
        parse(raw("DELETE", "/visits/$remoteId", null))

    // ───────────────────────── CONTENT ─────────────────────────
    suspend fun listContent(): ApiResult<List<ContentResponseDto>> =
        parse(raw("GET", "/content", null))

    // ───────────────────────── SYNC ─────────────────────────
    suspend fun migrate(req: MigrateRequest): ApiResult<MigrateResponse> =
        parse(raw("POST", "/sync/migrate", yupayJson.encodeToString(req)))

    suspend fun push(req: PushRequest): ApiResult<PushResponse> =
        parse(raw("POST", "/sync/push", yupayJson.encodeToString(req)))

    /** @param sinceIso marca ISO 8601 para sync incremental; null = traer todo. */
    suspend fun pull(sinceIso: String? = null): ApiResult<PullResponse> {
        val path = if (sinceIso.isNullOrBlank()) "/sync/pull"
        else "/sync/pull?since=" + URLEncoder.encode(sinceIso, "UTF-8")
        return parse(raw("GET", path, null))
    }

    // ───────────────────────── plomería ─────────────────────────
    private suspend fun raw(method: String, path: String, jsonBody: String?): RawResult =
        withContext(Dispatchers.IO) {
            val body = jsonBody?.toRequestBody(JSON_MEDIA)
            val builder = Request.Builder().url(baseUrl + path)
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> if (body != null) builder.delete(body) else builder.delete()
                "POST" -> builder.post(body ?: EMPTY_BODY)
                "PUT" -> builder.put(body ?: EMPTY_BODY)
                "PATCH" -> builder.patch(body ?: EMPTY_BODY)
                else -> builder.method(method, body)
            }
            try {
                client.newCall(builder.build()).execute().use { resp ->
                    RawResult(resp.code, resp.body?.string().orEmpty())
                }
            } catch (e: IOException) {
                RawResult(-1, "", offline = true)
            }
        }

    private inline fun <reified T> parse(raw: RawResult): ApiResult<T> {
        if (raw.offline) return ApiResult.Fail(-1, "Sin conexión a internet.", offline = true)
        val env = try {
            yupayJson.decodeFromString<ApiEnvelope<T>>(raw.bodyText)
        } catch (e: Exception) {
            return ApiResult.Fail(raw.code, fallbackMessage(raw))
        }
        return if (env.success && env.data != null) {
            ApiResult.Ok(env.data)
        } else {
            ApiResult.Fail(raw.code, env.error ?: fallbackMessage(raw))
        }
    }

    private fun fallbackMessage(raw: RawResult): String = when {
        raw.code == -1 -> "Sin conexión a internet."
        raw.code in 500..599 -> "El servidor no está disponible (HTTP ${raw.code}). Intenta de nuevo en unos segundos."
        raw.code == 401 -> "Sesión no válida o expirada."
        raw.bodyText.isNotBlank() && raw.bodyText.length < 300 -> raw.bodyText
        else -> "Error de red (HTTP ${raw.code})."
    }

    private companion object {
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}
