package yupay.turismo.data.remote

import android.util.Base64
import kotlinx.serialization.Serializable
import yupay.turismo.data.local.AppSettings
import yupay.turismo.data.local.DiscountType
import yupay.turismo.data.local.Product
import yupay.turismo.data.local.Visit
import yupay.turismo.data.remote.dto.ContentRequestDto
import yupay.turismo.data.remote.dto.ContentResponseDto
import yupay.turismo.data.remote.dto.ProductRequestDto
import yupay.turismo.data.remote.dto.ProductResponseDto
import yupay.turismo.data.remote.dto.ProfileRequestDto
import yupay.turismo.data.remote.dto.VisitRequestDto
import yupay.turismo.data.remote.dto.VisitResponseDto
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/* ════════════ Fechas: ISO 8601 (servidor) ↔ epoch ms (Room/Android) ════════════ */

/**
 * Convierte una fecha ISO 8601 (timestamptz de Postgres, p.ej. `2026-06-21T15:56:00.123+00:00`
 * o `...Z`) a epoch en milisegundos. Devuelve null si es nula/no parseable.
 * Requiere desugaring de java.time (habilitado en build.gradle.kts).
 */
fun parseIsoToMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** epoch ms → ISO 8601 UTC (para el parámetro `?since=` de /sync/pull). */
fun millisToIso(millis: Long): String = Instant.ofEpochMilli(millis).toString()

/* ════════════ Base64 (foto de perfil) ════════════ */

fun ByteArray?.toBase64(): String? =
    this?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

fun String?.fromBase64(): ByteArray? =
    if (this.isNullOrBlank()) null else try {
        Base64.decode(this, Base64.DEFAULT)
    } catch (_: Exception) {
        null
    }

private fun parseDiscountType(raw: String?, default: DiscountType): DiscountType =
    runCatching { DiscountType.valueOf((raw ?: "").trim().uppercase()) }.getOrDefault(default)

/* ════════════ PRODUCTS ════════════ */

/** @param includeRemoteId true sólo en /sync/push (upsert por id = remoteId). */
fun Product.toRequestDto(includeRemoteId: Boolean = false): ProductRequestDto = ProductRequestDto(
    id = if (includeRemoteId) remoteId else null,
    name = name,
    basePrice = basePrice,
    currency = currency,
    category = category,
    isDefault = isDefault,
    discountValue = discountValue,
    discountType = discountType.name,
    // Enviar como ISO 8601 (no epoch ms) para evitar el gotcha de timestamptz.
    discountStartDate = discountStartDate?.let { millisToIso(it) },
    discountEndDate = discountEndDate?.let { millisToIso(it) }
)

fun ProductResponseDto.toEntity(localId: Int = 0): Product = Product(
    id = localId,
    remoteId = id,
    name = name ?: "",
    basePrice = basePrice ?: 0.0,
    currency = currency ?: "S/",
    category = category ?: "Varios",
    isDefault = isDefault,
    discountValue = discountValue ?: 0.0,
    discountType = parseDiscountType(discountType, DiscountType.PERCENTAGE),
    discountStartDate = parseIsoToMillis(discountStartDate),
    discountEndDate = parseIsoToMillis(discountEndDate),
    createdAt = parseIsoToMillis(createdAt) ?: System.currentTimeMillis(),
    lastModified = parseIsoToMillis(lastModified) ?: System.currentTimeMillis()
)

/* ════════════ VISITS ════════════ */

fun Visit.toRequestDto(includeRemoteId: Boolean = false): VisitRequestDto = VisitRequestDto(
    id = if (includeRemoteId) remoteId else null,
    deviceId = deviceId,
    nationality = nationality,
    nationalityFlag = nationalityFlag,
    selectedProducts = selectedProducts,
    subtotal = subtotal,
    discountValue = discountValue,
    discountType = discountType.name,
    totalAmount = totalAmount,
    currency = currency,
    registrationDate = registrationDate,
    isSent = isSent,
    sentDate = sentDate
)

fun VisitResponseDto.toEntity(localId: Int = 0): Visit = Visit(
    id = localId,
    remoteId = id,
    deviceId = deviceId ?: "",
    nationality = nationality ?: "",
    nationalityFlag = nationalityFlag ?: "",
    selectedProducts = selectedProducts,
    subtotal = subtotal ?: 0.0,
    discountValue = discountValue ?: 0.0,
    discountType = parseDiscountType(discountType, DiscountType.FIXED),
    totalAmount = totalAmount ?: 0.0,
    currency = currency ?: "S/",
    registrationDate = parseIsoToMillis(registrationDate) ?: System.currentTimeMillis(),
    isSent = isSent,
    sentDate = parseIsoToMillis(sentDate)
)

/* ════════════ PROFILE ════════════ */

fun AppSettings.toProfileRequestDto(): ProfileRequestDto = ProfileRequestDto(
    businessName = businessName,
    businessCategory = businessCategory,
    profilePicture = profilePicture.toBase64()
)

/* ════════════ CONTENT (tips/maps por idioma) ════════════ */

/**
 * Construye las filas de content para subir (migrate). NO sube audio: el campo de audio de
 * la app es único (no por idioma) y el servidor genera el audio con IA. Ver §5 del plan.
 */
fun AppSettings.toContentRequestDtos(): List<ContentRequestDto> {
    val out = ArrayList<ContentRequestDto>(entrepreneurTips.size + mapSummary.size)
    entrepreneurTips.forEach { (lang, text) ->
        out += ContentRequestDto(language = lang, type = "tip", content = text, audioBase64 = null)
    }
    mapSummary.forEach { (lang, text) ->
        out += ContentRequestDto(language = lang, type = "map", content = text, audioBase64 = null)
    }
    return out
}

/**
 * Vuelca el content del servidor en los mapas de [AppSettings]. El audio (único en la app)
 * se rellena, en best-effort, con el del idioma actual.
 */
fun applyContentToSettings(
    settings: AppSettings,
    content: List<ContentResponseDto>,
    currentLanguage: String
): AppSettings {
    if (content.isEmpty()) return settings
    val tips = settings.entrepreneurTips.toMutableMap()
    val maps = settings.mapSummary.toMutableMap()
    var tipAudio = settings.entrepreneurTipsAudio
    var mapAudio = settings.mapSummaryAudio
    content.forEach { c ->
        when (c.type) {
            "tip" -> {
                if (!c.content.isNullOrBlank()) tips[c.language] = c.content
                if (c.language == currentLanguage && !c.audioBase64.isNullOrBlank()) tipAudio = c.audioBase64
            }
            "map" -> {
                if (!c.content.isNullOrBlank()) maps[c.language] = c.content
                if (c.language == currentLanguage && !c.audioBase64.isNullOrBlank()) mapAudio = c.audioBase64
            }
        }
    }
    return settings.copy(
        entrepreneurTips = tips,
        mapSummary = maps,
        entrepreneurTipsAudio = tipAudio,
        mapSummaryAudio = mapAudio
    )
}

/** Payload serializado de una op DELETE en el outbox (guarda el remoteId a borrar). */
@Serializable
data class DeletePayload(val remoteId: Long? = null)
