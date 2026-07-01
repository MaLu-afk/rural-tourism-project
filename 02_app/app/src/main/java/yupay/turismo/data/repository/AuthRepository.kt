package yupay.turismo.data.repository

import android.os.Build
import yupay.turismo.data.local.AppSettings
import yupay.turismo.data.local.AppSettingsDao
import yupay.turismo.data.remote.ApiResult
import yupay.turismo.data.remote.YupayApiService
import yupay.turismo.data.remote.dto.CheckEmailRequest
import yupay.turismo.data.remote.dto.CheckEmailResponse
import yupay.turismo.data.remote.dto.EmailRequest
import yupay.turismo.data.remote.dto.GoogleIdTokenRequest
import yupay.turismo.data.remote.dto.LoginRequest
import yupay.turismo.data.remote.dto.RegisterRequest
import yupay.turismo.data.remote.dto.ResetPasswordRequest
import yupay.turismo.data.remote.dto.SessionDto
import yupay.turismo.data.remote.dto.UserBriefDto
import yupay.turismo.data.remote.dto.VerifyResetCodeRequest
import yupay.turismo.data.remote.dto.VerifySignupCodeRequest
import yupay.turismo.data.session.SessionManager

/** Resultado de una operación de autenticación, sin lanzar excepciones. */
sealed interface AuthResult<out T> {
    data class Ok<T>(val value: T) : AuthResult<T>
    data class Err(val message: String, val code: Int = 0, val offline: Boolean = false) : AuthResult<Nothing>
}

enum class RegisterStatus { LOGGED_IN, NEEDS_EMAIL_CONFIRMATION }
enum class ForgotStatus { SENT, NOT_REGISTERED, OAUTH_ONLY }

/**
 * Autenticación contra la API: registro, login (email y Google nativo), recuperación por
 * código OTP, logout y borrado de cuenta. Persiste los tokens en [SessionManager] y marca
 * `isLinked`/`accountEmail` en [AppSettings] (sin guardar la contraseña).
 *
 * NO orquesta la sincronización de datos: tras un login/registro exitoso, el llamador debe
 * disparar el primer enlace vía [yupay.turismo.data.sync.CloudSyncEngine].
 */
class AuthRepository(
    private val api: YupayApiService,
    private val session: SessionManager,
    private val appSettingsDao: AppSettingsDao
) {

    suspend fun register(
        email: String,
        password: String,
        businessName: String? = null,
        businessCategory: String? = null
    ): AuthResult<RegisterStatus> {
        val (hw, name) = deviceArgs()
        return when (val res = api.register(
            RegisterRequest(email.trim(), password, businessName, businessCategory, hw, name)
        )) {
            is ApiResult.Ok -> {
                val sess = res.data.session
                if (sess?.accessToken != null) {
                    saveSession(sess, res.data.user, email)
                    markLinked(res.data.user?.email ?: email.trim())
                    AuthResult.Ok(RegisterStatus.LOGGED_IN)
                } else {
                    AuthResult.Ok(RegisterStatus.NEEDS_EMAIL_CONFIRMATION)
                }
            }
            is ApiResult.Fail -> res.toErr()
        }
    }

    suspend fun login(email: String, password: String): AuthResult<Unit> {
        val (hw, name) = deviceArgs()
        return when (val res = api.login(LoginRequest(email.trim(), password, hw, name))) {
            is ApiResult.Ok -> {
                val sess = res.data.session
                if (sess?.accessToken == null) AuthResult.Err("Respuesta de login inválida.")
                else {
                    saveSession(sess, res.data.user, email)
                    markLinked(res.data.user?.email ?: email.trim())
                    AuthResult.Ok(Unit)
                }
            }
            is ApiResult.Fail -> res.toErr()
        }
    }

    /** Login/registro con Google nativo: el idToken se obtiene con Credential Manager. */
    suspend fun loginWithGoogleIdToken(idToken: String, nonce: String? = null): AuthResult<Boolean> {
        val (hw, name) = deviceArgs()
        return when (val res = api.googleIdToken(GoogleIdTokenRequest(idToken, nonce, hw, name))) {
            is ApiResult.Ok -> {
                val sess = res.data.session
                if (sess?.accessToken == null) AuthResult.Err("Respuesta de Google inválida.")
                else {
                    val mail = res.data.user?.email ?: ""
                    saveSession(sess, res.data.user, mail)
                    markLinked(mail)
                    AuthResult.Ok(res.data.isNewUser)
                }
            }
            is ApiResult.Fail -> res.toErr()
        }
    }

    /**
     * Confirma el registro con el código (OTP de signup) que llegó al correo. Si el código
     * es válido, deja la sesión iniciada (igual que un login) y marca la cuenta como vinculada.
     */
    suspend fun verifySignupCode(email: String, code: String): AuthResult<Unit> {
        val (hw, name) = deviceArgs()
        val settings = appSettingsDao.getSettingsOnce()
        val res = api.verifySignupCode(
            VerifySignupCodeRequest(
                email = email.trim(),
                code = code.trim(),
                hardwareDeviceId = hw,
                deviceName = name,
                businessName = settings?.businessName,
                businessCategory = settings?.businessCategory
            )
        )
        return when (res) {
            is ApiResult.Ok -> {
                val sess = res.data.session
                if (sess?.accessToken == null) AuthResult.Err("Respuesta de verificación inválida.")
                else {
                    saveSession(sess, res.data.user, email)
                    markLinked(res.data.user?.email ?: email.trim())
                    AuthResult.Ok(Unit)
                }
            }
            is ApiResult.Fail -> res.toErr()
        }
    }

    suspend fun checkEmail(email: String): AuthResult<CheckEmailResponse> =
        api.checkEmail(CheckEmailRequest(email.trim())).toAuthResult()

    suspend fun resendVerification(email: String): AuthResult<Unit> =
        api.resendVerification(EmailRequest(email.trim())).toUnit()

    suspend fun forgotPassword(email: String): AuthResult<ForgotStatus> {
        return when (val res = api.forgotPassword(EmailRequest(email.trim()))) {
            is ApiResult.Ok -> {
                val d = res.data
                val status = when {
                    d.sent -> ForgotStatus.SENT
                    d.reason == "oauth_only" -> ForgotStatus.OAUTH_ONLY
                    else -> ForgotStatus.NOT_REGISTERED
                }
                AuthResult.Ok(status)
            }
            is ApiResult.Fail -> res.toErr()
        }
    }

    /** Devuelve el accessToken de recuperación (para [resetPassword]) si el código es válido. */
    suspend fun verifyResetCode(email: String, code: String): AuthResult<String> {
        return when (val res = api.verifyResetCode(VerifyResetCodeRequest(email.trim(), code.trim()))) {
            is ApiResult.Ok -> {
                val token = res.data.session?.accessToken
                if (res.data.valid && token != null) AuthResult.Ok(token)
                else AuthResult.Err("El código es inválido o expiró.")
            }
            is ApiResult.Fail -> res.toErr()
        }
    }

    /**
     * Cambia la contraseña. Pasar [recoveryAccessToken] (de [verifyResetCode]) o bien
     * [email] + [code] como atajo de un paso.
     */
    suspend fun resetPassword(
        newPassword: String,
        recoveryAccessToken: String? = null,
        email: String? = null,
        code: String? = null
    ): AuthResult<Unit> =
        api.resetPassword(
            ResetPasswordRequest(
                accessToken = recoveryAccessToken,
                email = email?.trim(),
                code = code?.trim(),
                newPassword = newPassword
            )
        ).toUnit()

    /** Logout: revoca en servidor (best-effort) y limpia la sesión local SIEMPRE. */
    suspend fun logout(): AuthResult<Unit> {
        api.logout()
        session.clear()
        unmarkLinked()
        return AuthResult.Ok(Unit)
    }

    /** Borra la cuenta y todos sus datos en el servidor; limpia la sesión local si tiene éxito. */
    suspend fun deleteAccount(): AuthResult<Unit> {
        return when (val res = api.deleteAccount()) {
            is ApiResult.Ok -> {
                session.clear()
                unmarkLinked()
                AuthResult.Ok(Unit)
            }
            is ApiResult.Fail -> res.toErr()
        }
    }

    /**
     * Descarta SOLO la sesión local (sin revocar en el servidor). Útil cuando, tras un
     * `signInWithIdToken`, decidimos NO continuar (p.ej. registro con Google de una cuenta que
     * ya existía): no debemos hacer logout global del usuario real en sus otros dispositivos.
     */
    suspend fun discardLocalSession() {
        session.clear()
        unmarkLinked()
    }

    /**
     * Establece/cambia la contraseña del usuario AUTENTICADO usando su propio accessToken
     * (no requiere el correo ni un código OTP). Habilita el login por correo+contraseña en
     * cuentas creadas solo con Google (caso 3).
     */
    suspend fun setPasswordWithSession(newPassword: String): AuthResult<Unit> {
        val token = session.accessTokenOnce()
        if (token.isNullOrBlank()) return AuthResult.Err("No hay una sesión activa.")
        return api.resetPassword(
            ResetPasswordRequest(accessToken = token, newPassword = newPassword)
        ).toUnit()
    }

    // ───────────────────────── helpers ─────────────────────────
    private suspend fun deviceArgs(): Pair<String?, String> {
        val hw = appSettingsDao.getSettingsOnce()?.hardwareDeviceId?.ifBlank { null }
        val name = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        return hw to name
    }

    private suspend fun saveSession(sess: SessionDto, user: UserBriefDto?, fallbackEmail: String) {
        session.save(
            accessToken = sess.accessToken ?: return,
            refreshToken = sess.refreshToken ?: "",
            expiresAt = sess.expiresAt ?: 0L,
            userId = user?.id ?: "",
            email = user?.email ?: fallbackEmail.trim()
        )
    }

    private suspend fun markLinked(email: String) {
        val s = appSettingsDao.getSettingsOnce() ?: AppSettings()
        appSettingsDao.saveSettings(
            s.copy(
                isLinked = true,
                accountEmail = email,
                accountPassword = "",
                lastModified = System.currentTimeMillis()
            )
        )
    }

    private suspend fun unmarkLinked() {
        val s = appSettingsDao.getSettingsOnce() ?: return
        appSettingsDao.saveSettings(
            s.copy(
                isLinked = false,
                accountEmail = "",
                accountPassword = "",
                lastSyncAt = 0L,
                lastModified = System.currentTimeMillis()
            )
        )
    }
}

// Conversores ApiResult → AuthResult
private fun ApiResult.Fail.toErr(): AuthResult.Err = AuthResult.Err(message, code, offline)

private fun <T> ApiResult<T>.toAuthResult(): AuthResult<T> = when (this) {
    is ApiResult.Ok -> AuthResult.Ok(data)
    is ApiResult.Fail -> AuthResult.Err(message, code, offline)
}

private fun ApiResult<*>.toUnit(): AuthResult<Unit> = when (this) {
    is ApiResult.Ok -> AuthResult.Ok(Unit)
    is ApiResult.Fail -> AuthResult.Err(message, code, offline)
}
