package yupay.turismo.data.remote.dto

import kotlinx.serialization.Serializable
import yupay.turismo.data.local.SelectedProduct

/*
 * Se usan DTOs SEPARADOS para envío (request) y recepción (response) porque las fechas
 * viajan distinto en cada sentido:
 *   - al ENVIAR: epoch en MILISEGUNDOS (Long); la API las normaliza a ISO con toIso().
 *   - al RECIBIR (/sync/pull, GET): cadenas ISO 8601 (timestamptz de Postgres).
 */

// ---------------- PRODUCTS ----------------
@Serializable
data class ProductRequestDto(
    /** Sólo se envía en /sync/push (upsert por id = remoteId). Null en migrate/POST. */
    val id: Long? = null,
    val name: String? = null,
    val basePrice: Double? = null,
    val currency: String? = null,
    val category: String? = null,
    val isDefault: Boolean = false,
    val discountValue: Double? = null,
    val discountType: String? = null,
    // ISO 8601 (no epoch ms): robusto contra la columna timestamptz aunque la API desplegada
    // no normalice estas fechas con toIso (ver gotcha de fechas en PLAN_INTEGRACION_NUBE.md).
    val discountStartDate: String? = null,
    val discountEndDate: String? = null
)

@Serializable
data class ProductResponseDto(
    val id: Long = 0,
    val name: String? = null,
    val basePrice: Double? = null,
    val currency: String? = null,
    val category: String? = null,
    val isDefault: Boolean = false,
    val discountValue: Double? = null,
    val discountType: String? = null,
    val discountStartDate: String? = null,
    val discountEndDate: String? = null,
    val createdAt: String? = null,
    val lastModified: String? = null
)

// ---------------- VISITS ----------------
@Serializable
data class VisitRequestDto(
    val id: Long? = null,
    val deviceId: String? = null,
    val nationality: String? = null,
    val nationalityFlag: String? = null,
    val selectedProducts: List<SelectedProduct> = emptyList(),
    val subtotal: Double? = null,
    val discountValue: Double? = null,
    val discountType: String? = null,
    val totalAmount: Double? = null,
    val currency: String? = null,
    /** epoch ms; clave de deduplicación del lado servidor. */
    val registrationDate: Long? = null,
    val isSent: Boolean = false,
    val sentDate: Long? = null
)

@Serializable
data class VisitResponseDto(
    val id: Long = 0,
    val deviceId: String? = null,
    val nationality: String? = null,
    val nationalityFlag: String? = null,
    val selectedProducts: List<SelectedProduct> = emptyList(),
    val subtotal: Double? = null,
    val discountValue: Double? = null,
    val discountType: String? = null,
    val totalAmount: Double? = null,
    val currency: String? = null,
    val registrationDate: String? = null,
    val isSent: Boolean = false,
    val sentDate: String? = null
)

// ---------------- CONTENT (tips/maps por idioma) ----------------
@Serializable
data class ContentRequestDto(
    val language: String,
    /** 'tip' | 'map'. */
    val type: String,
    val content: String? = null,
    val audioBase64: String? = null
)

@Serializable
data class ContentResponseDto(
    val id: Long = 0,
    val language: String = "",
    val type: String = "",
    val content: String? = null,
    val audioBase64: String? = null,
    val lastModified: String? = null
)

// ---------------- PROFILE / USER ----------------
@Serializable
data class ProfileRequestDto(
    val businessName: String? = null,
    val businessCategory: String? = null,
    /** imagen PNG en Base64 (NO_WRAP). */
    val profilePicture: String? = null
)

@Serializable
data class UserProfileResponseDto(
    val id: String? = null,
    val businessName: String? = null,
    val businessCategory: String? = null,
    val profilePicture: String? = null,
    val lastModified: String? = null
)

// ---------------- /sync/migrate ----------------
@Serializable
data class MigrateRequest(
    val profile: ProfileRequestDto? = null,
    val products: List<ProductRequestDto> = emptyList(),
    val visits: List<VisitRequestDto> = emptyList(),
    val content: List<ContentRequestDto> = emptyList()
)

@Serializable
data class MigrateInserted(
    val profile: Int = 0,
    val products: Int = 0,
    val visits: Int = 0,
    val content: Int = 0
)

@Serializable
data class MigrateResponse(val inserted: MigrateInserted = MigrateInserted())

// ---------------- /sync/push ----------------
@Serializable
data class PushRequest(
    val products: List<ProductRequestDto> = emptyList(),
    val visits: List<VisitRequestDto> = emptyList()
)

@Serializable
data class PushProducts(val upserted: Int = 0)

@Serializable
data class PushVisits(val inserted: Int = 0, val skipped: Int = 0)

@Serializable
data class PushResponse(
    val products: PushProducts = PushProducts(),
    val visits: PushVisits = PushVisits()
)

// ---------------- /sync/pull ----------------
@Serializable
data class PullResponse(
    val user: UserProfileResponseDto? = null,
    val products: List<ProductResponseDto> = emptyList(),
    val visits: List<VisitResponseDto> = emptyList(),
    val content: List<ContentResponseDto> = emptyList(),
    /** Hora del servidor (ISO 8601) al inicio del pull; el cliente la usa como watermark. */
    val serverTime: String? = null
)

// ---------------- genéricos ----------------
@Serializable
data class DeletedResponse(
    val deleted: Boolean = false,
    val id: String? = null
)
