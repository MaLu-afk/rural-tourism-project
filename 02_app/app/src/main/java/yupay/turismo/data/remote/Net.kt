package yupay.turismo.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

/**
 * Envoltura estándar de la API: `{ success, data }` en éxito o `{ success, error }` en error.
 * Como `data` es nullable, este mismo modelo deserializa tanto respuestas OK como de error.
 */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: String? = null
)

/** Resultado tipado de una llamada a la API, sin lanzar excepciones al llamador. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>
    /**
     * Fallo de la llamada.
     * @param code  código HTTP (o -1 si no hubo respuesta).
     * @param offline true si fue por falta de red (IOException), no por respuesta del servidor.
     */
    data class Fail(val code: Int, val message: String, val offline: Boolean = false) : ApiResult<Nothing>
}

inline fun <T> ApiResult<T>.onOk(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Ok) block(data)
    return this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Ok)?.data

/** Body crudo de una respuesta HTTP (o marca de offline). */
internal data class RawResult(val code: Int, val bodyText: String, val offline: Boolean = false)

/** `Json` compartido para (de)serializar DTOs camelCase de la API. */
val yupayJson: Json = Json {
    ignoreUnknownKeys = true   // el servidor puede añadir campos (content_last_*, expiresIn, …)
    coerceInputValues = true   // null → valor por defecto del campo
    explicitNulls = false      // no emitir claves null en requests (la API usa `?? null`)
    encodeDefaults = true      // sí emitir defaults no-null (isDefault=false, etc.)
    isLenient = true
}

internal val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
