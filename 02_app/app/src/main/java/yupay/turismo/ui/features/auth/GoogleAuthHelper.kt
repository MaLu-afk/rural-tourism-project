package yupay.turismo.ui.features.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import yupay.turismo.data.remote.getOrNull
import yupay.turismo.di.ServiceLocator

/**
 * Obtiene un `idToken` de Google con Credential Manager para mandarlo a
 * `POST /auth/google/idtoken`.
 *
 * IMPORTANTE: el `serverClientId` debe ser el **Client ID Web** (no el de Android). Se obtiene
 * de `GET /auth/config` (`googleWebClientId`), así que la API debe tener `GOOGLE_WEB_CLIENT_ID`
 * configurado. El Client ID de Android sólo autoriza la app por package + SHA‑1 y va en
 * Supabase → "Authorized Client IDs".
 *
 * ## Por qué se usa `GetSignInWithGoogleOption` (fix POCO X7 Pro / Xiaomi / HyperOS)
 * Antes se usaba el flujo "One Tap" (`GetGoogleIdOption`). En equipos casi-stock (Motorola, Pixel)
 * funciona, pero en muchos Xiaomi/POCO/HyperOS el bottom-sheet de One Tap **se cuelga**: no
 * muestra el selector de cuentas y tampoco lanza ninguna excepción → la app se queda "cargando
 * eternamente". Como el APK es el mismo (package + SHA‑1 + Client ID idénticos), el problema es
 * puramente del backend One Tap del dispositivo, no de la configuración en Google Cloud.
 *
 * `GetSignInWithGoogleOption` es el flujo **recomendado para un botón** "Iniciar sesión con
 * Google": abre la pantalla completa clásica de Google (no el bottom-sheet de One Tap) y es
 * mucho más fiable entre fabricantes. Además envolvemos la llamada en un timeout de seguridad
 * para que, pase lo que pase, NUNCA se quede colgada para siempre.
 */
object GoogleAuthHelper {

    private const val TAG = "GoogleAuth"

    /**
     * Backstop anti-cuelgue (3 min). No es el mecanismo principal —el arreglo real es usar el
     * flujo de pantalla completa— sino un seguro para que no gire eternamente si el proveedor se
     * cuelga. 3 min es de sobra incluso para añadir una cuenta nueva de Google a mano.
     */
    private const val CREDENTIAL_TIMEOUT_MS = 180_000L

    sealed interface Result {
        data class Success(val idToken: String) : Result
        data class Error(val message: String) : Result
        object Cancelled : Result
    }

    /** El Client ID Web casi nunca cambia; lo cacheamos en memoria para no repetir la llamada a la API. */
    @Volatile private var cachedWebClientId: String? = null

    suspend fun getIdToken(context: Context): Result {
        val activity = findActivity(context)
            ?: return Result.Error("No se pudo encontrar la actividad.")

        val webClientId = resolveWebClientId()
            ?: return Result.Error(
                "No se pudo conectar con el servidor para iniciar sesión con Google. " +
                    "Revisa tu conexión a internet e inténtalo de nuevo."
            )

        val credentialManager = CredentialManager.create(activity)
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        Log.d(TAG, "Lanzando 'Iniciar sesión con Google' (pantalla completa)…")
        val attempt = withTimeoutOrNull(CREDENTIAL_TIMEOUT_MS) {
            execute(credentialManager, activity, request)
        } ?: run {
            Log.e(TAG, "Timeout: el selector de Google no respondió en ${CREDENTIAL_TIMEOUT_MS / 1000}s.")
            Attempt.Failed(
                "Google tardó demasiado en responder. Revisa que Google Play Services esté " +
                    "actualizado (Play Store › buscar 'Google Play Services') e inténtalo de nuevo."
            )
        }

        return when (attempt) {
            is Attempt.Got -> Result.Success(attempt.idToken)
            is Attempt.Cancelled -> Result.Cancelled
            is Attempt.Failed -> Result.Error(attempt.message)
            is Attempt.NoCredential -> Result.Error(
                "No hay ninguna cuenta de Google disponible en este dispositivo. " +
                    "Agrega una cuenta en Ajustes › Cuentas e inténtalo de nuevo."
            )
        }
    }

    /** Resultado interno del intento contra Credential Manager. */
    private sealed interface Attempt {
        data class Got(val idToken: String) : Attempt
        object NoCredential : Attempt          // no hay cuenta de Google en el dispositivo
        object Cancelled : Attempt             // el usuario cerró el selector
        data class Failed(val message: String) : Attempt  // error real
    }

    private suspend fun execute(
        cm: CredentialManager,
        activity: Activity,
        request: GetCredentialRequest
    ): Attempt {
        return try {
            val response = cm.getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Log.d(TAG, "Credencial de Google obtenida correctamente.")
                Attempt.Got(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Log.e(TAG, "Tipo de credencial inesperado: ${credential.type}")
                Attempt.Failed("No se pudo obtener la credencial de Google.")
            }
        } catch (e: GetCredentialCancellationException) {
            // El usuario cerró el selector a propósito.
            Log.d(TAG, "El usuario canceló el selector de Google.")
            Attempt.Cancelled
        } catch (e: CancellationException) {
            // Cancelación de corrutina (timeout del backstop o salir de la pantalla): propagar.
            throw e
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No hay credenciales de Google en el dispositivo.", e)
            Attempt.NoCredential
        } catch (e: GetCredentialProviderConfigurationException) {
            // Credential Manager / Google Play Services no disponible o desactualizado.
            Log.e(TAG, "Play Services no disponible o desactualizado.", e)
            Attempt.Failed(
                "Google Play Services no está disponible o está desactualizado en este dispositivo. " +
                    "Actualízalo desde Play Store e inténtalo de nuevo."
            )
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException (${e.javaClass.simpleName}): ${e.message}", e)
            Attempt.Failed(
                e.message?.let { "No se pudo iniciar sesión con Google: $it" }
                    ?: "No se pudo iniciar sesión con Google."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado con Google.", e)
            Attempt.Failed(e.message ?: "Error inesperado con Google.")
        }
    }

    private suspend fun resolveWebClientId(): String? {
        cachedWebClientId?.let { return it }
        Log.d(TAG, "Obteniendo googleWebClientId de /auth/config…")
        val fetched = runCatching {
            ServiceLocator.apiService.getConfig().getOrNull()?.googleWebClientId
        }.onFailure {
            Log.e(TAG, "Fallo al obtener /auth/config.", it)
        }.getOrNull()?.takeIf { it.isNotBlank() }

        if (fetched != null) {
            cachedWebClientId = fetched
            Log.d(TAG, "googleWebClientId obtenido.")
        } else {
            Log.e(TAG, "googleWebClientId vacío o no disponible (¿falta GOOGLE_WEB_CLIENT_ID en la API?).")
        }
        return fetched
    }

    private fun findActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) return currentContext
            currentContext = currentContext.baseContext
        }
        return null
    }
}
