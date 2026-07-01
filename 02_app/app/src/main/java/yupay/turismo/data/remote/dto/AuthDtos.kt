package yupay.turismo.data.remote.dto

import kotlinx.serialization.Serializable

/** Sesión devuelta por la API (`shapeSession`). */
@Serializable
data class SessionDto(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    /** epoch en SEGUNDOS. */
    val expiresAt: Long? = null,
    val expiresIn: Long? = null,
    val tokenType: String? = null
)

@Serializable
data class UserBriefDto(
    val id: String? = null,
    val email: String? = null
)

// ---------- /auth/config ----------
@Serializable
data class AuthConfigResponse(
    val supabaseUrl: String? = null,
    val supabaseAnonKey: String? = null,
    val googleEnabled: Boolean = false,
    val emailPasswordEnabled: Boolean = false,
    /** Client ID Web de Google: úsalo como serverClientId en Credential Manager. */
    val googleWebClientId: String? = null,
    val googleAndroidClientId: String? = null
)

// ---------- /auth/register ----------
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val businessName: String? = null,
    val businessCategory: String? = null,
    val hardwareDeviceId: String? = null,
    val deviceName: String? = null
)

@Serializable
data class RegisterResponse(
    val user: UserBriefDto? = null,
    val session: SessionDto? = null,
    /** true si Supabase exige confirmar el correo antes de poder iniciar sesión. */
    val emailConfirmationRequired: Boolean = false
)

// ---------- /auth/login ----------
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val hardwareDeviceId: String? = null,
    val deviceName: String? = null
)

@Serializable
data class AuthResponse(
    val user: UserBriefDto? = null,
    val session: SessionDto? = null
)

// ---------- /auth/google (web) ----------
@Serializable
data class GoogleWebRequest(
    val accessToken: String,
    val refreshToken: String? = null,
    val hardwareDeviceId: String? = null,
    val deviceName: String? = null,
    val businessName: String? = null,
    val businessCategory: String? = null
)

// ---------- /auth/google/idtoken (Android nativo) ----------
@Serializable
data class GoogleIdTokenRequest(
    val idToken: String,
    val nonce: String? = null,
    val hardwareDeviceId: String? = null,
    val deviceName: String? = null,
    val businessName: String? = null,
    val businessCategory: String? = null
)

@Serializable
data class GoogleResponse(
    val user: UserBriefDto? = null,
    val session: SessionDto? = null,
    val isNewUser: Boolean = false
)

// ---------- /auth/refresh ----------
@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RefreshResponse(val session: SessionDto? = null)

// ---------- /auth/check-email ----------
@Serializable
data class CheckEmailRequest(val email: String)

@Serializable
data class CheckEmailResponse(
    val exists: Boolean = false,
    val confirmed: Boolean = false
)

// ---------- /auth/verify-signup-code ----------
@Serializable
data class VerifySignupCodeRequest(
    val email: String,
    val code: String,
    val hardwareDeviceId: String? = null,
    val deviceName: String? = null,
    val businessName: String? = null,
    val businessCategory: String? = null
)

// ---------- /auth/resend-verification + /auth/forgot-password ----------
@Serializable
data class EmailRequest(val email: String)

@Serializable
data class SentResponse(val sent: Boolean = false)

@Serializable
data class ForgotPasswordResponse(
    val sent: Boolean = false,
    /** 'sent' | 'not_registered' | 'oauth_only'. */
    val reason: String? = null,
    val message: String? = null
)

// ---------- /auth/verify-reset-code ----------
@Serializable
data class VerifyResetCodeRequest(val email: String, val code: String)

@Serializable
data class VerifyResetCodeResponse(
    val valid: Boolean = false,
    val session: SessionDto? = null,
    val user: UserBriefDto? = null
)

// ---------- /auth/reset-password ----------
@Serializable
data class ResetPasswordRequest(
    val accessToken: String? = null,
    val email: String? = null,
    val code: String? = null,
    val newPassword: String
)

@Serializable
data class UpdatedResponse(val updated: Boolean = false)

// ---------- /auth/logout + DELETE /auth/account ----------
@Serializable
data class LoggedOutResponse(val loggedOut: Boolean = false)

@Serializable
data class DeletedAccountResponse(
    val deleted: Boolean = false,
    val userId: String? = null
)
